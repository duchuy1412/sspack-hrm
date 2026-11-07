import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  EMPTY,
  catchError,
  debounceTime,
  distinctUntilChanged,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { RbacApi, RbacErrorMessages } from '../rbac.api';
import { Page, RoleSummary, UserWithRoles } from '../rbac.models';

/**
 * PLT-03: gán vai trò cho tài khoản. Tìm theo email, mở hộp thoại để chọn vai trò. Vai trò mới có hiệu lực
 * khi người dùng đó làm mới phiên (tối đa 15 phút).
 */
@Component({
  selector: 'hrm-users-page',
  imports: [ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../../shared/ui/page-shared.scss', './users-page.scss'],
  templateUrl: './users-page.html',
})
export class UsersPage {
  private readonly api = inject(RbacApi);
  private readonly errors = inject(RbacErrorMessages);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly page = signal<Page<UserWithRoles> | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly allRoles = signal<RoleSummary[]>([]);
  protected readonly editing = signal<UserWithRoles | null>(null);
  protected readonly chosen = signal<ReadonlySet<number>>(new Set());
  protected readonly saving = signal(false);
  protected readonly dialogError = signal<string | null>(null);
  protected readonly savedMessage = signal<string | null>(null);

  constructor() {
    this.search.valueChanges
      .pipe(
        debounceTime(300),
        startWith(this.search.value),
        distinctUntilChanged(),
        tap(() => this.pageIndex.set(0)),
        switchMap(() => this.fetch()),
        takeUntilDestroyed(),
      )
      .subscribe();

    // Vai trò cho hộp thoại: chỉ đọc được khi có role:manage; không có thì dùng vai trò đang gán của từng người
    this.api.roles(0, 100).subscribe({
      next: (page) => this.allRoles.set(page.content),
      error: () => this.allRoles.set([]),
    });
  }

  protected goTo(index: number): void {
    this.pageIndex.set(index);
    this.fetch().subscribe();
  }

  protected open(user: UserWithRoles): void {
    this.editing.set(user);
    this.chosen.set(new Set(user.roles.map((r) => r.id)));
    this.dialogError.set(null);
    this.savedMessage.set(null);
    this.dialog().nativeElement.showModal();
  }

  protected close(): void {
    this.dialog().nativeElement.close();
    this.editing.set(null);
  }

  protected dialogRoles(): { id: number; code: string; name: string }[] {
    const roles = this.allRoles();
    return roles.length > 0 ? roles : (this.editing()?.roles ?? []);
  }

  protected toggle(roleId: number, checked: boolean): void {
    this.chosen.update((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(roleId);
      } else {
        next.delete(roleId);
      }
      return next;
    });
  }

  protected save(): void {
    const user = this.editing();
    if (!user || this.chosen().size === 0) {
      return;
    }
    this.saving.set(true);
    this.dialogError.set(null);
    this.api.assignRoles(user.id, Array.from(this.chosen()), user.version).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.page.update((p) =>
          p ? { ...p, content: p.content.map((u) => (u.id === updated.id ? updated : u)) } : p,
        );
        this.savedMessage.set(updated.email);
        this.close();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.dialogError.set(this.errors.describe(err));
      },
    });
  }

  private fetch() {
    this.loading.set(true);
    this.error.set(null);
    return this.api.users(this.search.value, this.pageIndex()).pipe(
      tap({
        next: (page) => {
          this.page.set(page);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.error.set(this.errors.describe(err));
          this.loading.set(false);
        },
      }),
      // Giữ luồng tìm kiếm sống sau lỗi để lần gõ tiếp theo vẫn gọi API
      catchError(() => EMPTY),
    );
  }
}
