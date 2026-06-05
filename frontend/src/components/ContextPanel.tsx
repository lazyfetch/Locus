import type { ContextPanelProps, Message } from '../types';
import './ContextPanel.css';

export default function ContextPanel({
  open,
  onClose,
  messages,
  tokenUsed,
  tokenTotal,
}: ContextPanelProps) {
  if (!open) return null;

  const lastAssistant = [...messages].reverse().find(
    (m): m is Message & { sources: NonNullable<Message['sources']> } =>
      m.role === 'assistant' && m.sources !== null && m.sources.length > 0
  );
  const sources = lastAssistant?.sources || [];

  const entities = extractEntities(messages);

  const historyTokens = Math.min(Math.round(tokenUsed * 0.25), tokenUsed);
  const dataTokens = Math.min(Math.round(tokenUsed * 0.45), tokenUsed - historyTokens);
  const docTokens = tokenUsed - historyTokens - dataTokens;
  const remaining = Math.max(tokenTotal - tokenUsed, 0);

  const userMessages = messages.filter((m) => m.role === 'user').length;
  const assistantMessages = messages.filter((m) => m.role === 'assistant').length;

  return (
    <div className="context-panel-overlay" onClick={onClose}>
      <div className="context-panel" onClick={(e: React.MouseEvent) => e.stopPropagation()}>
        <div className="context-panel__header">
          <h2 className="context-panel__title">Context Inspector</h2>
          <button className="context-panel__close" onClick={onClose}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <section className="context-panel__section">
          <h3 className="context-panel__section-title">Token Budget Allocation</h3>
          <div className="context-panel__budget-bar">
            <div className="context-panel__budget-segment" style={{ flex: historyTokens, background: 'var(--color-accent)' }} title="Conversation History" />
            <div className="context-panel__budget-segment" style={{ flex: dataTokens, background: 'var(--color-success)' }} title="Structured Data" />
            <div className="context-panel__budget-segment" style={{ flex: docTokens, background: 'var(--color-warning)' }} title="Retrieved Documents" />
          </div>
          <div className="context-panel__budget-legend">
            <span className="context-panel__legend-item">
              <span className="context-panel__legend-dot" style={{ background: 'var(--color-accent)' }} />
              History
            </span>
            <span className="context-panel__legend-item">
              <span className="context-panel__legend-dot" style={{ background: 'var(--color-success)' }} />
              Data
            </span>
            <span className="context-panel__legend-item">
              <span className="context-panel__legend-dot" style={{ background: 'var(--color-warning)' }} />
              Docs
            </span>
          </div>
          <div className="context-panel__budget-numbers">
            <span>Used: <strong>{tokenUsed.toLocaleString()}</strong></span>
            <span>Remaining: <strong>{remaining.toLocaleString()}</strong></span>
            <span>Total: <strong>{tokenTotal.toLocaleString()}</strong></span>
          </div>
        </section>

        <section className="context-panel__section">
          <h3 className="context-panel__section-title">
            Active Sources
            {sources.length > 0 && <span className="context-panel__badge">{sources.length}</span>}
          </h3>
          {sources.length === 0 ? (
            <p className="context-panel__empty">No sources cited yet. Ask a financial question to see sources.</p>
          ) : (
            <div className="context-panel__sources-list">
              {sources.map((s, i) => (
                <div key={i} className="context-panel__source-item">
                  <div className="context-panel__source-title">{s.title}</div>
                  <div className="context-panel__source-snippet">{s.snippet}</div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="context-panel__section">
          <h3 className="context-panel__section-title">
            Key Entities
            {entities.length > 0 && <span className="context-panel__badge">{entities.length}</span>}
          </h3>
          {entities.length === 0 ? (
            <p className="context-panel__empty">No entities detected yet.</p>
          ) : (
            <div className="context-panel__entities">
              {entities.map((e, i) => (
                <span key={i} className="context-panel__entity-tag">{e}</span>
              ))}
            </div>
          )}
        </section>

        <section className="context-panel__section">
          <h3 className="context-panel__section-title">Conversation Stats</h3>
          <div className="context-panel__stats">
            <div className="context-panel__stat">
              <span className="context-panel__stat-value">{userMessages + assistantMessages}</span>
              <span className="context-panel__stat-label">Total Messages</span>
            </div>
            <div className="context-panel__stat">
              <span className="context-panel__stat-value">{userMessages}</span>
              <span className="context-panel__stat-label">User</span>
            </div>
            <div className="context-panel__stat">
              <span className="context-panel__stat-value">{assistantMessages}</span>
              <span className="context-panel__stat-label">Assistant</span>
            </div>
            <div className="context-panel__stat">
              <span className="context-panel__stat-value">{Math.round(tokenUsed / tokenTotal * 100)}%</span>
              <span className="context-panel__stat-label">Budget Used</span>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}

function extractEntities(messages: Message[]): string[] {
  const entities = new Set<string>();

  const companyNames = ['tesla', 'nvidia', 'apple', 'microsoft', 'google', 'amazon', 'meta', 'netflix'];
  const financialTerms = ['p/e', 'pe ratio', 'revenue', 'earnings', 'gross margin', 'net income', 'eps'];

  const allText = messages
    .map((m) => m.content?.toLowerCase() || '')
    .join(' ');

  if (allText.includes('tsla')) entities.add('TSLA');
  if (allText.includes('nvda')) entities.add('NVDA');
  if (allText.includes('aapl')) entities.add('AAPL');
  if (allText.includes('msft')) entities.add('MSFT');
  if (allText.includes('googl')) entities.add('GOOGL');
  if (allText.includes('amzn')) entities.add('AMZN');

  companyNames.forEach((name) => {
    if (allText.includes(name)) {
      entities.add(name.charAt(0).toUpperCase() + name.slice(1));
    }
  });

  financialTerms.forEach((term) => {
    if (allText.includes(term)) {
      entities.add(term === 'pe ratio' || term === 'p/e' ? 'P/E Ratio' : term.charAt(0).toUpperCase() + term.slice(1));
    }
  });

  return Array.from(entities);
}
