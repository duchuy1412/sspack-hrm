import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';

import vi_ from '../../../../../public/i18n/vi.json';
import { FakeAuth, SystemRole } from '../../../../testing/fake-auth';
import { AuthService } from '../../../core/auth/auth.service';
import { DEPARTMENTS, summary } from '../testing-data';
import { EmployeesPage, departmentOptions } from './employees-page';

const ROWS = [
  summary(10, 'SP0010', 'Trần Minh Anh', { positionName: 'Quản đốc xưởng' }),
  summary(11, 'SP0011', 'Phạm Văn Đức', { status: 'PROBATION' }),
];

describe('EmployeesPage (EMP-01)', () => {
  let fixture: ComponentFixture<EmployeesPage>;
  let host: HTMLElement;
  let http: HttpTestingController;

  /** `inspect` xem request danh sách đầu tiên trước khi trả kết quả. */
  async function setUp(role: SystemRole, inspect: (req: TestRequest) => void = () => undefined) {
    await TestBed.configureTestingModule({
      imports: [
        EmployeesPage,
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
    fixture = TestBed.createComponent(EmployeesPage);
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/departments').flush(DEPARTMENTS);
    const first = expectList();
    inspect(first);
    respond(first, ROWS, 45);
  }

  afterEach(() => http.verify());

  function expectList(): TestRequest {
    return http.expectOne((r) => r.url === '/api/v1/employees');
  }

  function respond(req: TestRequest, content = ROWS, total = content.length) {
    const page = Number(req.request.params.get('page'));
    req.flush({ content, page, size: 20, totalElements: total, totalPages: Math.ceil(total / 20) });
    fixture.detectChanges();
  }

  function select(id: string, value: string) {
    const el = host.querySelector<HTMLSelectElement>(`#${id}`)!;
    el.value = value;
    el.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function text(selector: string): string {
    return host.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('mặc định lọc nhân viên đang làm việc, trang đầu, 20 dòng', async () => {
    await setUp('HR', (req) => {
      expect(req.request.params.getAll('status')).toEqual(['PROBATION', 'ACTIVE', 'ON_LEAVE']);
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('20');
      expect(req.request.params.has('q')).toBe(false);
    });
  });

  it('hiện các cột theo mockup: mã, họ tên kèm email, phòng ban, chức danh, ngày vào làm dd/MM/yyyy, trạng thái', async () => {
    await setUp('HR');

    const first = host.querySelector('tbody tr')!;
    expect(first.querySelector('td.code')!.textContent!.trim()).toBe('SP0010');
    expect(first.querySelector('.person-name')!.getAttribute('href')).toBe('/10');
    expect(first.textContent).toContain('sp0010@sspack.vn');
    expect(first.textContent).toContain('Quản đốc xưởng');
    expect(first.querySelector('td.date')!.textContent!.trim()).toBe('15/08/2021');
    expect(host.querySelectorAll('.chip.status')[1].textContent!.trim()).toBe('Thử việc');
    expect(text('.pager')).toContain('Hiển thị 1–2 trong 45 nhân viên');
  });

  it('HR thấy nút Thêm nhân viên; quản lý và nhân viên thì không', async () => {
    await setUp('HR');
    expect(text('.page-header')).toContain('Thêm nhân viên');

    TestBed.resetTestingModule();
    await setUp('MANAGER');
    expect(text('.page-header')).not.toContain('Thêm nhân viên');
  });

  it('ô phòng ban xếp theo cây, thụt lề cấp con', async () => {
    await setUp('HR');
    const labels = Array.from(host.querySelectorAll('#employee-department option')).map(
      (o) => o.textContent!,
    );
    expect(labels.map((l) => l.trim())).toEqual([
      'Tất cả phòng ban',
      'Nhà máy Bình Dương',
      'Xưởng In',
      'Phòng cũ',
    ]);
    expect(departmentOptions(DEPARTMENTS)[1].label.startsWith('   ')).toBe(true);
  });

  it('đổi phòng ban, trạng thái thì tải lại từ trang đầu với tham số tương ứng', async () => {
    await setUp('HR');
    host.querySelectorAll<HTMLButtonElement>('.pager-buttons .btn')[1].click();
    respond(expectList(), ROWS, 45);

    select('employee-department', '1');
    let req = expectList();
    expect(req.request.params.get('departmentId')).toBe('1');
    expect(req.request.params.get('page')).toBe('0');
    respond(req);

    select('employee-status', 'left');
    req = expectList();
    expect(req.request.params.getAll('status')).toEqual(['RESIGNED', 'TERMINATED']);
    respond(req);

    select('employee-status', 'all');
    req = expectList();
    expect(req.request.params.has('status')).toBe(false);
    respond(req, [], 0);
    expect(text('tbody')).toBe('Không có nhân viên nào khớp bộ lọc.');
  });

  it('gõ tìm kiếm: chờ 300 ms rồi mới gọi API', async () => {
    vi.useFakeTimers();
    try {
      await setUp('HR');
      const input = host.querySelector<HTMLInputElement>('#employee-search')!;
      input.value = 'nguyen van';
      input.dispatchEvent(new Event('input'));
      http.expectNone((r) => r.url === '/api/v1/employees');

      vi.advanceTimersByTime(300);
      const req = expectList();
      expect(req.request.params.get('q')).toBe('nguyen van');
      respond(req);
    } finally {
      vi.useRealTimers();
    }
  });

  it('phân trang: nút trang sau gửi page=1, trang đầu khóa nút trang trước', async () => {
    await setUp('HR');
    const [prev, next] = Array.from(
      host.querySelectorAll<HTMLButtonElement>('.pager-buttons .btn'),
    );
    expect(prev.disabled).toBe(true);
    expect(text('.page-of')).toBe('Trang 1/3');

    next.click();
    const req = expectList();
    expect(req.request.params.get('page')).toBe('1');
    respond(req, ROWS, 45);
    expect(text('.page-of')).toBe('Trang 2/3');
    expect(text('.pager')).toContain('Hiển thị 21–22 trong 45 nhân viên');
  });

  it('lỗi tải danh sách hiện thông báo tiếng Việt', async () => {
    await setUp('HR');
    select('employee-status', 'ACTIVE');
    expectList().flush({ code: 'FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(text('.alert')).toBe('Bạn không có quyền thực hiện thao tác này.');
  });
});
