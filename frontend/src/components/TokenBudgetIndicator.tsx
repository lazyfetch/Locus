import type { TokenBudgetIndicatorProps } from '../types';
import './TokenBudgetIndicator.css';

export default function TokenBudgetIndicator({ used = 0, total = 6000 }: TokenBudgetIndicatorProps) {
  const percentage = Math.min((used / total) * 100, 100);
  const isHigh = percentage > 80;

  return (
    <div className="token-budget">
      <span className="token-budget__label">
        Context: <strong>{used.toLocaleString()}</strong> / {total.toLocaleString()} tokens
      </span>
      <div className="token-budget__bar">
        <div
          className={`token-budget__fill ${isHigh ? 'token-budget__fill--high' : ''}`}
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}
