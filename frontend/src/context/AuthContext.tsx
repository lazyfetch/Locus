import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import type { UserProfile, AuthContextType } from '../types';

const AuthContext = createContext<AuthContextType | null>(null);

const STORAGE_KEY = 'locus-user';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(() => {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored ? JSON.parse(stored) as UserProfile : null;
  });

  const login = useCallback((email: string, password: string): boolean => {
    if (!email || !password) return false;
    const userData: UserProfile = { email, username: email.split('@')[0] };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(userData));
    setUser(userData);
    return true;
  }, []);

  const signup = useCallback((name: string, email: string, password: string): boolean => {
    if (!name || !email || !password) return false;
    const userData: UserProfile = { email, username: name };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(userData));
    setUser(userData);
    return true;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, signup, logout }}>
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
