// Mirrors the Java records of the backend (io.flagship.*). If the API changes, change it here only.
export type Role = 'OWNER' | 'ADMIN' | 'EDITOR' | 'VIEWER';

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  organizationId: string;
  organizationSlug: string;
  role: Role;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EnvironmentState {
  environmentKey: string;
  environmentName: string;
  enabled: boolean;
  rolloutPercentage: number;
  version: number;
  updatedBy: string | null;
  updatedAt: string | null;
}

export interface Flag {
  id: string;
  key: string;
  name: string;
  description: string | null;
  archived: boolean;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
  environments: EnvironmentState[];
}

export interface Environment {
  id: string;
  key: string;
  name: string;
  createdAt: string;
}

export type RuleType = 'USER_LIST' | 'GROUP' | 'ATTRIBUTE';
export const RULE_TYPES: RuleType[] = ['USER_LIST', 'GROUP', 'ATTRIBUTE'];
export type RuleOperator =
  | 'EQUALS' | 'NOT_EQUALS' | 'IN' | 'NOT_IN' | 'CONTAINS'
  | 'STARTS_WITH' | 'ENDS_WITH' | 'GREATER_THAN' | 'LESS_THAN';
export const RULE_OPERATORS: RuleOperator[] = [
  'EQUALS', 'NOT_EQUALS', 'IN', 'NOT_IN', 'CONTAINS', 'STARTS_WITH', 'ENDS_WITH', 'GREATER_THAN', 'LESS_THAN',
];

export interface Rule {
  id: string;
  priority: number;
  type: RuleType;
  attribute: string | null;
  operator: RuleOperator | null;
  values: string[];
  serve: boolean;
  description: string | null;
}

export interface RuleInput {
  type: RuleType;
  attribute: string | null;
  operator: RuleOperator | null;
  values: string[];
  serve: boolean;
  description: string | null;
}

export interface RulesResponse {
  version: number;
  rules: Rule[];
}

export interface AuditLog {
  id: string;
  actorId: string | null;
  actorEmail: string | null;
  entityType: string;
  entityId: string;
  action: string;
  oldValue: Record<string, unknown> | null;
  newValue: Record<string, unknown> | null;
  createdAt: string;
}

export interface Member {
  userId: string;
  email: string;
  fullName: string;
  role: Role;
}

export interface Team {
  id: string;
  name: string;
  createdAt: string;
}

export interface ApiKey {
  id: string;
  name: string;
  prefix: string;
  createdAt: string;
  revokedAt: string | null;
}

export interface CreatedApiKey {
  id: string;
  name: string;
  prefix: string;
  key: string;
  createdAt: string;
}

export interface EvaluationResponse {
  enabled: boolean;
  reason: string;
  variant: string;
}
