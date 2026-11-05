import { Routes } from '@angular/router';

/** Route của phân hệ "reports", được lazy-load từ app.routes.ts. Thêm trang thật khi làm user story tương ứng. */
export default [
  {
    path: '',
    loadComponent: () =>
      import('../../shared/ui/module-placeholder/module-placeholder').then((m) => m.ModulePlaceholder),
    data: { titleKey: 'nav.reports' },
  },
] satisfies Routes;
