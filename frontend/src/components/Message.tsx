import { Fragment, useState } from 'react';
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

  const lines = text.replace(/\r\n/g, '\n').split('\n');
  const blocks: JSX.Element[] = [];
  let index = 0;
  let paragraph: string[] = [];

  const flushParagraph = () => {
    if (paragraph.length > 0) {
      blocks.push(<p key={`p-${blocks.length}`}>{renderInline(paragraph.join(' '))}</p>);
      paragraph = [];
    }
  };

  while (index < lines.length) {
    const line = lines[index];
    if (line.trim().startsWith('```')) {
      flushParagraph();
      const language = line.trim().slice(3).trim();
      const code: string[] = [];
      index += 1;
      while (index < lines.length && !lines[index].trim().startsWith('```')) {
        code.push(lines[index]);
        index += 1;
      }
      blocks.push(
        <pre className="message__code" key={`code-${blocks.length}`}>
          <code data-language={language || undefined}>{code.join('\n')}</code>
        </pre>
      );
      index += 1;
      continue;
    }

    if (isTableStart(lines, index)) {
      flushParagraph();
      const headers = splitTableRow(lines[index]);
      index += 2;
      const rows: string[][] = [];
      while (index < lines.length && isTableRow(lines[index])) {
        rows.push(splitTableRow(lines[index]));
        index += 1;
      }
      blocks.push(
        <div className="message__table-wrap" key={`table-${blocks.length}`}>
          <table className="message__markdown-table">
            <thead><tr>{headers.map((header, i) => <th key={i}>{renderInline(header)}</th>)}</tr></thead>
            <tbody>{rows.map((row, rowIndex) => (
              <tr key={rowIndex}>{headers.map((_, cellIndex) => <td key={cellIndex}>{renderInline(row[cellIndex] || '')}</td>)}</tr>
            ))}</tbody>
          </table>
        </div>
      );
      continue;
    }

    const heading = line.match(/^(#{1,4})\s+(.+)$/);
    if (heading) {
      flushParagraph();
      const Heading = `h${heading[1].length}` as keyof JSX.IntrinsicElements;
      blocks.push(<Heading key={`heading-${blocks.length}`}>{renderInline(heading[2])}</Heading>);
      index += 1;
      continue;
    }

    if (/^\s*(---+|\*\*\*+)\s*$/.test(line)) {
      flushParagraph();
      blocks.push(<hr key={`hr-${blocks.length}`} />);
      index += 1;
      continue;
    }

    if (/^\s*>\s?/.test(line)) {
      flushParagraph();
      const quote: string[] = [];
      while (index < lines.length && /^\s*>\s?/.test(lines[index])) {
        quote.push(lines[index].replace(/^\s*>\s?/, ''));
        index += 1;
      }
      blocks.push(<blockquote key={`quote-${blocks.length}`}>{renderInline(quote.join(' '))}</blockquote>);
      continue;
    }

    if (/^\s*[-*+]\s+/.test(line) || /^\s*\d+[.)]\s+/.test(line)) {
      flushParagraph();
      const ordered = /^\s*\d+[.)]\s+/.test(line);
      const items: string[] = [];
      while (index < lines.length) {
        const match = lines[index].match(ordered ? /^\s*\d+[.)]\s+(.+)$/ : /^\s*[-*+]\s+(.+)$/);
        if (!match) break;
        items.push(match[1]);
        index += 1;
      }
      const List = ordered ? 'ol' : 'ul';
      blocks.push(<List key={`list-${blocks.length}`}>{items.map((item, i) => <li key={i}>{renderInline(item)}</li>)}</List>);
      continue;
    }

    if (line.trim() === '') {
      flushParagraph();
      index += 1;
      continue;
    }

    paragraph.push(line.trim());
    index += 1;
  }

  flushParagraph();
  return <div className="message__markdown">{blocks}</div>;
}

function isTableStart(lines: string[], index: number): boolean {
  return index + 1 < lines.length && isTableRow(lines[index]) && /^\s*\|?\s*:?-{3,}/.test(lines[index + 1]);
}

function isTableRow(line: string): boolean {
  return line.includes('|');
}

function splitTableRow(line: string): string[] {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim());
}

function renderInline(value: string): JSX.Element {
  const pattern = /(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`|\[[^\]]+\]\([^\)]+\))/g;
  const parts = value.split(pattern);
  return <>{parts.map((part, index) => {
    if (part.startsWith('**') && part.endsWith('**')) return <strong key={index}>{part.slice(2, -2)}</strong>;
    if (part.startsWith('*') && part.endsWith('*')) return <em key={index}>{part.slice(1, -1)}</em>;
    if (part.startsWith('`') && part.endsWith('`')) return <code className="message__inline-code" key={index}>{part.slice(1, -1)}</code>;
    const link = part.match(/^\[([^\]]+)\]\(([^\)]+)\)$/);
    if (link) return <a href={link[2]} target="_blank" rel="noreferrer" key={index}>{link[1]}</a>;
    return <Fragment key={index}>{part}</Fragment>;
  })}</>;
}
