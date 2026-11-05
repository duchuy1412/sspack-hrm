import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import vi_ from '../../../../../public/i18n/vi.json';
import { AuthService } from '../../../core/auth/auth.service';
import { LoginPage } from './login';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let host: HTMLElement;
  const login = vi.fn();

  beforeEach(async () => {
    login.mockReset();
    await TestBed.configureTestingModule({
      imports: [
        LoginPage,
        TranslocoTestingModule.forRoot({
          langs: { vi: vi_ },
          translocoConfig: { availableLangs: ['vi', 'en'], defaultLang: 'vi' },
          preloadLangs: true,
        }),
      ],
      providers: [provideRouter([]), { provide: AuthService, useValue: { login } }],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginPage);
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  function type(selector: string, value: string): void {
    const input = host.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  function submit(): void {
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('hiển thị đúng nội dung tiếng Việt theo mockup', () => {
    expect(host.querySelector('h2')?.textContent).toContain('Đăng nhập');
    expect(host.querySelector('button[type="submit"]')?.textContent).toContain('Đăng nhập');
    expect(host.querySelector('.hero h1')?.textContent).toContain('Quản lý nhân sự SS Pack');
  });

  it('bỏ trống thì báo lỗi từng trường và không gọi API', () => {
    submit();

    expect(login).not.toHaveBeenCalled();
    expect(host.querySelector('#email-error')?.textContent).toContain('Vui lòng nhập email');
    expect(host.querySelector('#password-error')?.textContent).toContain('Vui lòng nhập mật khẩu');
  });

  it('email sai định dạng', () => {
    type('#email', 'khong-phai-email');
    fixture.detectChanges();

    expect(host.querySelector('#email-error')?.textContent).toContain('Email không đúng định dạng');
  });

  it('đăng nhập thành công thì chuyển tới trang đích', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.componentRef.setInput('returnUrl', '/leave');
    login.mockReturnValue(of({}));

    type('#email', ' vanduc.pham@sspack.vn ');
    type('#password', 'Demo@2026');
    submit();

    expect(login).toHaveBeenCalledWith('vanduc.pham@sspack.vn', 'Demo@2026', false);
    expect(navigate).toHaveBeenCalledWith('/leave');
  });

  it('tài khoản bị khóa: báo số phút còn lại', () => {
    login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 423,
            error: { code: 'AUTH_ACCOUNT_LOCKED', retryAfterSeconds: 610 },
          }),
      ),
    );

    type('#email', 'vanduc.pham@sspack.vn');
    type('#password', 'sai');
    submit();

    expect(host.querySelector('[role="alert"]')?.textContent).toContain(
      'Tài khoản tạm khóa do đăng nhập sai nhiều lần. Vui lòng thử lại sau 11 phút.',
    );
    expect(host.querySelector<HTMLInputElement>('#password')!.value).toBe('');
  });

  it('sai mật khẩu: thông báo chung', () => {
    login.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 401, error: { code: 'AUTH_INVALID_CREDENTIALS' } }),
      ),
    );

    type('#email', 'vanduc.pham@sspack.vn');
    type('#password', 'sai');
    submit();

    expect(host.querySelector('[role="alert"]')?.textContent).toContain(
      'Email hoặc mật khẩu không đúng.',
    );
  });

  it('bấm tài khoản dùng thử thì điền sẵn email', () => {
    const demo = Array.from(host.querySelectorAll<HTMLButtonElement>('.demo .link-button')).find(
      (b) => b.textContent?.includes('thuha.le@sspack.vn'),
    )!;
    demo.click();
    fixture.detectChanges();

    expect(host.querySelector<HTMLInputElement>('#email')!.value).toBe('thuha.le@sspack.vn');
  });
});
