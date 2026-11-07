import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';

import vi_ from '../../../../../public/i18n/vi.json';
import { Page } from '../rbac.models';
import { AuditLogEntry } from './audit.models';
import { AuditPage } from './audit-page';

const UPDATE: AuditLogEntry = {
  id: 42,
  createdAt: '2026-11-07T10:30:00+07:00',
  userId: 1,
  userEmail: 'admin@sspack.vn',
  action: 'UPDATE',
  entityType: 'roles',
  entityId: 9,
  oldValue: { code: 'TRUONG_CA', name: 'Trưởng ca', permissions: ['leave:read', 'leave:approve'] },
  newValue: {
    code: 'TRUONG_CA',
    name: 'Trưởng ca sản xuất',
    permissions: ['leave:read', 'attendance:confirm'],
  },
  ipAddress: '10.0.0.5',
};

function page(content: AuditLogEntry[], totalPages = 1): Page<AuditLogEntry> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages };
}

describe('AuditPage (PLT-04)', () => {
  let fixture: ComponentFixture<AuditPage>;
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
        AuditPage,
        TranslocoTestingModule.forRoot({
          langs: { vi: vi_ },
          translocoConfig: { availableLangs: ['vi', 'en'], defaultLang: 'vi' },
          preloadLangs: true,
        }),
      ],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AuditPage);
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/audit-logs').flush(page([UPDATE]));
    http.expectOne('/api/v1/audit-logs/actors').flush([{ id: 1, email: 'admin@sspack.vn' }]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('mỗi dòng cho biết ai, lúc nào (giờ Việt Nam), hành động, bảng nào', () => {
    const cells = Array.from(host.querySelectorAll('tbody tr td')).map((td) =>
      td.textContent?.trim(),
    );

    expect(cells[0]).toBe('07/11/2026 10:30:00');
    expect(cells[1]).toBe('admin@sspack.vn');
    expect(cells[2]).toBe('Sửa');
    expect(cells[3]).toContain('Vai trò');
    expect(cells[3]).toContain('#9');
    expect(cells[4]).toBe('10.0.0.5');
  });

  it('lọc: gửi các điều kiện đã chọn, bỏ qua ô trống', () => {
    const set = (selector: string, value: string, event = 'change') => {
      const el = host.querySelector<HTMLInputElement | HTMLSelectElement>(selector)!;
      el.value = value;
      el.dispatchEvent(new Event(event));
    };
    set('#audit-from', '2026-11-01', 'input');
    set('#audit-entity', 'roles');
    set('#audit-action', 'DELETE');
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('.filters button[type="submit"]')!.click();

    const req = http.expectOne((r) => r.url === '/api/v1/audit-logs');
    expect(req.request.params.get('from')).toBe('2026-11-01');
    expect(req.request.params.get('entityType')).toBe('roles');
    expect(req.request.params.get('action')).toBe('DELETE');
    expect(req.request.params.has('to')).toBe(false);
    expect(req.request.params.has('userId')).toBe(false);
    req.flush(page([]));
    fixture.detectChanges();
    expect(host.textContent).toContain('Không có dòng nhật ký nào khớp bộ lọc.');
  });

  it('xem chi tiết: so sánh trước/sau, đánh dấu trường đổi, quyền được thêm và bị gỡ', () => {
    host.querySelector<HTMLButtonElement>('tbody .link-button')!.click();
    fixture.detectChanges();

    const rows = Array.from(host.querySelectorAll('dialog .diff tbody tr'));
    expect(rows.map((r) => r.querySelector('code')?.textContent)).toEqual([
      'code',
      'name',
      'permissions',
    ]);
    expect(rows.map((r) => r.classList.contains('changed'))).toEqual([false, true, true]);
    expect(rows[1].textContent).toContain('Trưởng ca sản xuất');
    expect(rows[2].querySelector('.added')?.textContent).toContain('attendance:confirm');
    expect(rows[2].querySelector('.removed')?.textContent).toContain('leave:approve');
  });
});
