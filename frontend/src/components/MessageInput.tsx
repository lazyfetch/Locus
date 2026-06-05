import { useState, useRef } from 'react';
import type { MessageInputProps } from '../types';
import './MessageInput.css';

export default function MessageInput({ onSend }: MessageInputProps) {
  const [text, setText] = useState('');
  const [attachment, setAttachment] = useState<File | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const handleSend = () => {
    const trimmed = text.trim();
    if (!trimmed && !attachment) return;

    onSend(trimmed, attachment);

    setText('');
    setAttachment(null);
    inputRef.current?.focus();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setAttachment(file);
    }
    e.target.value = '';
  };

  const removeAttachment = () => {
    setAttachment(null);
  };

  return (
    <div className="message-input-wrapper">
      <div className="message-input">
        {attachment && (
          <div className="message-input__attachment">
            <span className="message-input__attachment-icon">&#128206;</span>
            <span className="message-input__attachment-name">{attachment.name}</span>
            <button
              className="message-input__attachment-remove"
              onClick={removeAttachment}
              title="Remove attachment"
            >
              &times;
            </button>
          </div>
        )}

        <div className="message-input__row">
          <button
            className="message-input__attach-btn"
            onClick={() => fileRef.current?.click()}
            title="Attach a document"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48" />
            </svg>
          </button>
          <input
            ref={fileRef}
            type="file"
            className="message-input__file-input"
            onChange={handleFileChange}
            accept=".pdf,.doc,.docx,.txt,.csv,.xlsx"
          />

          <textarea
            ref={inputRef}
            className="message-input__textarea"
            placeholder="Ask about financial data, companies, or markets..."
            value={text}
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setText(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={1}
          />

          <button
            className="message-input__send-btn"
            onClick={handleSend}
            disabled={!text.trim() && !attachment}
            title="Send message"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="22" y1="2" x2="11" y2="13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  );
}
