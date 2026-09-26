import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import type { UserProfile, AuthContextType } from '../types';

const AuthContext = createContext<AuthContextType | null>(null);

const ACCESS_TOKEN_KEY = 'locus-access-token';

type AuthResponse = { accessToken: string; user: { id: string; name: string; email: string } };

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(path, { ...init, credentials: 'include', headers: { 'Content-Type': 'application/json', ...(init.headers || {}) } });
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  const applyAuth = useCallback((data: AuthResponse) => {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken);
    setUser({ email: data.user.email, username: data.user.name });
  }, []);

  const refresh = useCallback(async (): Promise<boolean> => {
    const response = await request('/api/auth/refresh', { method: 'POST' });
    if (!response.ok) {
      sessionStorage.removeItem(ACCESS_TOKEN_KEY);
      setUser(null);
      return false;
    }
    applyAuth(await response.json() as AuthResponse);
    return true;
  }, [applyAuth]);

  useEffect(() => {
    refresh().finally(() => setLoading(false));
  }, [refresh]);

  const login = useCallback(async (email: string, password: string) => {
    const response = await request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    if (!response.ok) throw new Error((await response.json()).error || 'Unable to sign in');
    applyAuth(await response.json() as AuthResponse);
  }, [applyAuth]);

  const signup = useCallback(async (name: string, email: string, password: string) => {
    const response = await request('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ name, email, password }),
    });
    if (!response.ok) throw new Error((await response.json()).error || 'Unable to create account');
    applyAuth(await response.json() as AuthResponse);
  }, [applyAuth]);

  const logout = useCallback(async () => {
    await request('/api/auth/logout', { method: 'POST' });
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    setUser(null);
  }, []);

  const authorizedFetch = useCallback(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const headers = new Headers(init.headers);
    const token = sessionStorage.getItem(ACCESS_TOKEN_KEY);
    if (token) headers.set('Authorization', `Bearer ${token}`);
    headers.set('Content-Type', 'application/json');
    let response = await fetch(input, { ...init, headers, credentials: 'include' });
    if (response.status === 401 && await refresh()) {
      const retryHeaders = new Headers(init.headers);
      const refreshedToken = sessionStorage.getItem(ACCESS_TOKEN_KEY);
      if (refreshedToken) retryHeaders.set('Authorization', `Bearer ${refreshedToken}`);
      retryHeaders.set('Content-Type', 'application/json');
      response = await fetch(input, { ...init, headers: retryHeaders, credentials: 'include' });
    }
    return response;
  }, [refresh]);

  return (
    <AuthContext.Provider value={{ user, loading, login, signup, logout, authorizedFetch }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
