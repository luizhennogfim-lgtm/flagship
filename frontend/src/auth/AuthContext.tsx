import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authApi } from '../api/endpoints';
import { clearSession, loadSession, saveSession, setUnauthorizedHandler, type Session } from '../api/client';
import type { AuthResponse } from '../api/types';

interface AuthValue {
  session: Session | null;
  login: (email: string, password: string, organizationSlug?: string) => Promise<void>;
  register: (fullName: string, email: string, password: string, organizationName: string) => Promise<void>;
  logout: () => void;
  canEdit: boolean;
  canAdmin: boolean;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<Session | null>(() => loadSession());

  const start = useCallback((email: string, res: AuthResponse) => {
    const s: Session = { token: res.accessToken, email, organizationSlug: res.organizationSlug, role: res.role };
    saveSession(s);
    setSession(s);
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(logout); // an expired token sends the user back to the login screen
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  const value = useMemo<AuthValue>(() => ({
    session,
    login: async (email, password, organizationSlug) => {
      const res = await authApi.login({ email, password, organizationSlug: organizationSlug || undefined });
      start(email, res);
    },
    register: async (fullName, email, password, organizationName) => {
      const res = await authApi.register({ fullName, email, password, organizationName });
      start(email, res);
    },
    logout,
    canEdit: session?.role === 'OWNER' || session?.role === 'ADMIN' || session?.role === 'EDITOR',
    canAdmin: session?.role === 'OWNER' || session?.role === 'ADMIN',
  }), [session, start, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
