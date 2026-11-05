import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Trang tạm cho phân hệ chưa làm. Khóa tiêu đề lấy từ `data.titleKey` của route
 * (nhờ withComponentInputBinding).
 */
@Component({
  selector: 'hrm-module-placeholder',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="display">{{ titleKey() | transloco }}</h1>
    <section class="card">
      <p class="subheading">{{ 'placeholder.message' | transloco }}</p>
      <p class="hint">{{ 'placeholder.hint' | transloco }}</p>
    </section>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--space-6);
    }
    .card {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      padding: var(--space-4);
      background: var(--surface-raised);
      border: 1px solid var(--line);
      border-radius: var(--radius-md);
    }
    .hint {
      color: var(--ink-muted);
    }
  `,
})
export class ModulePlaceholder {
  readonly titleKey = input.required<string>();
}
