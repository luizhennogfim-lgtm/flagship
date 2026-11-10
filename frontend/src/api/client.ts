import type { Role } from './types';

export interface Session {
  token: string;
  email: string;
  organizationSlug: string;
  role: Role;
}

// The token lives in localStorage so a refresh keeps the user logged in.
// Trade-off documented in the README: localStorage is readable by any script on the page (XSS).
const KEY = 'flagship.session';

export function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function saveSession(session: Session): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    /* storage unavailable: the user simply has to log in again after a refresh */
  }
}

export function clearSession(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public title: string,
    message: string,
    public fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

let unauthorizedHandler: (() => void) | null = null;
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

interface Options {
  method?: string;
  body?: unknown;
  auth?: boolean;
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export async function api<T>(path: string, { method = 'GET', body, auth = true }: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const session = loadSession();
  if (auth && session) headers.Authorization = `Bearer ${session.token}`;

  let res: Response;
  try {
    res = await fetch(`/api/v1${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Network error', 'Cannot reach the Flagship API. Is the backend running?');
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? safeParse(text) : undefined;

  if (!res.ok) {
    if (res.status === 401 && auth) unauthorizedHandler?.();
    const problem = (data ?? {}) as { title?: string; detail?: string; errors?: Record<string, string> };
    let message = problem.detail ?? problem.title ?? `Request failed (${res.status})`;
    if (res.status === 429) {
      const retry = res.headers.get('Retry-After');
      message = `Too many requests.${retry ? ` Try again in ${retry}s.` : ''}`;
    }
    throw new ApiError(res.status, problem.title ?? 'Error', message, problem.errors ?? {});
  }
  return data as T;
}
