import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

/** Trang báo không có quyền khi mở thẳng URL của phân hệ không được phép (PLT-03). */
@Component({
  selector: 'hrm-forbidden',
  imports: [RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel" role="alert">
      <h1 class="heading">{{ 'forbidden.title' | transloco }}</h1>
      <p>{{ 'forbidden.message' | transloco }}</p>
      <a routerLink="/" class="back">{{ 'forbidden.back' | transloco }}</a>
    </section>
  `,
  styles: `
    .panel {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: var(--space-4);
      max-width: 36rem;
      padding: var(--space-6);
      background: var(--surface-raised);
      border: 1px solid var(--line);
      border-radius: var(--radius-md);
    }
    p {
      color: var(--ink-muted);
    }
    .back {
      font: var(--text-label);
    }
  `,
})
export class ForbiddenPage {}
