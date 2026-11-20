import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';

import vi_ from '../../../../../public/i18n/vi.json';
import { FakeAuth } from '../../../../testing/fake-auth';
import { AuthService } from '../../../core/auth/auth.service';
import { OrgChartPage } from './org-chart-page';
import { ChartDepartment } from './org-chart.models';
import { CHART, member } from './org-chart.models.spec';

describe('OrgChartPage (ORG-03)', () => {
  let fixture: ComponentFixture<OrgChartPage>;
  let host: HTMLElement;
  let http: HttpTestingController;

  async function setUp(chart: ChartDepartment[] = CHART) {
    await TestBed.configureTestingModule({
      imports: [
        OrgChartPage,
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
        { provide: AuthService, useValue: new FakeAuth().signInAs('EMPLOYEE') },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrgChartPage);
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne('/api/v1/org-chart').flush(chart);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  function names(): string[] {
    return Array.from(host.querySelectorAll('.member-name')).map((e) => e.textContent!.trim());
  }

  function type(value: string) {
    const input = host.querySelector<HTMLInputElement>('#chart-search')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function toggleOf(departmentId: number): HTMLButtonElement {
    return host.querySelector<HTMLButtonElement>(
      `[data-department="${departmentId}"] > .unit .toggle`,
    )!;
  }

  it('hiện cây phòng ban, tên, chức danh, chữ cái đầu và số người cả nhánh', async () => {
    await setUp();

    expect(names()).toEqual([
      'Đinh Quốc Bảo',
      'Lương Thế Vinh',
      'Vũ Đình Khoa',
      'Trần Minh Anh',
      'Phạm Văn Đức',
    ]);
    const factory = host.querySelector('[data-department="1"] > .unit')!;
    expect(factory.textContent).toContain('Nhà máy');
    expect(factory.textContent).toContain('4 người');
    expect(host.querySelector('[data-member="10"] .avatar')!.textContent!.trim()).toBe('MA');
    expect(host.querySelector('[data-member="10"]')!.textContent).toContain(
      'Công nhân vận hành máy in',
    );
    expect(host.textContent).toContain('5 nhân viên đang làm việc');
  });

  it('chỉ thẻ được xem hồ sơ mới là liên kết; thẻ của chính mình có nhãn "Bạn"', async () => {
    await setUp();

    const self = host.querySelector('[data-member="11"]')!;
    expect(self.querySelector('a.member-name')!.getAttribute('href')).toBe('/employees/11');
    expect(self.textContent).toContain('Bạn');
    expect(host.querySelector('[data-member="10"] a')).toBeNull();
  });

  it('thu gọn rồi mở lại một nhánh', async () => {
    await setUp();
    const toggle = toggleOf(1);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');

    toggle.click();
    fixture.detectChanges();
    expect(toggleOf(1).getAttribute('aria-expanded')).toBe('false');
    expect(names()).toEqual(['Đinh Quốc Bảo', 'Lương Thế Vinh']);

    toggleOf(1).click();
    fixture.detectChanges();
    expect(names()).toContain('Phạm Văn Đức');
  });

  it('tìm kiếm mở nhánh đã thu gọn, tô sáng người khớp, chuyển qua lại giữa kết quả', async () => {
    await setUp();
    host.querySelector<HTMLButtonElement>('.search-nav .link-button:last-child')!.click(); // Thu gọn tất cả
    fixture.detectChanges();
    expect(names()).not.toContain('Trần Minh Anh');

    type('inh');
    expect(names()).toContain('Trần Minh Anh');
    const matched = Array.from(host.querySelectorAll('.member.match')).map((e) =>
      e.getAttribute('data-member'),
    );
    expect(matched).toEqual(['5', '1', '22', '10']);
    expect(host.querySelector('.member.current')!.getAttribute('data-member')).toBe('5');
    expect(host.querySelector('#chart-search-result')!.textContent!.trim()).toBe('Kết quả 1/4');

    host.querySelectorAll<HTMLButtonElement>('.search-nav .btn')[1].click();
    fixture.detectChanges();
    expect(host.querySelector('.member.current')!.getAttribute('data-member')).toBe('1');
    host.querySelectorAll<HTMLButtonElement>('.search-nav .btn')[0].click();
    host.querySelectorAll<HTMLButtonElement>('.search-nav .btn')[0].click();
    fixture.detectChanges();
    // Vòng lại từ đầu xuống cuối
    expect(host.querySelector('.member.current')!.getAttribute('data-member')).toBe('10');

    type('khong co ai');
    expect(host.querySelector('#chart-search-result')!.textContent!.trim()).toBe(
      'Không có nhân viên nào khớp.',
    );
  });

  it('phòng ban đông người chỉ hiện 6 người đầu, có nút Xem thêm', async () => {
    const crowd = Array.from({ length: 8 }, (_, i) => member(100 + i, `Công nhân ${i + 1}`));
    await setUp([
      {
        id: 9,
        parentId: null,
        code: 'X',
        name: 'Xưởng lớn',
        depth: 1,
        headEmployeeId: null,
        members: crowd,
      },
    ]);

    expect(names()).toHaveLength(6);
    const more = host.querySelector<HTMLButtonElement>('.more')!;
    expect(more.textContent!.trim()).toBe('Xem thêm 2 người');
    more.click();
    fixture.detectChanges();
    expect(names()).toHaveLength(8);
  });

  it('thẻ của trưởng đơn vị có nhãn "Trưởng đơn vị" và đứng đầu phòng; thẻ khác không có nhãn', async () => {
    await setUp([
      {
        id: 2,
        parentId: null,
        code: 'XI',
        name: 'Xưởng In',
        depth: 1,
        headEmployeeId: 11,
        members: [member(11, 'Phạm Văn Đức', { head: true }), member(10, 'Trần Minh Anh')],
      },
    ]);

    expect(names()).toEqual(['Phạm Văn Đức', 'Trần Minh Anh']);
    expect(host.querySelector('[data-member="11"]')!.textContent).toContain('Trưởng đơn vị');
    expect(host.querySelector('[data-member="10"]')!.textContent).not.toContain('Trưởng đơn vị');
    expect(host.querySelectorAll('.head-badge').length).toBe(1);
  });

  it('trưởng kiêm nhiệm xuất hiện ở hai phòng: chỉ thẻ ở phòng mình làm trưởng có nhãn và chỉ thẻ đó là kết quả hiện tại', async () => {
    await setUp([
      {
        id: 1,
        parentId: null,
        code: 'A',
        name: 'A',
        depth: 1,
        headEmployeeId: 7,
        members: [member(7, 'Hà Trưởng', { head: true })],
      },
      {
        id: 2,
        parentId: null,
        code: 'B',
        name: 'B',
        depth: 1,
        headEmployeeId: null,
        members: [member(7, 'Hà Trưởng')],
      },
    ]);

    expect(host.querySelectorAll('.head-badge').length).toBe(1);
    expect(host.querySelector('[data-department="1"] .head-badge')).not.toBeNull();
    type('truong');
    expect(host.querySelectorAll('.member.match').length).toBe(2);
    expect(host.querySelectorAll('.member.current').length).toBe(1);
    expect(host.querySelector('[data-department="1"] .member.current')).not.toBeNull();
  });
});
