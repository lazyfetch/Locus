import { useState, useRef, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import './ProfileMenu.css';

export default function ProfileMenu() {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener('mousedown', handleClick);
    }
    return () => document.removeEventListener('mousedown', handleClick);
  }, [open]);

  const initials = user?.username
    ? user.username.charAt(0).toUpperCase()
    : 'U';

  return (
    <div className="profile-menu" ref={menuRef}>
      <button
        className="profile-menu__trigger"
        onClick={() => setOpen(!open)}
        title="Profile menu"
      >
        <span className="profile-menu__avatar">{initials}</span>
      </button>

      {open && (
        <div className="profile-menu__dropdown">
          <div className="profile-menu__header">
            <span className="profile-menu__name">{user?.username || 'User'}</span>
            <span className="profile-menu__email">{user?.email || ''}</span>
          </div>

          <div className="profile-menu__divider" />

          <button className="profile-menu__item" onClick={toggleTheme}>
            <span className="profile-menu__item-icon">
              {theme === 'light' ? (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z" />
                </svg>
              ) : (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
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
            </span>
            <span className="profile-menu__item-text">
              {theme === 'light' ? 'Dark Mode' : 'Light Mode'}
            </span>
          </button>

          <button
            className="profile-menu__item"
            onClick={() => alert('Memories — coming soon!')}
          >
            <span className="profile-menu__item-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 01-3.46 0" />
              </svg>
            </span>
            <span className="profile-menu__item-text">Memories</span>
            <span className="profile-menu__badge">Soon</span>
          </button>

          <div className="profile-menu__divider" />

          <button className="profile-menu__item profile-menu__item--danger" onClick={logout}>
            <span className="profile-menu__item-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4" />
                <polyline points="16 17 21 12 16 7" />
                <line x1="21" y1="12" x2="9" y2="12" />
              </svg>
            </span>
            <span className="profile-menu__item-text">Logout</span>
          </button>
        </div>
      )}
    </div>
  );
}
