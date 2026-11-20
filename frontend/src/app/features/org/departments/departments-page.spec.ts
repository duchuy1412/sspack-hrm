import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import vi_ from '../../../../../public/i18n/vi.json';
import { FakeAuth, SystemRole } from '../../../../testing/fake-auth';
import { AuthService } from '../../../core/auth/auth.service';
import { EmployeeSummary } from '../../employees/employees.api';
import { Department, PersonRef } from './department.models';
import { DepartmentsPage } from './departments-page';

function dept(id: number, path: string, name: string, extra: Partial<Department> = {}): Department {
  const ids = path.split('/').filter(Boolean).map(Number);
  return {
    id,
    parentId: ids.length > 1 ? ids[ids.length - 2] : null,
    code: `D${id}`,
    name,
    path,
    depth: ids.length,
    active: true,
    childCount: 0,
    employeeCount: 0,
    workingEmployeeCount: 0,
    head: null,
    effectiveApprover: null,
    version: 2,
    updatedAt: '2026-11-07T09:00:00+07:00',
    ...extra,
  };
}

const LIST = [
  dept(1, '/1/', 'Nhà máy', { childCount: 1 }),
  dept(2, '/1/2/', 'Xưởng In', { childCount: 1, workingEmployeeCount: 12, employeeCount: 14 }),
  dept(3, '/1/2/3/', 'Tổ In offset'),
  dept(5, '/5/', 'Khối Văn phòng'),
];

describe('DepartmentsPage (ORG-01)', () => {
  let fixture: ComponentFixture<DepartmentsPage>;
  let host: HTMLElement;
  let http: HttpTestingController;

  async function setUp(role: SystemRole) {
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
    await TestBed.configureTestingModule({
      imports: [
        DepartmentsPage,
        TranslocoTestingModule.forRoot({
          langs: { vi: vi_ },
          translocoConfig: { availableLangs: ['vi', 'en'], defaultLang: 'vi' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: new FakeAuth().signInAs(role) },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DepartmentsPage);
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    flushList();
  }

  function flushList(list = LIST) {
    http.expectOne((r) => r.url === '/api/v1/departments').flush(list);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  function row(id: number): HTMLElement {
    return host.querySelector<HTMLElement>(`.row[data-id="${id}"]`)!;
  }

  function names(): string[] {
    return Array.from(host.querySelectorAll('.dept-name')).map((e) => e.textContent!.trim());
  }

  function dragEvent(type: string): DragEvent {
    const event = new Event(type, { bubbles: true, cancelable: true }) as DragEvent;
    Object.defineProperty(event, 'dataTransfer', {
      value: { setData: () => undefined, effectAllowed: '' },
    });
    return event;
  }

  it('hiển thị cây nhiều cấp theo thứ tự tên, kèm số nhân viên đang làm', async () => {
    await setUp('HR');

    expect(names()).toEqual(['Khối Văn phòng', 'Nhà máy', 'Xưởng In', 'Tổ In offset']);
    expect(row(2).textContent).toContain('12 nhân viên');
    expect(host.querySelector('[role="treeitem"] [role="group"] [role="group"]')).not.toBeNull();
  });

  it('Nhân viên (chỉ org:read): xem cây, không có nút sửa, không kéo được', async () => {
    await setUp('EMPLOYEE');

    expect(names()).toHaveLength(4);
    expect(host.querySelector('.actions')).toBeNull();
    expect(host.textContent).not.toContain('Thêm phòng ban');
    expect(row(2).getAttribute('draggable')).toBe('false');
  });

  it('kéo thả: thả Xưởng In lên Khối Văn phòng gửi lệnh đổi cấp cha kèm version', async () => {
    await setUp('HR');

    row(2).dispatchEvent(dragEvent('dragstart'));
    fixture.detectChanges();
    // Không thả được vào chính nó, cấp dưới của nó hay cấp cha hiện tại
    expect(row(3).classList).toContain('drop-blocked');
    expect(row(1).classList).toContain('drop-blocked');
    const over = dragEvent('dragover');
    row(5).dispatchEvent(over);
    fixture.detectChanges();
    expect(over.defaultPrevented).toBe(true);
    expect(row(5).classList).toContain('drop-ok');

    row(5).dispatchEvent(dragEvent('drop'));

    const req = http.expectOne('/api/v1/departments/2/parent');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ parentId: 5, version: 2 });
    req.flush(dept(2, '/5/2/', 'Xưởng In'));
    flushList();
    expect(host.querySelector('[role="status"]')?.textContent).toContain(
      'Đã chuyển Xưởng In vào Khối Văn phòng',
    );
  });

  it('thả vào phòng ban cấp dưới: trình duyệt không cho thả, không gọi API', async () => {
    await setUp('HR');

    row(1).dispatchEvent(dragEvent('dragstart'));
    const over = dragEvent('dragover');
    row(3).dispatchEvent(over);
    row(3).dispatchEvent(dragEvent('drop'));

    expect(over.defaultPrevented).toBe(false);
    http.expectNone('/api/v1/departments/1/parent');
  });

  it('"Chuyển đến…": chỉ liệt kê nơi hợp lệ, có lựa chọn cấp cao nhất', async () => {
    await setUp('HR');
    const moveButton = Array.from(row(2).querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Chuyển đến'),
    )!;

    moveButton.click();
    fixture.detectChanges();

    const options = Array.from(
      host.querySelectorAll<HTMLOptionElement>('#dept-move-target option'),
    ).map((o) => o.textContent!.trim());
    expect(options).toEqual([
      'Chọn phòng ban',
      'Cấp cao nhất',
      'Khối Văn phòng',
      'Nhà máy (hiện tại)',
    ]);
    expect(
      host.querySelector<HTMLOptionElement>('#dept-move-target option:last-child')!.disabled,
    ).toBe(true);
  });

  it('lỗi nghiệp vụ hiện bằng câu tiếng Việt có số lượng', async () => {
    await setUp('HR');
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const deleteButton = Array.from(row(5).querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Xóa'),
    )!;

    deleteButton.click();
    http
      .expectOne('/api/v1/departments/5')
      .flush(
        { code: 'DEPARTMENT_HAS_EMPLOYEES', employeeCount: 3, detail: 'x' },
        { status: 409, statusText: 'Conflict' },
      );
    flushList();

    expect(host.querySelector('[role="alert"]')?.textContent).toContain('đã có 3 nhân viên');
  });

  it('thêm phòng ban cấp dưới: gửi mã in hoa và cấp trên', async () => {
    await setUp('HR');
    const addChild = Array.from(row(1).querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Thêm cấp dưới'),
    )!;
    addChild.click();
    fixture.detectChanges();
    expect(host.querySelector('dialog')?.textContent).toContain('Cấp trên: Nhà máy');

    const type = (selector: string, value: string) => {
      const input = host.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    type('#dept-code', 'kho-vat-tu');
    type('#dept-name', 'Kho vật tư');
    host.querySelector<HTMLButtonElement>('dialog button[type="submit"]')!.click();

    const req = http.expectOne((r) => r.url === '/api/v1/departments' && r.method === 'POST');
    expect(req.request.body).toEqual({ code: 'KHO-VAT-TU', name: 'Kho vật tư', parentId: 1 });
    req.flush(dept(9, '/1/9/', 'Kho vật tư'));
    flushList();
  });

  it('tìm kiếm: giữ phòng ban khớp và các cấp trên của nó', async () => {
    await setUp('HR');
    const search = host.querySelector<HTMLInputElement>('#dept-search')!;
    search.value = 'offset';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(names()).toEqual(['Nhà máy', 'Xưởng In', 'Tổ In offset']);
  });

  // ---- ORG-04: trưởng đơn vị

  function person(id: number, fullName: string, warnings: PersonRef['warnings'] = []): PersonRef {
    return { employeeId: id, code: `NV${id}`, fullName, warnings };
  }

  function employee(id: number, fullName: string): EmployeeSummary {
    return {
      id,
      code: `NV${id}`,
      fullName,
      workEmail: null,
      departmentId: 2,
      departmentName: 'Xưởng In',
      positionId: null,
      positionName: null,
      hireDate: '2025-01-01',
      status: 'ACTIVE',
    };
  }

  const WITH_HEAD = [
    dept(1, '/1/', 'Nhà máy', { childCount: 1, effectiveApprover: person(9, 'Lê Giám Đốc') }),
    dept(2, '/1/2/', 'Xưởng In', {
      head: person(7, 'Trần Minh Anh', ['NO_ACCOUNT']),
      effectiveApprover: person(7, 'Trần Minh Anh', ['NO_ACCOUNT']),
    }),
    dept(3, '/1/2/3/', 'Tổ In offset', {
      effectiveApprover: person(7, 'Trần Minh Anh', ['NO_ACCOUNT']),
    }),
    dept(5, '/5/', 'Khối Văn phòng', {
      head: person(8, 'Phạm Văn Đức', ['NO_MANAGER_SCOPE']),
      effectiveApprover: person(8, 'Phạm Văn Đức', ['NO_MANAGER_SCOPE']),
      active: false,
    }),
  ];

  function button(scope: HTMLElement, text: string): HTMLButtonElement | undefined {
    return Array.from(scope.querySelectorAll('button')).find((b) => b.textContent?.includes(text));
  }

  async function openHeadDialog(): Promise<void> {
    button(row(3), 'Chỉ định trưởng')!.click();
    http
      .expectOne((r) => r.url === '/api/v1/employees')
      .flush({
        content: [employee(7, 'Trần Minh Anh'), employee(8, 'Phạm Văn Đức')],
        page: 0,
        size: 100,
        totalElements: 2,
        totalPages: 1,
      });
    fixture.detectChanges();
  }

  it('hàng phòng ban hiện trưởng; phòng chưa có trưởng hiện người duyệt thay thế', async () => {
    await setUp('HR');
    // list đầu của setUp đã flush LIST; thay bằng dữ liệu có trưởng
    fixture.componentInstance['departments'].set(WITH_HEAD);
    fixture.detectChanges();

    expect(row(2).textContent).toContain('Trưởng: Trần Minh Anh');
    expect(row(3).textContent).toContain('Trưởng: —');
    expect(row(3).textContent).toContain('(duyệt bởi Trần Minh Anh)');
    expect(row(2).textContent).not.toContain('duyệt bởi');
  });

  it('cảnh báo trưởng thiếu tài khoản hoặc phạm vi quản lý, có title', async () => {
    await setUp('HR');
    fixture.componentInstance['departments'].set(WITH_HEAD);
    fixture.detectChanges();

    const titles = (id: number) =>
      Array.from(row(id).querySelectorAll<HTMLElement>('.head-warning')).map((e) =>
        e.getAttribute('title'),
      );
    expect(titles(2)).toEqual([vi_.org.departments.head.warnings.NO_ACCOUNT]);
    expect(titles(5)).toEqual([vi_.org.departments.head.warnings.NO_MANAGER_SCOPE]);
    expect(titles(2)[0]).toContain('Quản trị');
  });

  it('chỉ định trưởng: gõ không dấu lọc đúng người, Lưu gọi setHead kèm version', async () => {
    await setUp('HR');
    fixture.componentInstance['departments'].set(WITH_HEAD);
    fixture.detectChanges();
    await openHeadDialog();

    const labels = () =>
      Array.from(host.querySelectorAll('.head-option')).map((e) => e.textContent!.trim());
    expect(labels()).toHaveLength(2);
    const search = host.querySelector<HTMLInputElement>('#head-search')!;
    search.value = 'minh anh';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(labels()).toHaveLength(1);
    expect(labels()[0]).toContain('Trần Minh Anh');

    host.querySelector<HTMLInputElement>('.head-option input')!.click();
    fixture.detectChanges();
    button(host.querySelector('#head-dialog')!, 'Lưu')!.click();

    const req = http.expectOne('/api/v1/departments/3/head');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ employeeId: 7, version: 2 });
    req.flush(WITH_HEAD[2]);
    flushList();
  });

  it('"Bỏ chỉ định" gọi setHead với null', async () => {
    await setUp('HR');
    fixture.componentInstance['departments'].set(WITH_HEAD);
    fixture.detectChanges();
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    button(row(2), 'Bỏ chỉ định')!.click();

    const req = http.expectOne('/api/v1/departments/2/head');
    expect(req.request.body).toEqual({ employeeId: null, version: 2 });
    req.flush(dept(2, '/1/2/', 'Xưởng In'));
    flushList();
  });

  it('lỗi 422 hiện thông báo từ org.errors trong hộp thoại', async () => {
    await setUp('HR');
    fixture.componentInstance['departments'].set(WITH_HEAD);
    fixture.detectChanges();
    await openHeadDialog();

    host.querySelector<HTMLInputElement>('.head-option input')!.click();
    fixture.detectChanges();
    button(host.querySelector('#head-dialog')!, 'Lưu')!.click();
    http
      .expectOne('/api/v1/departments/3/head')
      .flush(
        { code: 'DEPARTMENT_HEAD_NOT_WORKING', detail: 'x' },
        { status: 422, statusText: 'Unprocessable' },
      );
    fixture.detectChanges();

    expect(host.querySelector('#head-dialog [role="alert"]')?.textContent).toContain(
      vi_.org.errors.DEPARTMENT_HEAD_NOT_WORKING,
    );
  });

  it('phòng ngừng hoạt động: ẩn "Chỉ định trưởng", còn "Bỏ chỉ định" khi có trưởng', async () => {
    await setUp('HR');
    fixture.componentInstance['departments'].set(WITH_HEAD);
    fixture.detectChanges();

    expect(button(row(5), 'Chỉ định trưởng')).toBeUndefined();
    expect(button(row(5), 'Bỏ chỉ định')).toBeDefined();
  });

  it('Nhân viên (chỉ xem): vẫn thấy trưởng nhưng không có nút chỉ định', async () => {
    await setUp('EMPLOYEE');
    fixture.componentInstance['departments'].set(WITH_HEAD);
    fixture.detectChanges();

    expect(row(2).textContent).toContain('Trưởng: Trần Minh Anh');
    expect(button(row(2), 'Bỏ chỉ định')).toBeUndefined();
    expect(button(row(2), 'Chỉ định trưởng')).toBeUndefined();
  });

  it('ô chọn trưởng tải mọi trang nhân viên (server giới hạn 100/trang)', async () => {
    await setUp('HR');
    fixture.componentInstance['departments'].set(WITH_HEAD);
    fixture.detectChanges();
    button(row(3), 'Chỉ định trưởng')!.click();

    const first = http.expectOne(
      (r) => r.url === '/api/v1/employees' && r.params.get('page') === '0',
    );
    expect(first.request.params.get('size')).toBe('100');
    first.flush({
      content: [employee(7, 'Trần Minh Anh')],
      page: 0,
      size: 100,
      totalElements: 101,
      totalPages: 2,
    });
    const second = http.expectOne(
      (r) => r.url === '/api/v1/employees' && r.params.get('page') === '1',
    );
    second.flush({
      content: [employee(8, 'Phạm Văn Đức')],
      page: 1,
      size: 100,
      totalElements: 101,
      totalPages: 2,
    });
    fixture.detectChanges();

    const labels = Array.from(host.querySelectorAll('.head-option')).map((e) => e.textContent);
    expect(labels).toHaveLength(2);
    expect(labels.join()).toContain('Phạm Văn Đức');
  });
});
