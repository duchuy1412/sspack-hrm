import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';

import vi_ from '../../../../../public/i18n/vi.json';
import { FakeAuth, SystemRole } from '../../../../testing/fake-auth';
import { AuthService } from '../../../core/auth/auth.service';
import { JobGrade, Position } from '../org-catalog.api';
import { PositionsPage, normalizeSearch } from './positions-page';

const GRADES: JobGrade[] = [
  {
    id: 1,
    code: 'G2',
    name: 'Công nhân lành nghề',
    minSalary: 8000000,
    maxSalary: 13000000,
    active: true,
    positionCount: 1,
    activePositionCount: 1,
    version: 0,
    updatedAt: '2026-11-09T09:00:00+07:00',
  },
  {
    id: 2,
    code: 'G3',
    name: 'Chuyên viên',
    minSalary: 12000000,
    maxSalary: 20000000,
    active: true,
    positionCount: 1,
    activePositionCount: 1,
    version: 0,
    updatedAt: '2026-11-09T09:00:00+07:00',
  },
  {
    id: 3,
    code: 'G9',
    name: 'Cũ',
    minSalary: 1000000,
    maxSalary: 2000000,
    active: false,
    positionCount: 0,
    activePositionCount: 0,
    version: 0,
    updatedAt: '2026-11-09T09:00:00+07:00',
  },
];

function position(id: number, code: string, name: string, grade: JobGrade, extra: Partial<Position> = {}): Position {
  return {
    id,
    code,
    name,
    jobGradeId: grade.id,
    jobGradeCode: grade.code,
    jobGradeName: grade.name,
    jobGradeActive: grade.active,
    active: true,
    employeeCount: 0,
    workingEmployeeCount: 0,
    version: 3,
    updatedAt: '2026-11-09T09:00:00+07:00',
    ...extra,
  };
}

const POSITIONS = [
  position(10, 'CN-IN', 'Công nhân vận hành máy in', GRADES[0], {
    employeeCount: 5,
    workingEmployeeCount: 4,
  }),
  position(11, 'KS-QC', 'Kỹ sư chất lượng', GRADES[1]),
];

describe('PositionsPage (ORG-02)', () => {
  let fixture: ComponentFixture<PositionsPage>;
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
        PositionsPage,
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
    fixture = TestBed.createComponent(PositionsPage);
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    flushLists(role === 'HR');
  }

  function flushLists(withGrades: boolean) {
    if (withGrades) {
      http.expectOne((r) => r.url === '/api/v1/job-grades').flush(GRADES);
    }
    http.expectOne((r) => r.url === '/api/v1/positions').flush(POSITIONS);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  function codes(): string[] {
    return Array.from(host.querySelectorAll('tbody td.code')).map((e) => e.textContent!.trim());
  }

  function headers(): string[] {
    return Array.from(host.querySelectorAll('thead th')).map((e) => e.textContent!.trim());
  }

  function select(id: string, value: string) {
    const el = host.querySelector<HTMLSelectElement>(`#${id}`)!;
    el.value = value;
    el.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function type(id: string, value: string) {
    const input = host.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('nhân viên chỉ xem: không gọi API khung lương, không có cột khung lương và nút sửa', async () => {
    await setUp('EMPLOYEE');

    expect(codes()).toEqual(['CN-IN', 'KS-QC']);
    expect(headers()).not.toContain('Khung lương');
    expect(host.querySelector('.btn-primary')).toBeNull();
    expect(host.querySelectorAll('tbody button')).toHaveLength(0);
    // Ô lọc cấp bậc lấy từ chính danh sách chức danh
    const options = Array.from(
      host.querySelectorAll<HTMLOptionElement>('#position-grade-filter option'),
    ).map((o) => o.textContent!.trim());
    expect(options).toEqual(['Tất cả cấp bậc', 'G2 – Công nhân lành nghề', 'G3 – Chuyên viên']);
  });

  it('HR thấy khung lương của cấp bậc mỗi chức danh', async () => {
    await setUp('HR');

    expect(headers()).toContain('Khung lương');
    const range = host.querySelector('tr[data-id="11"] .range')!.textContent!.trim();
    expect(range).toBe('12.000.000 ₫ – 20.000.000 ₫');
    // Chức danh đã có nhân viên thì không có nút Xóa
    const staffed = Array.from(host.querySelectorAll('tr[data-id="10"] button')).map((b) =>
      b.childNodes[0].textContent!.trim(),
    );
    expect(staffed).toEqual(['Sửa', 'Ngừng hoạt động']);
  });

  it('lọc theo từ khóa không dấu và theo cấp bậc', async () => {
    await setUp('HR');

    type('position-search', 'ky su');
    expect(codes()).toEqual(['KS-QC']);

    type('position-search', '');
    select('position-grade-filter', '1');
    expect(codes()).toEqual(['CN-IN']);

    type('position-search', 'ky su');
    expect(host.querySelector('tbody')!.textContent).toContain('Không có chức danh nào khớp bộ lọc');
  });

  it('thêm chức danh: chỉ chọn được cấp bậc đang hoạt động, hiện khung lương, gửi đúng body', async () => {
    await setUp('HR');
    host.querySelector<HTMLButtonElement>('.btn-primary')!.click();
    fixture.detectChanges();

    const options = Array.from(
      host.querySelectorAll<HTMLOptionElement>('#position-grade option'),
    ).map((o) => o.value);
    expect(options).toEqual(['', '1', '2']);

    type('position-code', 'nv-kho');
    type('position-name', 'Nhân viên kho');
    select('position-grade', '2');
    expect(host.querySelector('#position-grade-range')!.textContent).toContain(
      'Khung lương: 12.000.000 ₫ – 20.000.000 ₫',
    );

    host.querySelector<HTMLFormElement>('dialog form')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/positions');
    expect(req.request.body).toEqual({ code: 'NV-KHO', name: 'Nhân viên kho', jobGradeId: 2 });
    req.flush(position(12, 'NV-KHO', 'Nhân viên kho', GRADES[1]));
    flushLists(true);
    expect(host.querySelector('.notice')!.textContent).toContain('Đã thêm chức danh Nhân viên kho');
  });

  it('sửa chức danh có cấp bậc đã ngừng: vẫn giữ được cấp bậc hiện tại trong ô chọn', async () => {
    await setUp('HR');
    const fixtureComponent = fixture.componentInstance as unknown as {
      openEdit(p: Position): void;
    };
    fixtureComponent.openEdit(position(13, 'OLD', 'Chức danh cũ', GRADES[2]));
    fixture.detectChanges();

    const options = Array.from(
      host.querySelectorAll<HTMLOptionElement>('#position-grade option'),
    ).map((o) => o.value);
    expect(options).toEqual(['', '1', '2', '3']);
    expect(host.querySelector<HTMLSelectElement>('#position-grade')!.value).toBe('3');
  });

  it('bỏ trống cấp bậc: báo lỗi, không gọi API', async () => {
    await setUp('HR');
    host.querySelector<HTMLButtonElement>('.btn-primary')!.click();
    fixture.detectChanges();
    type('position-code', 'AB');
    type('position-name', 'Thử');
    select('position-grade', '');
    host.querySelector<HTMLFormElement>('dialog form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(host.querySelector('dialog')!.textContent).toContain('Vui lòng chọn cấp bậc');
    http.expectNone((r) => r.method === 'POST');
  });

  it('normalizeSearch bỏ dấu tiếng Việt và chữ đ', () => {
    expect(normalizeSearch('  Kỹ sư Đảm bảo ')).toBe('ky su dam bao');
  });
});
