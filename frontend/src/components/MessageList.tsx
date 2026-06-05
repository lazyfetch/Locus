import { useEffect, useRef } from 'react';
import type { MessageListProps } from '../types';
import Message from './Message';
import './MessageList.css';

export default function MessageList({ messages }: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  if (!messages || messages.length === 0) {
    return (
      <div className="message-list message-list--empty">
        <div className="message-list__empty-state">
          <div className="message-list__empty-icon">L</div>
          <h2>Welcome to Locus</h2>
          <p>Your AI-powered financial intelligence assistant. Ask me anything about companies, markets, or financial metrics.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="message-list">
      <div className="message-list__inner">
        {messages.map((msg, i) => (
          <Message key={i} message={msg} />
        ))}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
