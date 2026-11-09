import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';

import vi_ from '../../../../../public/i18n/vi.json';
import { FakeAuth } from '../../../../testing/fake-auth';
import { AuthService } from '../../../core/auth/auth.service';
import { todayIso } from '../../../shared/format/date';
import { Employee } from '../employees.api';
import { DEPARTMENTS, POSITIONS, employee } from '../testing-data';
import { EmployeeProfilePage, birthDateValidator } from './employee-profile-page';

describe('EmployeeProfilePage (EMP-01)', () => {
  let fixture: ComponentFixture<EmployeeProfilePage>;
  let host: HTMLElement;
  let http: HttpTestingController;
  let router: Router;

  /** `id` null = trang tạo mới; còn lại trả hồ sơ `loaded`. */
  async function open(id: number | null, loaded: Employee = employee()) {
    await TestBed.configureTestingModule({
      imports: [
        EmployeeProfilePage,
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
        { provide: AuthService, useValue: new FakeAuth().signInAs('HR') },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(EmployeeProfilePage);
    if (id !== null) {
      fixture.componentRef.setInput('id', String(id));
    }
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/departments').flush(DEPARTMENTS);
    http.expectOne((r) => r.url === '/api/v1/positions').flush(POSITIONS);
    if (id === null) {
      http.expectOne('/api/v1/employees/next-code').flush({ code: 'SP0026' });
    } else {
      http.expectOne(`/api/v1/employees/${id}`).flush(loaded);
    }
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  function value(id: string): string {
    return host.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!.value;
  }

  function set(id: string, v: string) {
    const el = host.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    el.value = v;
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input'));
    el.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  function options(id: string): string[] {
    return Array.from(host.querySelectorAll(`#${id} option`)).map((o) =>
      o.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  function submit() {
    host.querySelector<HTMLFormElement>('form.profile')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function errorOf(id: string): string {
    return (
      host
        .querySelector(`#${id}`)!
        .parentElement!.querySelector('.field-error')
        ?.textContent?.trim() ?? ''
    );
  }

  it('tạo mới: điền sẵn mã gợi ý, ngày vào làm hôm nay; chỉ chọn Thử việc hoặc Chính thức', async () => {
    await open(null);

    expect(host.querySelector('h1')!.textContent).toContain('Thêm nhân viên');
    expect(value('emp-code')).toBe('SP0026');
    expect(value('emp-hire')).toBe(todayIso());
    expect(options('emp-status')).toEqual(['Thử việc', 'Chính thức']);
    // Phòng ban, chức danh ngừng hoạt động không có trong ô chọn khi tạo mới
    expect(options('emp-department')).not.toContain('Phòng cũ (Ngừng hoạt động)');
    expect(options('emp-position').some((o) => o.includes('Chức danh cũ'))).toBe(false);
  });

  it('tạo mới: báo lỗi trường bắt buộc, không gọi API khi biểu mẫu sai', async () => {
    await open(null);
    set('emp-code', '');
    submit();

    expect(errorOf('emp-code')).toBe('Vui lòng nhập mã nhân viên');
    expect(errorOf('emp-name')).toBe('Vui lòng nhập họ và tên');
    expect(errorOf('emp-department')).toBe('Vui lòng chọn phòng ban');
    expect(errorOf('emp-position')).toBe('Vui lòng chọn chức danh');
    http.expectNone((r) => r.method === 'POST');
  });

  it('ngày sinh không được trùng hoặc sau ngày vào làm', async () => {
    await open(null);
    set('emp-hire', '2024-01-02');
    set('emp-birth', '2024-01-02');
    expect(errorOf('emp-birth')).toBe('Ngày sinh phải trước ngày vào làm và không ở tương lai');
    set('emp-birth', '1990-01-01');
    expect(errorOf('emp-birth')).toBe('');
  });

  it('tạo mới: gửi dữ liệu đã chuẩn hóa rồi chuyển sang hồ sơ vừa tạo', async () => {
    await open(null);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    set('emp-code', 'sp0026');
    set('emp-name', '  Ngô Thị Hằng ');
    set('emp-gender', 'FEMALE');
    set('emp-department', '2');
    set('emp-position', '10');
    set('emp-work-email', ' thihang.ngo@sspack.vn ');
    submit();

    const req = http.expectOne({ method: 'POST', url: '/api/v1/employees' });
    expect(req.request.body).toEqual({
      code: 'SP0026',
      fullName: 'Ngô Thị Hằng',
      gender: 'FEMALE',
      dateOfBirth: null,
      workEmail: 'thihang.ngo@sspack.vn',
      personalEmail: null,
      phone: null,
      address: null,
      departmentId: 2,
      positionId: 10,
      hireDate: todayIso(),
      status: 'PROBATION',
    });
    req.flush(employee({ id: 26, code: 'SP0026', fullName: 'Ngô Thị Hằng' }));

    expect(navigate).toHaveBeenCalledWith(['/employees', 26], {
      state: { notice: 'Đã thêm nhân viên Ngô Thị Hằng.' },
    });
  });

  it('sửa: điền hồ sơ, giữ được phòng ban/chức danh hiện tại đã ngừng, gửi kèm phiên bản', async () => {
    await open(
      11,
      employee({
        departmentId: 3,
        departmentName: 'Phòng cũ',
        departmentActive: false,
        positionId: 12,
        positionName: 'Chức danh cũ',
        positionActive: false,
      }),
    );

    expect(host.querySelector('h1')!.textContent).toContain('Phạm Văn Đức');
    expect(value('emp-code')).toBe('SP0011');
    expect(value('emp-department')).toBe('3');
    expect(options('emp-department')).toContain('Phòng cũ (Ngừng hoạt động)');
    expect(options('emp-status')).toEqual(['Thử việc', 'Chính thức', 'Nghỉ dài hạn']);

    set('emp-status', 'ON_LEAVE');
    submit();
    const req = http.expectOne({ method: 'PUT', url: '/api/v1/employees/11' });
    expect(req.request.body).toMatchObject({
      departmentId: 3,
      positionId: 12,
      status: 'ON_LEAVE',
      version: 2,
    });
    req.flush(employee({ status: 'ON_LEAVE', version: 3 }));
    fixture.detectChanges();

    expect(host.querySelector('.notice')!.textContent).toContain('Đã lưu hồ sơ Phạm Văn Đức.');
  });

  it('lỗi từ server (mã trùng) hiện thông báo tiếng Việt', async () => {
    await open(11);
    submit();
    http
      .expectOne({ method: 'PUT', url: '/api/v1/employees/11' })
      .flush({ code: 'EMPLOYEE_CODE_EXISTS' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(host.querySelector('.alert')!.textContent).toContain('Mã nhân viên đã tồn tại.');
  });

  it('không được sửa và không được xem thông tin cá nhân: chỉ hiện thông tin công việc', async () => {
    await open(
      11,
      employee({
        editable: false,
        personalVisible: false,
        dateOfBirth: null,
        phone: null,
        gender: null,
      }),
    );

    expect(host.querySelector('form')).toBeNull();
    const facts = host.textContent!.replace(/\s+/g, ' ');
    expect(facts).toContain('15/08/2021');
    expect(facts).toContain('vanduc.pham@sspack.vn');
    expect(facts).not.toContain('Điện thoại');
    expect(facts).toContain('chỉ hiển thị với bộ phận nhân sự và chính nhân viên');
  });

  it('chính chủ xem hồ sơ của mình: thấy thông tin cá nhân, không có biểu mẫu sửa', async () => {
    await open(11, employee({ editable: false, personalVisible: true }));

    expect(host.querySelector('form')).toBeNull();
    const facts = host.textContent!.replace(/\s+/g, ' ');
    expect(facts).toContain('0903110011');
    expect(facts).toContain('15/05/1991');
  });

  it('birthDateValidator: bỏ trống hợp lệ, ngày sinh ở tương lai không hợp lệ', () => {
    const group = (birth: string, hire: string) =>
      new FormGroup({ dateOfBirth: new FormControl(birth), hireDate: new FormControl(hire) });
    expect(birthDateValidator(group('', '2024-01-01'))).toBeNull();
    expect(birthDateValidator(group('1990-01-01', '2024-01-01'))).toBeNull();
    expect(birthDateValidator(group('2999-01-01', '2999-06-01'))).toEqual({ birthDate: true });
  });
});
