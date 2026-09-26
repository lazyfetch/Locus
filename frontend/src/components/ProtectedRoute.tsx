import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import type { ProtectedRouteProps } from '../types';

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div className="auth-loading">Restoring session...</div>;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
