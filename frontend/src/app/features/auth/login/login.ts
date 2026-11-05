import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  isDevMode,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { safeReturnUrl } from '../../../core/auth/auth.guards';
import { ApiProblem } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';

interface DemoAccount {
  readonly roleKey: string;
  readonly email: string;
}

/** Tài khoản demo có trong seed profile dev (db/dev/R__demo_users.sql). */
const DEMO_ACCOUNTS: readonly DemoAccount[] = [
  { roleKey: 'auth.demoRoles.admin', email: 'admin@sspack.vn' },
  { roleKey: 'auth.demoRoles.hr', email: 'thuha.le@sspack.vn' },
  { roleKey: 'auth.demoRoles.manager', email: 'minhanh.tran@sspack.vn' },
  { roleKey: 'auth.demoRoles.employee', email: 'vanduc.pham@sspack.vn' },
];

/** SC-01 / PLT-01: đăng nhập bằng email công ty, theo mockup artboard "Main". */
@Component({
  selector: 'hrm-login',
  imports: [ReactiveFormsModule, TranslocoPipe],
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);

  /** Trang đích sau khi đăng nhập (query param, gắn qua withComponentInputBinding). */
  readonly returnUrl = input<string>();

  protected readonly demoAccounts = isDevMode() ? DEMO_ACCOUNTS : [];
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly showForgotNote = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
    rememberMe: [false],
  });

  protected fieldError(name: 'email' | 'password'): string | null {
    const control = this.form.controls[name];
    if (!control.invalid || !(control.touched || control.dirty)) {
      return null;
    }
    if (control.hasError('required')) {
      return name === 'email'
        ? 'auth.validation.emailRequired'
        : 'auth.validation.passwordRequired';
    }
    return control.hasError('email') ? 'auth.validation.emailInvalid' : null;
  }

  protected useDemoAccount(email: string): void {
    this.form.patchValue({ email });
    this.errorMessage.set(null);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password, rememberMe } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.auth.login(email.trim(), password, rememberMe).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigateByUrl(safeReturnUrl(this.returnUrl()));
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.form.controls.password.reset();
        this.errorMessage.set(this.describeError(err));
      },
    });
  }

  private describeError(err: unknown): string {
    if (!(err instanceof HttpErrorResponse)) {
      return this.transloco.translate('auth.errors.generic');
    }
    if (err.status === 0) {
      return this.transloco.translate('auth.errors.network');
    }
    const problem = err.error as ApiProblem | null;
    switch (problem?.code) {
      case 'AUTH_INVALID_CREDENTIALS':
      case 'AUTH_ACCOUNT_DISABLED':
        return this.transloco.translate(`auth.errors.${problem.code}`);
      case 'AUTH_ACCOUNT_LOCKED': {
        const minutes = Math.max(1, Math.ceil((problem.retryAfterSeconds ?? 60) / 60));
        return this.transloco.translate('auth.errors.AUTH_ACCOUNT_LOCKED', { minutes });
      }
      default:
        return problem?.detail ?? this.transloco.translate('auth.errors.generic');
    }
  }
}
