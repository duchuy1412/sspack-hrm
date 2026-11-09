import { ChartDepartment, ChartMember, buildChart, searchChart } from './org-chart.models';

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
    members: [member(1, 'Lương Thế Vinh')],
  },
  {
    id: 2,
    parentId: 1,
    code: 'XI',
    name: 'Xưởng In',
    depth: 2,
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
    members: [member(22, 'Vũ Đình Khoa')],
  },
  {
    id: 4,
    parentId: null,
    code: 'BGD',
    name: 'Ban Giám đốc',
    depth: 1,
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
});
