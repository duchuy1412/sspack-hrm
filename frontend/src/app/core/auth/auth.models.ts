export type DataScope = 'ALL' | 'DEPARTMENT_TREE' | 'DEPARTMENT' | 'SELF' | 'ASSIGNED';

export interface AccessTokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Số giây đến khi access token hết hạn. */
  expiresIn: number;
}

export interface CurrentUserRole {
  code: string;
  name: string;
  dataScope: DataScope;
}

export interface CurrentUser {
  id: number;
  email: string;
  preferredLocale: string;
  roles: CurrentUserRole[];
  permissions: string[];
}

/** Lỗi RFC 7807 từ backend; `code` ổn định để ánh xạ sang chuỗi i18n. */
export interface ApiProblem {
  status: number;
  code?: string;
  detail?: string;
  retryAfterSeconds?: number;
  errors?: Record<string, string>;
}

export const AUTH_API = '/api/v1/auth';
