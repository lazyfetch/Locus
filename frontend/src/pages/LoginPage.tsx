import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import './LoginPage.css';

export default function LoginPage() {
  const { login, signup } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();

  const [isLogin, setIsLogin] = useState(true);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    let success: boolean;
    if (isLogin) {
      success = login(email, password);
    } else {
      success = signup(name, email, password);
    }

    if (success) {
      navigate('/chat', { replace: true });
    } else {
      setError('Please fill in all fields.');
    }
  };

  const toggleMode = () => {
    setIsLogin(!isLogin);
    setError('');
  };

  return (
    <div className="login-page">
      <button className="login-page__theme-toggle" onClick={toggleTheme} title="Toggle theme">
        {theme === 'light' ? (
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z" />
          </svg>
        ) : (
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="5" />
            <line x1="12" y1="1" x2="12" y2="3" />
            <line x1="12" y1="21" x2="12" y2="23" />
            <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
            <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
            <line x1="1" y1="12" x2="3" y2="12" />
            <line x1="21" y1="12" x2="23" y2="12" />
            <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
            <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
          </svg>
        )}
      </button>

      <div className="login-page__card">
        <div className="login-page__logo">Locus</div>
        <p className="login-page__tagline">Financial Intelligence Assistant</p>

        <form className="login-page__form" onSubmit={handleSubmit}>
          {!isLogin && (
            <div className="login-page__field">
              <label htmlFor="name" className="login-page__label">Name</label>
              <input
                id="name"
                type="text"
                className="login-page__input"
                placeholder="Your name"
                value={name}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setName(e.target.value)}
                autoComplete="name"
              />
            </div>
          )}

          <div className="login-page__field">
            <label htmlFor="email" className="login-page__label">Email</label>
            <input
              id="email"
              type="email"
              className="login-page__input"
              placeholder="you@example.com"
              value={email}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setEmail(e.target.value)}
              autoComplete="email"
            />
          </div>

          <div className="login-page__field">
            <label htmlFor="password" className="login-page__label">Password</label>
            <input
              id="password"
              type="password"
              className="login-page__input"
              placeholder="Enter your password"
              value={password}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setPassword(e.target.value)}
              autoComplete={isLogin ? 'current-password' : 'new-password'}
            />
          </div>

          {error && <p className="login-page__error">{error}</p>}

          <button type="submit" className="login-page__submit">
            {isLogin ? 'Sign In' : 'Create Account'}
          </button>
        </form>

        <p className="login-page__toggle">
          {isLogin ? (
            <>Don't have an account? <button className="login-page__link" onClick={toggleMode}>Sign up</button></>
          ) : (
            <>Already have an account? <button className="login-page__link" onClick={toggleMode}>Sign in</button></>
          )}
        </p>
      </div>
    </div>
  );
}
