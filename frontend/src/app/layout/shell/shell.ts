import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { AuthService } from '../../core/auth/auth.service';
import { AVAILABLE_LANGS, AppLang } from '../../core/i18n/i18n.config';
import { NAV_ITEMS } from '../nav-items';

/** Vai trò hiển thị dưới tên người dùng: lấy vai trò cao nhất khi có nhiều vai trò. */
const ROLE_PRIORITY = ['ADMIN', 'HR', 'MANAGER', 'INTERVIEWER', 'EMPLOYEE'];

/**
 * Khung trang chính: thanh điều hướng bên trái theo mockup (logo, các phân hệ được phép, khối người dùng ở đáy)
 * và vùng nội dung. Trên màn hình hẹp, thanh bên trượt ra từ nút menu.
 */
@Component({
  selector: 'hrm-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslocoPipe],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  private readonly transloco = inject(TranslocoService);
  protected readonly auth = inject(AuthService);

  /** PLT-03: chỉ hiện phân hệ người dùng có quyền; tự cập nhật khi quyền đổi sau lần làm mới token. */
  protected readonly navItems = computed(() =>
    NAV_ITEMS.filter((item) => this.auth.hasAnyPermission(item.permissions)),
  );
  protected readonly langs = AVAILABLE_LANGS;
  protected readonly activeLang = signal<AppLang>(this.transloco.getActiveLang() as AppLang);
  protected readonly menuOpen = signal(false);

  protected readonly initials = computed(() => {
    const local = this.auth.user()?.email.split('@')[0] ?? '';
    const parts = local.split(/[._-]+/).filter(Boolean);
    return (
      parts
        .map((p) => p[0])
        .join('')
        .slice(0, 2)
        .toUpperCase() || '?'
    );
  });

  protected readonly roleLabelKey = computed(() => {
    const codes = this.auth.user()?.roles.map((r) => r.code) ?? [];
    const primary = ROLE_PRIORITY.find((code) => codes.includes(code)) ?? codes[0];
    return primary ? `roles.${primary}` : '';
  });

  protected logout(): void {
    this.closeMenu();
    this.auth.logout().subscribe();
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected setLang(lang: AppLang): void {
    this.transloco.setActiveLang(lang);
    this.activeLang.set(lang);
    document.documentElement.lang = lang;
  }
}
