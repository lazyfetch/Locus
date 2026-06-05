import type { HeaderProps } from '../types';
import TokenBudgetIndicator from './TokenBudgetIndicator';
import ProfileMenu from './ProfileMenu';
import './Header.css';

export default function Header({ tokenUsed, tokenTotal, onToggleContext }: HeaderProps) {
  return (
    <header className="header">
      <div className="header__left">
        <span className="header__logo">Locus</span>
      </div>
      <div className="header__center">
        <TokenBudgetIndicator used={tokenUsed} total={tokenTotal} />
      </div>
      <div className="header__right">
        <button
          className="header__context-btn"
          onClick={onToggleContext}
          title="Toggle Context Inspector"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
          </svg>
          <span>Context</span>
        </button>
        <ProfileMenu />
      </div>
    </header>
  );
}
