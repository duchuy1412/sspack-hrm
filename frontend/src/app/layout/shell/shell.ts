import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { AVAILABLE_LANGS, AppLang } from '../../core/i18n/i18n.config';
import { NAV_ITEMS } from '../nav-items';

/**
 * Khung trang chính: thanh điều hướng bên trái theo mockup (logo, 9 phân hệ, khối người dùng ở đáy)
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

  protected readonly navItems = NAV_ITEMS;
  protected readonly langs = AVAILABLE_LANGS;
  protected readonly activeLang = signal<AppLang>(this.transloco.getActiveLang() as AppLang);
  protected readonly menuOpen = signal(false);

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
