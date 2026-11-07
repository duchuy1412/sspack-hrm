import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { EMPTY, Subject, catchError, startWith, switchMap, tap } from 'rxjs';

import { Page } from '../rbac.models';
import { RbacErrorMessages } from '../rbac.api';
import { AuditApi } from './audit.api';
import {
  AUDIT_ACTIONS,
  AUDIT_ENTITIES,
  AuditActor,
  AuditFilter,
  AuditLogEntry,
  diffValues,
  formatValue,
} from './audit.models';

/**
 * PLT-04: Admin xem nhật ký thao tác: ai, lúc nào, bảng nào, giá trị trước/sau. Lọc theo khoảng ngày,
 * người thực hiện, bảng, hành động; bấm một dòng để xem so sánh trước/sau.
 */
@Component({
  selector: 'hrm-audit-page',
  imports: [ReactiveFormsModule, TranslocoPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../admin-shared.scss', './audit-page.scss'],
  templateUrl: './audit-page.html',
})
export class AuditPage {
  private readonly api = inject(AuditApi);
  private readonly errors = inject(RbacErrorMessages);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly reload = new Subject<number>();

  protected readonly actions = AUDIT_ACTIONS;
  protected readonly entities = AUDIT_ENTITIES;
  protected readonly format = formatValue;

  protected readonly filters = inject(FormBuilder).nonNullable.group({
    from: [''],
    to: [''],
    userId: [''],
    entityType: [''],
    action: [''],
  });

  protected readonly actors = signal<AuditActor[]>([]);
  protected readonly page = signal<Page<AuditLogEntry> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly selected = signal<AuditLogEntry | null>(null);
  protected readonly diff = computed(() => {
    const entry = this.selected();
    return entry ? diffValues(entry.oldValue, entry.newValue) : [];
  });

  constructor() {
    this.reload
      .pipe(
        startWith(0),
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((index) =>
          this.api.search(this.filters.getRawValue() as AuditFilter, index).pipe(
            catchError((err: unknown) => {
              this.error.set(this.errors.describe(err));
              this.loading.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.page.set(page);
        this.loading.set(false);
      });

    this.api.actors().subscribe({
      next: (actors) => this.actors.set(actors),
      error: () => this.actors.set([]),
    });
  }

  protected apply(): void {
    this.reload.next(0);
  }

  protected reset(): void {
    this.filters.reset();
    this.reload.next(0);
  }

  protected goTo(index: number): void {
    this.reload.next(index);
  }

  protected open(entry: AuditLogEntry): void {
    this.selected.set(entry);
    this.dialog().nativeElement.showModal();
  }

  protected close(): void {
    this.dialog().nativeElement.close();
    this.selected.set(null);
  }
}
