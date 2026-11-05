import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';

import vi from '../../../../public/i18n/vi.json';
import { routes } from '../../app.routes';
import { NAV_ITEMS } from '../nav-items';
import { Shell } from './shell';

describe('Shell', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        Shell,
        TranslocoTestingModule.forRoot({
          langs: { vi },
          translocoConfig: { availableLangs: ['vi', 'en'], defaultLang: 'vi' },
          preloadLangs: true,
        }),
      ],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  function render(): HTMLElement {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('hiển thị đủ 9 phân hệ theo đúng thứ tự mockup, nhãn tiếng Việt', () => {
    const labels = Array.from(render().querySelectorAll('.nav-item .nav-label')).map((el) =>
      el.textContent?.trim(),
    );

    expect(labels).toEqual([
      'Tổng quan',
      'Nhân viên',
      'Cơ cấu tổ chức',
      'Nghỉ phép',
      'Duyệt đơn',
      'Chấm công',
      'Tính lương',
      'Tuyển dụng',
      'Báo cáo',
    ]);
  });

  it('mỗi mục trỏ tới route lazy-load của phân hệ', () => {
    const hrefs = Array.from(render().querySelectorAll<HTMLAnchorElement>('.nav-item')).map((a) =>
      a.getAttribute('href'),
    );

    expect(hrefs).toEqual(NAV_ITEMS.map((item) => `/${item.path}`));
  });

  it('mọi trang trong shell yêu cầu đăng nhập', () => {
    expect(routes.find((r) => r.path === '')?.canActivate?.length).toBe(1);
  });

  it('mỗi phân hệ có một route lazy-load riêng', () => {
    const children = routes.find((r) => r.path === '')?.children ?? [];
    for (const item of NAV_ITEMS) {
      const route = children.find((r) => r.path === item.path);
      expect(route?.loadChildren, item.path).toBeTypeOf('function');
    }
  });

  it('nút menu đóng/mở thanh bên trên màn hình hẹp', () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const button = host.querySelector<HTMLButtonElement>('.menu-button')!;

    expect(button.getAttribute('aria-expanded')).toBe('false');
    button.click();
    fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(host.querySelector('#sidebar')?.classList).toContain('open');
  });
});
