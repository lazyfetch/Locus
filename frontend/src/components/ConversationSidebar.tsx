import { useState } from 'react';
import type { ConversationSidebarProps } from '../types';
import './ConversationSidebar.css';

export default function ConversationSidebar({
  conversations,
  activeId,
  onSelect,
  onNew,
  onDelete,
  collapsed,
  onToggleCollapse,
}: ConversationSidebarProps) {
  const [hoveredId, setHoveredId] = useState<string | null>(null);

  const formatDate = (ts: number): string => {
    const d = new Date(ts);
    const now = new Date();
    const diff = now.getTime() - d.getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));

    if (days === 0) {
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }
    if (days === 1) return 'Yesterday';
    if (days < 7) return `${days}d ago`;
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
  };

  if (collapsed) {
    return (
      <aside className="conv-sidebar conv-sidebar--collapsed">
        <button className="conv-sidebar__collapse-btn" onClick={onToggleCollapse} title="Expand sidebar">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <button className="conv-sidebar__new-btn-small" onClick={onNew} title="New chat">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
        </button>
        <div className="conv-sidebar__list-small">
          {conversations.map((conv) => (
            <button
              key={conv.id}
              className={`conv-sidebar__item-small ${conv.id === activeId ? 'conv-sidebar__item-small--active' : ''}`}
              onClick={() => onSelect(conv.id)}
              title={conv.title}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
              </svg>
            </button>
          ))}
        </div>
      </aside>
    );
  }

  return (
    <aside className="conv-sidebar">
      <div className="conv-sidebar__header">
        <span className="conv-sidebar__brand">Locus</span>
        <button className="conv-sidebar__collapse-btn" onClick={onToggleCollapse} title="Collapse sidebar">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>
      </div>

      <button className="conv-sidebar__new-btn" onClick={onNew}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        New Chat
      </button>

      <div className="conv-sidebar__list">
        {conversations.length === 0 ? (
          <div className="conv-sidebar__empty">
            <p>No conversations yet</p>
          </div>
        ) : (
          conversations.map((conv) => (
            <div
              key={conv.id}
              className={`conv-sidebar__item ${conv.id === activeId ? 'conv-sidebar__item--active' : ''}`}
              onClick={() => onSelect(conv.id)}
              onMouseEnter={() => setHoveredId(conv.id)}
              onMouseLeave={() => setHoveredId(null)}
            >
              <div className="conv-sidebar__item-content">
                <div className="conv-sidebar__item-title">{conv.title}</div>
                <div className="conv-sidebar__item-meta">
                  <span>{formatDate(conv.createdAt)}</span>
                  <span>&middot;</span>
                  <span>{conv.messages.length} messages</span>
                </div>
              </div>
              {hoveredId === conv.id && (
                <button
                  className="conv-sidebar__delete-btn"
                  onClick={(e: React.MouseEvent) => {
                    e.stopPropagation();
                    onDelete(conv.id);
                  }}
                  title="Delete conversation"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="3 6 5 6 21 6" />
                    <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
                  </svg>
                </button>
              )}
            </div>
          ))
        )}
      </div>
    </aside>
  );
}
