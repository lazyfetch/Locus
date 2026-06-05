import { useState } from 'react';
import type { MessageProps } from '../types';
import './Message.css';

export default function Message({ message }: MessageProps) {
  const { role, content, metrics, sources } = message;
  const [sourcesOpen, setSourcesOpen] = useState(false);
  const isUser = role === 'user';

  return (
    <div className={`message ${isUser ? 'message--user' : 'message--assistant'}`}>
      <div className="message__avatar">
        {isUser ? (
          <span className="message__avatar-icon">U</span>
        ) : (
          <span className="message__avatar-icon message__avatar-icon--ai">L</span>
        )}
      </div>

      <div className="message__body">
        <div className="message__bubble">
          <div className="message__content">{renderContent(content)}</div>

          {metrics && metrics.length > 0 && (
            <div className="message__metrics">
              <table className="metrics-table">
                <thead>
                  <tr>
                    <th>Metric</th>
                    <th>Value</th>
                    <th>Period</th>
                  </tr>
                </thead>
                <tbody>
                  {metrics.map((m, i) => (
                    <tr key={i}>
                      <td>{m.label}</td>
                      <td className="metrics-table__value">{m.value}</td>
                      <td className="metrics-table__date">{m.date}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {sources && sources.length > 0 && (
            <div className="message__sources">
              <button
                className="message__sources-toggle"
                onClick={() => setSourcesOpen(!sourcesOpen)}
              >
                <span className={`message__sources-arrow ${sourcesOpen ? 'message__sources-arrow--open' : ''}`}>
                  &#9654;
                </span>
                Sources ({sources.length})
              </button>
              {sourcesOpen && (
                <div className="message__sources-list">
                  {sources.map((s, i) => (
                    <div key={i} className="message__source-item">
                      <div className="message__source-title">{s.title}</div>
                      <div className="message__source-snippet">{s.snippet}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function renderContent(text: string | null): JSX.Element | null {
  if (!text) return null;

  const html = text
    .replace(/^### (.*$)/gm, '<h3>$1</h3>')
    .replace(/^## (.*$)/gm, '<h2>$1</h2>')
    .replace(/^> (.*$)/gm, '<blockquote>$1</blockquote>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/^---$/gm, '<hr />')
    .replace(/\n/g, '<br />');

  return <span dangerouslySetInnerHTML={{ __html: html }} />;
}
