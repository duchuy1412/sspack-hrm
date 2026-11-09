import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { vi } from 'vitest';

import vi_ from '../../../../../public/i18n/vi.json';
import { FakeAuth } from '../../../../testing/fake-auth';
import { AuthService } from '../../../core/auth/auth.service';
import { JobGrade } from '../org-catalog.api';
import { JobGradesPage } from './job-grades-page';

function grade(id: number, code: string, min: number, max: number, extra: Partial<JobGrade> = {}): JobGrade {
  return {
    id,
    code,
    name: `Cấp ${code}`,
    minSalary: min,
    maxSalary: max,
    active: true,
    positionCount: 0,
    activePositionCount: 0,
    version: 1,
    updatedAt: '2026-11-09T09:00:00+07:00',
    ...extra,
  };
}

const LIST = [
  grade(1, 'G1', 6000000, 9000000, { positionCount: 2, activePositionCount: 2 }),
  grade(2, 'G5', 25000000, 25000000),
];

describe('JobGradesPage (ORG-02)', () => {
  let fixture: ComponentFixture<JobGradesPage>;
  let host: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
    await TestBed.configureTestingModule({
      imports: [
        JobGradesPage,
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
    fixture = TestBed.createComponent(JobGradesPage);
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    flushList();
  });

  afterEach(() => http.verify());

  function flushList(list = LIST) {
    http.expectOne((r) => r.url === '/api/v1/job-grades').flush(list);
    fixture.detectChanges();
  }

  function row(id: number): HTMLElement {
    return host.querySelector<HTMLElement>(`tr[data-id="${id}"]`)!;
  }

  function buttons(id: number): string[] {
    return Array.from(row(id).querySelectorAll('button')).map((b) =>
      b.childNodes[0].textContent!.trim(),
    );
  }

  function type(id: string, value: string) {
    const input = host.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  it('hiện khung lương theo định dạng 18.450.000 ₫; khung một mức chỉ hiện một số', () => {
    expect(row(1).querySelector('.range')!.textContent!.trim()).toBe('6.000.000 ₫ – 9.000.000 ₫');
    expect(row(2).querySelector('.range')!.textContent!.trim()).toBe('25.000.000 ₫');
  });

  it('cấp bậc còn chức danh không có nút Xóa', () => {
    expect(buttons(1)).toEqual(['Sửa', 'Ngừng hoạt động']);
    expect(buttons(2)).toEqual(['Sửa', 'Ngừng hoạt động', 'Xóa']);
  });

  it('thêm cấp bậc: đọc số có dấu chấm, gửi số đồng và mã in hoa', () => {
    host.querySelector<HTMLButtonElement>('.btn-primary')!.click();
    fixture.detectChanges();
    type('grade-code', 'g3');
    type('grade-name', 'Chuyên viên');
    type('grade-min', '12.000.000');
    type('grade-max', '20000000');
    fixture.detectChanges();

    expect(host.querySelector<HTMLInputElement>('#grade-max')!.value).toBe('20.000.000');
    expect(host.querySelector('dialog')!.textContent).toContain(
      'Khung lương: 12.000.000 ₫ – 20.000.000 ₫',
    );

    host.querySelector<HTMLFormElement>('dialog form')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/job-grades');
    expect(req.request.body).toEqual({
      code: 'G3',
      name: 'Chuyên viên',
      minSalary: 12000000,
      maxSalary: 20000000,
    });
    req.flush(grade(3, 'G3', 12000000, 20000000, { name: 'Chuyên viên' }));
    flushList();

    expect(host.querySelector('.notice')!.textContent).toContain('Đã thêm cấp bậc Chuyên viên');
  });

  it('khung ngược hoặc số lẻ: báo lỗi trên form, không gọi API', () => {
    host.querySelector<HTMLButtonElement>('.btn-primary')!.click();
    fixture.detectChanges();
    type('grade-code', 'G9');
    type('grade-name', 'Thử');
    type('grade-min', '20.000.000');
    type('grade-max', '10.000.000');
    host.querySelector<HTMLFormElement>('dialog form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(host.querySelector('dialog')!.textContent).toContain(
      'Lương tối đa phải lớn hơn hoặc bằng lương tối thiểu',
    );

    type('grade-max', '25000000,5');
    fixture.detectChanges();
    expect(host.querySelector('dialog')!.textContent).toContain(
      'Nhập số đồng nguyên lớn hơn 0, tối đa 15 chữ số',
    );
    http.expectNone((r) => r.method === 'POST');
  });

  it('sửa: điền sẵn số có dấu chấm và gửi kèm version', () => {
    row(1).querySelector<HTMLButtonElement>('button')!.click();
    fixture.detectChanges();
    expect(host.querySelector<HTMLInputElement>('#grade-min')!.value).toBe('6.000.000');

    type('grade-max', '9.500.000');
    host.querySelector<HTMLFormElement>('dialog form')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/v1/job-grades/1');
    expect(req.request.body).toEqual({
      code: 'G1',
      name: 'Cấp G1',
      minSalary: 6000000,
      maxSalary: 9500000,
      version: 1,
    });
    req.flush(grade(1, 'G1', 6000000, 9500000));
    flushList();
  });

  it('lỗi nghiệp vụ từ server hiện bằng tiếng Việt kèm số liệu', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    row(1).querySelectorAll<HTMLButtonElement>('button')[1].click();
    http
      .expectOne('/api/v1/job-grades/1/status')
      .flush(
        { code: 'JOB_GRADE_HAS_ACTIVE_POSITIONS', positionCount: 2 },
        { status: 409, statusText: 'Conflict' },
      );
    flushList();

    expect(host.querySelector('.alert')!.textContent).toContain(
      'Cấp bậc còn 2 chức danh đang hoạt động',
    );
  });
});
