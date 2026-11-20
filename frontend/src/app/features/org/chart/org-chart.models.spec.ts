import {
  ChartDepartment,
  ChartMember,
  buildChart,
  countPeople,
  searchChart,
} from './org-chart.models';

export function member(
  id: number,
  fullName: string,
  extra: Partial<ChartMember> = {},
): ChartMember {
  return {
    id,
    code: `SP${String(id).padStart(4, '0')}`,
    fullName,
    positionName: 'Công nhân vận hành máy in',
    workEmail: null,
    avatarUrl: null,
    profileVisible: false,
    self: false,
    head: false,
    ...extra,
  };
}

export const CHART: ChartDepartment[] = [
  {
    id: 1,
    parentId: null,
    code: 'NM',
    name: 'Nhà máy',
    depth: 1,
    headEmployeeId: null,
    members: [member(1, 'Lương Thế Vinh')],
  },
  {
    id: 2,
    parentId: 1,
    code: 'XI',
    name: 'Xưởng In',
    depth: 2,
    headEmployeeId: null,
    members: [
      member(10, 'Trần Minh Anh'),
      member(11, 'Phạm Văn Đức', { self: true, profileVisible: true }),
    ],
  },
  {
    id: 3,
    parentId: 1,
    code: 'KHO',
    name: 'Kho vận',
    depth: 2,
    headEmployeeId: null,
    members: [member(22, 'Vũ Đình Khoa')],
  },
  {
    id: 4,
    parentId: null,
    code: 'BGD',
    name: 'Ban Giám đốc',
    depth: 1,
    headEmployeeId: null,
    members: [member(5, 'Đinh Quốc Bảo')],
  },
];

describe('buildChart', () => {
  it('dựng cây, con xếp theo tên, đếm người cả nhánh', () => {
    const roots = buildChart(CHART);

    expect(roots.map((r) => r.department.name)).toEqual(['Ban Giám đốc', 'Nhà máy']);
    const factory = roots[1];
    expect(factory.children.map((c) => c.department.name)).toEqual(['Kho vận', 'Xưởng In']);
    expect(factory.total).toBe(4);
    expect(roots[0].total).toBe(1);
  });
});

describe('searchChart', () => {
  it('không có từ khóa: null', () => {
    expect(searchChart(buildChart(CHART), '  ')).toBeNull();
  });

  it('tìm không dấu theo tên hoặc mã, mở các nhánh chứa người khớp, kết quả theo thứ tự trên sơ đồ', () => {
    const result = searchChart(buildChart(CHART), 'duc')!;
    expect(result.matches.map((m) => m.member.fullName)).toEqual(['Phạm Văn Đức']);
    expect([...result.openIds].sort()).toEqual([1, 2]);

    const byCode = searchChart(buildChart(CHART), 'sp0022')!;
    expect(byCode.matches.map((m) => m.departmentId)).toEqual([3]);

    const many = searchChart(buildChart(CHART), 'n')!;
    expect(many.matches.map((m) => m.member.id)).toEqual([5, 1, 22, 10, 11]);
  });

  it('một người xuất hiện ở hai phòng ban (trưởng kiêm nhiệm): mỗi thẻ là một kết quả, giữ thứ tự server', () => {
    const chart: ChartDepartment[] = [
      {
        id: 1,
        parentId: null,
        code: 'A',
        name: 'A',
        depth: 1,
        headEmployeeId: 7,
        members: [member(7, 'Hà Trưởng', { head: true }), member(8, 'Bình An')],
      },
      {
        id: 2,
        parentId: null,
        code: 'B',
        name: 'B',
        depth: 1,
        headEmployeeId: null,
        members: [member(9, 'Cao Nam'), member(7, 'Hà Trưởng')],
      },
    ];
    const roots = buildChart(chart);
    expect(roots[0].department.members.map((m) => m.id)).toEqual([7, 8]);
    const result = searchChart(roots, 'truong')!;
    expect(result.matches.map((m) => [m.member.id, m.departmentId])).toEqual([
      [7, 1],
      [7, 2],
    ]);
  });
});

describe('đếm người khi trưởng kiêm nhiệm có thẻ ở hai phòng ban', () => {
  const dup = (id: number, parentId: number | null, members: ChartMember[]): ChartDepartment => ({
    id,
    parentId,
    code: `D${id}`,
    name: `Phòng ${id}`,
    depth: 1,
    headEmployeeId: null,
    members,
  });

  it('cùng một nhánh: tổng nhánh tính người đó một lần', () => {
    const roots = buildChart([
      dup(1, null, [member(1, 'Giám đốc')]),
      dup(2, 1, [member(7, 'Hà Trưởng', { head: true }), member(8, 'Bình An')]),
      dup(3, 1, [member(7, 'Hà Trưởng')]),
    ]);
    expect(roots[0].total).toBe(3);
    expect(roots[0].children.map((c) => c.total)).toEqual([2, 1]);
  });

  it('tổng toàn sơ đồ (thanh công cụ) tính một lần, kể cả khác nhánh', () => {
    const roots = buildChart([
      dup(1, null, [member(7, 'Hà Trưởng', { head: true }), member(8, 'Bình An')]),
      dup(2, null, [member(7, 'Hà Trưởng'), member(9, 'Cao Nam')]),
    ]);
    expect(countPeople(roots)).toBe(3);
  });
});
