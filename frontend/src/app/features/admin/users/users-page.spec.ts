import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import vi_ from '../../../../../public/i18n/vi.json';
import { Page, RoleSummary, UserWithRoles } from '../rbac.models';
import { UsersPage } from './users-page';

const ROLES: Page<RoleSummary> = {
  content: [
    {
      id: 4,
      code: 'EMPLOYEE',
      name: 'Nhân viên',
      description: null,
      dataScope: 'SELF',
      system: true,
      permissionCount: 8,
      assignedUserCount: 3,
      updatedAt: '2026-11-07T09:00:00+07:00',
    },
    {
      id: 9,
      code: 'TRUONG_CA',
      name: 'Trưởng ca',
      description: null,
      dataScope: 'DEPARTMENT',
      system: false,
      permissionCount: 2,
      assignedUserCount: 0,
      updatedAt: '2026-11-07T09:00:00+07:00',
    },
  ],
  page: 0,
  size: 100,
  totalElements: 2,
  totalPages: 1,
};

const USER: UserWithRoles = {
  id: 7,
  email: 'vanduc.pham@sspack.vn',
  status: 'ACTIVE',
  roles: [{ id: 4, code: 'EMPLOYEE', name: 'Nhân viên' }],
  version: 5,
};

describe('UsersPage (PLT-03)', () => {
  let fixture: ComponentFixture<UsersPage>;
  let host: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers();
    // jsdom chưa hỗ trợ đầy đủ <dialog>
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };

    await TestBed.configureTestingModule({
      imports: [
        UsersPage,
        TranslocoTestingModule.forRoot({
          langs: { vi: vi_ },
          translocoConfig: { availableLangs: ['vi', 'en'], defaultLang: 'vi' },
          preloadLangs: true,
        }),
      ],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UsersPage);
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/roles').flush(ROLES);
    vi.advanceTimersByTime(300);
    http
      .expectOne((r) => r.url === '/api/v1/users')
      .flush({ content: [USER], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('liệt kê tài khoản kèm vai trò', () => {
    const row = host.querySelector('tbody tr')!;
    expect(row.textContent).toContain('vanduc.pham@sspack.vn');
    expect(row.textContent).toContain('Nhân viên');
    expect(row.textContent).toContain('Đang hoạt động');
  });

  it('tìm theo email: gửi tham số q sau khi ngừng gõ', () => {
    const input = host.querySelector<HTMLInputElement>('#user-search')!;
    input.value = 'vanduc';
    input.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);

    const req = http.expectOne((r) => r.url === '/api/v1/users');
    expect(req.request.params.get('q')).toBe('vanduc');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(host.textContent).toContain('Không tìm thấy tài khoản nào.');
  });

  it('gán thêm vai trò: gửi danh sách vai trò và version, cập nhật dòng trong bảng', () => {
    host.querySelector<HTMLButtonElement>('tbody .link-button')!.click();
    fixture.detectChanges();
    const truongCa = Array.from(host.querySelectorAll('dialog label.check')).find((l) =>
      l.textContent?.includes('Trưởng ca'),
    )!;
    truongCa.querySelector('input')!.click();
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('dialog button[type="submit"]')!.click();

    const req = http.expectOne('/api/v1/users/7/roles');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ roleIds: [4, 9], version: 5 });
    req.flush({
      ...USER,
      roles: [...USER.roles, { id: 9, code: 'TRUONG_CA', name: 'Trưởng ca' }],
      version: 6,
    });
    fixture.detectChanges();

    expect(host.querySelector('tbody tr')!.textContent).toContain('Trưởng ca');
    expect(host.querySelector('[role="status"]')?.textContent).toContain('vanduc.pham@sspack.vn');
  });

  it('bỏ hết vai trò: nút lưu bị vô hiệu kèm lý do', () => {
    host.querySelector<HTMLButtonElement>('tbody .link-button')!.click();
    fixture.detectChanges();
    const employee = Array.from(host.querySelectorAll('dialog label.check')).find((l) =>
      l.textContent?.includes('Nhân viên'),
    )!;
    employee.querySelector('input')!.click();
    fixture.detectChanges();

    expect(host.querySelector<HTMLButtonElement>('dialog button[type="submit"]')!.disabled).toBe(
      true,
    );
    expect(host.textContent).toContain('Người dùng phải có ít nhất một vai trò.');
  });
});
