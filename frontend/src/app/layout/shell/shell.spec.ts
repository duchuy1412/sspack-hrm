import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';

import vi from '../../../../public/i18n/vi.json';
import { FakeAuth, SystemRole } from '../../../testing/fake-auth';
import { routes } from '../../app.routes';
import { AuthService } from '../../core/auth/auth.service';
import { NAV_ITEMS } from '../nav-items';
import { Shell } from './shell';

describe('Shell', () => {
  let auth: FakeAuth;

  beforeEach(async () => {
    auth = new FakeAuth();
    await TestBed.configureTestingModule({
      imports: [
        Shell,
        TranslocoTestingModule.forRoot({
          langs: { vi },
          translocoConfig: { availableLangs: ['vi', 'en'], defaultLang: 'vi' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
  });

  function render(...roles: SystemRole[]): HTMLElement {
    auth.signInAs(...roles);
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function labels(host: HTMLElement): (string | undefined)[] {
    return Array.from(host.querySelectorAll('.nav-item .nav-label')).map((el) =>
      el.textContent?.trim(),
    );
  }

  it('HR thấy đủ 9 phân hệ nghiệp vụ theo đúng thứ tự mockup, không thấy Quản trị', () => {
    expect(labels(render('HR'))).toEqual([
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

  it('PLT-03: ẩn menu không có quyền theo từng vai trò', () => {
    expect(labels(render('EMPLOYEE'))).toEqual([
      'Nhân viên',
      'Cơ cấu tổ chức',
      'Nghỉ phép',
      'Chấm công',
      'Tính lương',
    ]);
  });

  it('PLT-03: Admin thấy mục Quản trị', () => {
    expect(labels(render('ADMIN'))).toEqual([
      'Nhân viên',
      'Cơ cấu tổ chức',
      'Tính lương',
      'Quản trị',
    ]);
  });

  it('Người phỏng vấn chỉ thấy Tuyển dụng', () => {
    expect(labels(render('INTERVIEWER'))).toEqual(['Tuyển dụng']);
  });

  it('Quản lý kiêm nhân viên: hợp quyền của hai vai trò', () => {
    expect(labels(render('MANAGER', 'EMPLOYEE'))).toEqual([
      'Tổng quan',
      'Nhân viên',
      'Cơ cấu tổ chức',
      'Nghỉ phép',
      'Duyệt đơn',
      'Chấm công',
      'Tính lương',
      'Tuyển dụng',
    ]);
  });

  it('menu cập nhật ngay khi quyền thay đổi (sau lần làm mới phiên)', () => {
    auth.signInAs('EMPLOYEE');
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    expect(labels(fixture.nativeElement)).not.toContain('Duyệt đơn');

    auth.signInAs('MANAGER', 'EMPLOYEE');
    fixture.detectChanges();
    expect(labels(fixture.nativeElement)).toContain('Duyệt đơn');
  });

  it('mỗi mục trỏ tới route lazy-load của phân hệ', () => {
    const hrefs = Array.from(
      render('HR', 'ADMIN').querySelectorAll<HTMLAnchorElement>('.nav-item'),
    ).map((a) => a.getAttribute('href'));

    expect(hrefs).toEqual(NAV_ITEMS.map((item) => `/${item.path}`));
  });

  it('mọi trang trong shell yêu cầu đăng nhập', () => {
    expect(routes.find((r) => r.path === '')?.canActivate?.length).toBe(1);
  });

  it('mỗi phân hệ có route lazy-load riêng và guard quyền (không chỉ ẩn menu)', () => {
    const children = routes.find((r) => r.path === '')?.children ?? [];
    for (const item of NAV_ITEMS) {
      const route = children.find((r) => r.path === item.path);
      expect(route?.loadChildren, item.path).toBeTypeOf('function');
      expect(route?.canActivate?.length, item.path).toBe(1);
    }
  });

  it('nút menu đóng/mở thanh bên trên màn hình hẹp', () => {
    auth.signInAs('EMPLOYEE');
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
