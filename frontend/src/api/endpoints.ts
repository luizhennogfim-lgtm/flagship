import { api } from './client';
import type {
  ApiKey, AuditLog, AuthResponse, CreatedApiKey, Environment, EnvironmentState, EvaluationResponse,
  Flag, Member, PageResponse, Role, RuleInput, RulesResponse, Team,
} from './types';

type Params = Record<string, string | number | boolean | undefined | null>;

function qs(params: Params): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : '';
}

const enc = encodeURIComponent;

export const authApi = {
  login: (body: { email: string; password: string; organizationSlug?: string }) =>
    api<AuthResponse>('/auth/login', { method: 'POST', body, auth: false }),
  register: (body: { fullName: string; email: string; password: string; organizationName: string }) =>
    api<AuthResponse>('/auth/register', { method: 'POST', body, auth: false }),
};

export const flagsApi = {
  list: (p: { search?: string; environment?: string; enabled?: boolean; archived?: boolean; page?: number; size?: number }) =>
    api<PageResponse<Flag>>(`/flags${qs(p)}`),
  get: (id: string) => api<Flag>(`/flags/${id}`),
  create: (body: { key: string; name: string; description?: string }) =>
    api<Flag>('/flags', { method: 'POST', body }),
  update: (id: string, body: { name: string; description?: string }) =>
    api<Flag>(`/flags/${id}`, { method: 'PUT', body }),
  archive: (id: string) => api<void>(`/flags/${id}`, { method: 'DELETE' }),
  updateEnvironment: (id: string, envKey: string, body: { enabled: boolean; rolloutPercentage: number; version: number }) =>
    api<EnvironmentState>(`/flags/${id}/environments/${enc(envKey)}`, { method: 'PUT', body }),
  getRules: (id: string, envKey: string) =>
    api<RulesResponse>(`/flags/${id}/environments/${enc(envKey)}/rules`),
  replaceRules: (id: string, envKey: string, body: { version: number; rules: RuleInput[] }) =>
    api<RulesResponse>(`/flags/${id}/environments/${enc(envKey)}/rules`, { method: 'PUT', body }),
};

export const envApi = {
  list: () => api<Environment[]>('/environments'),
  create: (body: { key: string; name: string }) => api<Environment>('/environments', { method: 'POST', body }),
};

export const auditApi = {
  list: (p: { entityId?: string; page?: number; size?: number }) => api<PageResponse<AuditLog>>(`/audit-logs${qs(p)}`),
};

export const membersApi = {
  list: (page: number) => api<PageResponse<Member>>(`/members${qs({ page, size: 20 })}`),
  add: (body: { fullName: string; email: string; password: string; role: Role }) =>
    api<Member>('/members', { method: 'POST', body }),
};

export const teamsApi = {
  list: (page: number) => api<PageResponse<Team>>(`/teams${qs({ page, size: 20 })}`),
  create: (body: { name: string }) => api<Team>('/teams', { method: 'POST', body }),
};

export const apiKeysApi = {
  list: () => api<ApiKey[]>('/api-keys'),
  create: (body: { name: string }) => api<CreatedApiKey>('/api-keys', { method: 'POST', body }),
  revoke: (id: string) => api<void>(`/api-keys/${id}`, { method: 'DELETE' }),
};

export const evaluateApi = {
  evaluate: (body: {
    flagKey: string; userId: string; environment: string; groups?: string[]; attributes?: Record<string, string>;
  }) => api<EvaluationResponse>('/evaluate', { method: 'POST', body }),
};
