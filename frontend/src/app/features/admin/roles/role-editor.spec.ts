import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import vi_ from '../../../../../public/i18n/vi.json';
import { PermissionItem, RoleDetail } from '../rbac.models';
import { RoleEditorPage } from './role-editor';

const PERMISSIONS: PermissionItem[] = [
  { code: 'leave:approve', module: 'leave', description: 'Duyệt hoặc từ chối đơn nghỉ' },
  { code: 'leave:read', module: 'leave', description: 'Xem đơn nghỉ' },
  { code: 'role:manage', module: 'platform', description: 'Quản lý vai trò và gán quyền' },
  { code: 'user:manage', module: 'platform', description: 'Quản lý tài khoản người dùng' },
];

function role(overrides: Partial<RoleDetail> = {}): RoleDetail {
  return {
    id: 9,
    code: 'TRUONG_CA',
    name: 'Trưởng ca',
    description: null,
    dataScope: 'DEPARTMENT',
    system: false,
    permissions: ['leave:read'],
    assignedUserCount: 0,
    version: 3,
    updatedAt: '2026-11-07T09:00:00+07:00',
    ...overrides,
  };
}

describe('RoleEditorPage (PLT-03)', () => {
  let fixture: ComponentFixture<RoleEditorPage>;
  let host: HTMLElement;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  async function setUp(id?: string, loaded?: RoleDetail) {
    await TestBed.configureTestingModule({
      imports: [
        RoleEditorPage,
        TranslocoTestingModule.forRoot({
          langs: { vi: vi_ },
          translocoConfig: { availableLangs: ['vi', 'en'], defaultLang: 'vi' },
          preloadLangs: true,
        }),
      ],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(RoleEditorPage);
    if (id) {
      fixture.componentRef.setInput('id', id);
    }
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();

    http.expectOne('/api/v1/permissions').flush(PERMISSIONS);
    if (loaded) {
      http.expectOne(`/api/v1/roles/${loaded.id}`).flush(loaded);
    }
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  function type(selector: string, value: string): void {
    const input = host.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  function checkbox(text: string): HTMLInputElement {
    const label = Array.from(host.querySelectorAll('label.check')).find((l) =>
      l.textContent?.includes(text),
    );
    return label!.querySelector('input')!;
  }

  function click(input: HTMLInputElement): void {
    input.click();
    fixture.detectChanges();
  }

  function submit(): void {
    host.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    fixture.detectChanges();
  }

  it('quyền hiện thành ô tích nhóm theo phân hệ', async () => {
    await setUp();

    const legends = Array.from(host.querySelectorAll('legend')).map((l) => l.textContent?.trim());
    expect(legends).toEqual(['Nghỉ phép', 'Nền tảng']);
    expect(host.querySelectorAll('fieldset label.check:not(.group-check)')).toHaveLength(4);
  });

  it('tạo vai trò: gửi mã in hoa và các quyền đã chọn, xong quay về danh sách', async () => {
    await setUp();
    type('#role-code', 'truong_ca');
    type('#role-name', '  Trưởng ca  ');
    click(checkbox('Nghỉ phép'));

    submit();

    const req = http.expectOne('/api/v1/roles');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      code: 'TRUONG_CA',
      name: 'Trưởng ca',
      description: null,
      dataScope: 'DEPARTMENT',
      permissions: ['leave:approve', 'leave:read'],
    });
    req.flush(role());
    expect(navigate).toHaveBeenCalledWith(['/admin/roles']);
  });

  it('chưa chọn quyền hoặc thiếu trường: báo lỗi, không gọi API', async () => {
    await setUp();

    submit();

    expect(host.textContent).toContain('Vui lòng nhập mã vai trò');
    expect(host.textContent).toContain('Vui lòng nhập tên vai trò');
    expect(host.textContent).toContain('Vui lòng chọn ít nhất một quyền.');
    http.expectNone('/api/v1/roles');
  });

  it('sửa vai trò: mã bị khóa, gửi kèm version; lỗi sửa trùng hiện thông báo', async () => {
    await setUp('9', role());
    expect(host.querySelector<HTMLInputElement>('#role-code')!.disabled).toBe(true);
    expect(checkbox('Xem đơn nghỉ').checked).toBe(true);

    click(checkbox('Duyệt hoặc từ chối'));
    submit();

    const req = http.expectOne('/api/v1/roles/9');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.version).toBe(3);
    expect(req.request.body.permissions).toEqual(['leave:read', 'leave:approve']);
    req.flush(
      { code: 'CONCURRENT_MODIFICATION', detail: 'x' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(host.querySelector('[role="alert"]')?.textContent).toContain(
      'Dữ liệu vừa được người khác thay đổi',
    );
    expect(navigate).not.toHaveBeenCalled();
  });

  it('vai trò hệ thống: không đổi phạm vi dữ liệu, không có nút xóa', async () => {
    await setUp('1', role({ id: 1, code: 'HR', system: true, dataScope: 'ALL' }));

    expect(host.querySelector<HTMLSelectElement>('#role-scope')!.disabled).toBe(true);
    expect(host.querySelector('.btn-danger')).toBeNull();
  });

  it('vai trò ADMIN: khóa ô quyền quản lý vai trò', async () => {
    await setUp(
      '1',
      role({ id: 1, code: 'ADMIN', system: true, permissions: ['role:manage', 'user:manage'] }),
    );

    expect(checkbox('Quản lý vai trò').disabled).toBe(true);
    expect(host.textContent).toContain('luôn giữ quyền quản lý vai trò');
  });

  it('vai trò đang gán: nút xóa bị vô hiệu kèm lý do', async () => {
    await setUp('9', role({ assignedUserCount: 2 }));

    expect(host.querySelector<HTMLButtonElement>('.btn-danger')!.disabled).toBe(true);
    expect(host.textContent).toContain('Đang gán cho 2 người dùng');
  });

  it('xóa vai trò sau khi xác nhận', async () => {
    await setUp('9', role());
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    host.querySelector<HTMLButtonElement>('.btn-danger')!.click();

    const req = http.expectOne('/api/v1/roles/9');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(navigate).toHaveBeenCalledWith(['/admin/roles']);
  });
});
