import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { RbacErrorMessages } from '../../admin/rbac.api';
import { initials } from '../../employees/employees.api';
import { ChartNode, OrgChartApi, buildChart, searchChart } from './org-chart.models';

/** Số người hiện sẵn trong một phòng ban; còn lại mở bằng "Xem thêm". */
export const MEMBERS_PREVIEW = 6;

/**
 * ORG-03: sơ đồ tổ chức. Cây phòng ban kèm nhân viên (ảnh hoặc chữ cái đầu, tên, chức danh), mở/thu gọn từng
 * nhánh. Tìm theo tên không dấu hoặc mã: mở các nhánh có người khớp, tô sáng và cuộn tới từng người. Mọi người
 * dùng xem được cả công ty (chỉ thông tin công việc); thẻ chỉ là liên kết khi được xem hồ sơ đầy đủ.
 */
@Component({
  selector: 'hrm-org-chart-page',
  imports: [NgTemplateOutlet, FormsModule, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../../shared/ui/page-shared.scss', './org-chart-page.scss'],
  templateUrl: './org-chart-page.html',
})
export class OrgChartPage {
  private readonly api = inject(OrgChartApi);
  private readonly errors = inject(RbacErrorMessages);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly roots = signal<ChartNode[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  /** Phòng ban người dùng đã thu gọn (mặc định mọi nhánh mở). */
  protected readonly collapsed = signal<ReadonlySet<number>>(new Set());
  /** Phòng ban đã bấm "Xem thêm". */
  protected readonly expandedMembers = signal<ReadonlySet<number>>(new Set());
  protected readonly current = signal(0);

  protected readonly search = computed(() => searchChart(this.roots(), this.query()));
  protected readonly total = computed(() => this.roots().reduce((sum, r) => sum + r.total, 0));
  protected readonly currentMemberId = computed(() => {
    const s = this.search();
    return s && s.matches.length ? s.matches[this.current()].member.id : null;
  });

  protected readonly initials = initials;
  protected readonly preview = MEMBERS_PREVIEW;

  constructor() {
    this.api.chart().subscribe({
      next: (list) => {
        this.roots.set(buildChart(list));
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(this.errors.describe(err, ['org.errors', 'admin.errors']));
        this.loading.set(false);
      },
    });
  }

  /** Nhánh đang mở: khi tìm, nhánh có người khớp luôn mở; còn lại theo lựa chọn của người dùng. */
  protected isOpen(node: ChartNode): boolean {
    const s = this.search();
    if (s && s.openIds.has(node.department.id)) {
      return true;
    }
    return !this.collapsed().has(node.department.id);
  }

  protected toggle(node: ChartNode): void {
    const id = node.department.id;
    const next = new Set(this.collapsed());
    if (this.isOpen(node)) {
      next.add(id);
    } else {
      next.delete(id);
    }
    this.collapsed.set(next);
  }

  /** Hiện đủ người khi đã bấm "Xem thêm" hoặc phòng ban có người khớp tìm kiếm. */
  protected showAllMembers(node: ChartNode): boolean {
    const id = node.department.id;
    return (
      node.department.members.length <= MEMBERS_PREVIEW ||
      this.expandedMembers().has(id) ||
      (this.search()?.openIds.has(id) ?? false)
    );
  }

  protected showMore(node: ChartNode): void {
    this.expandedMembers.set(new Set([...this.expandedMembers(), node.department.id]));
  }

  protected setAll(open: boolean): void {
    const ids = new Set<number>();
    const walk = (nodes: ChartNode[]) =>
      nodes.forEach((n) => {
        if (n.children.length) {
          ids.add(n.department.id);
        }
        walk(n.children);
      });
    if (!open) {
      walk(this.roots());
    }
    this.collapsed.set(ids);
  }

  protected onQuery(value: string): void {
    this.query.set(value);
    this.current.set(0);
    this.reveal();
  }

  /** Đi tới kết quả trước (-1) hoặc sau (+1), vòng lại khi tới đầu/cuối. */
  protected step(delta: number): void {
    const count = this.search()?.matches.length ?? 0;
    if (count === 0) {
      return;
    }
    this.current.set((this.current() + delta + count) % count);
    this.reveal();
  }

  /** Cuộn tới người đang chọn sau khi sơ đồ vẽ lại. */
  private reveal(): void {
    afterNextRender(
      () => {
        const id = this.currentMemberId();
        if (id === null) {
          return;
        }
        const card = this.host.nativeElement.querySelector<HTMLElement>(`[data-member="${id}"]`);
        const reduced =
          globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
        card?.scrollIntoView?.({ block: 'center', behavior: reduced ? 'auto' : 'smooth' });
      },
      { injector: this.injector },
    );
  }
}
