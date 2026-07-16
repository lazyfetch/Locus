import { useState, useCallback, useEffect } from 'react';
import type { Conversation, Message } from '../types';
import Header from '../components/Header';
import ConversationSidebar from '../components/ConversationSidebar';
import MessageList from '../components/MessageList';
import MessageInput from '../components/MessageInput';
import ContextPanel from '../components/ContextPanel';
import { WELCOME_MESSAGE } from '../data/mockData';
import './ChatPage.css';

const STORAGE_KEY = 'locus-conversations';
const DEFAULT_TITLE = 'New Chat';
const TOKEN_TOTAL = 6000;

function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
}

function loadConversations(): Conversation[] {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) return JSON.parse(stored) as Conversation[];
  } catch {
 
  }
  return [];
}

function saveConversations(conversations: Conversation[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations));
  } catch {
  }
}

function createConversation(): Conversation {
  return {
    id: generateId(),
    title: DEFAULT_TITLE,
    messages: [WELCOME_MESSAGE],
    tokenUsed: 1200,
    createdAt: Date.now(),
  };
}

function estimateTokens(text: string): number {
  return Math.ceil((text?.length || 0) / 4);
}

function deriveTitle(messages: Message[]): string {
  const firstUser = messages.find((m) => m.role === 'user');
  if (firstUser) {
    const text = firstUser.content.replace(/\*\*(.*?)\*\*/g, '$1').replace(/\n.*/, '');
    return text.length > 40 ? text.slice(0, 40) + '\u2026' : text;
  }
  return DEFAULT_TITLE;
}

export default function ChatPage() {
  const [conversations, setConversations] = useState<Conversation[]>(() => {
    const loaded = loadConversations();
    return loaded.length > 0 ? loaded : [createConversation()];
  });
  const [activeId, setActiveId] = useState<string>(conversations[0]?.id || '');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [panelOpen, setPanelOpen] = useState(false);
  const [isTyping, setIsTyping] = useState(false);

  useEffect(() => {
    saveConversations(conversations);
  }, [conversations]);

  const activeConversation = conversations.find((c) => c.id === activeId) || conversations[0];
  const messages = activeConversation?.messages || [];
  const tokenUsed = activeConversation?.tokenUsed || 0;

  const updateConversation = useCallback((id: string, updater: (conv: Conversation) => Conversation) => {
    setConversations((prev) =>
      prev.map((c) => (c.id === id ? updater(c) : c))
    );
  }, []);

  const handleNewChat = useCallback(() => {
    const newConv = createConversation();
    setConversations((prev) => [newConv, ...prev]);
    setActiveId(newConv.id);
    setIsTyping(false);
  }, []);

  const handleSelectConversation = useCallback((id: string) => {
    setActiveId(id);
    setIsTyping(false);
  }, []);

  const handleDeleteConversation = useCallback((id: string) => {
    setConversations((prev) => {
      const filtered = prev.filter((c) => c.id !== id);
      if (filtered.length === 0) {
        const newConv = createConversation();
        setActiveId(newConv.id);
        return [newConv];
      }
      if (id === activeId) {
        setActiveId(filtered[0].id);
      }
      return filtered;
    });
  }, [activeId]);

  const handleSend = useCallback(async (text: string, attachment: File | null) => {
    if (!text && !attachment) return;

    const userTokens = estimateTokens(text);
    const userMessage: Message = {
      role: 'user',
      content: text + (attachment ? `\n\n*Attached: ${attachment.name}*` : ''),
      metrics: null,
      sources: [],
    };

    updateConversation(activeId, (conv) => {
      const newMessages = [...conv.messages, userMessage];
      const title = conv.title === DEFAULT_TITLE ? deriveTitle(newMessages) : conv.title;
      return {
        ...conv,
        title,
        messages: newMessages,
        tokenUsed: Math.min(conv.tokenUsed + userTokens + 50, TOKEN_TOTAL),
      };
    });

    setIsTyping(true);

    try {
      const res = await fetch('/api/ask', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          question: text,
          conversationId: activeId,
        }),
      });

      if (!res.ok) {
        throw new Error(`API error: ${res.status}`);
      }

      const data = await res.json();

      
      const assistantMessage: Message = {
        role: 'assistant',
        content: data.answer || '',
        metrics: null,
        sources: (data.sources || []).map((s: Record<string, unknown>) => ({
          title: (s.section_type as string) || 'Source',
          snippet: (s.chunk_text as string) || '',
        })),
      };

      updateConversation(activeId, (conv) => ({
        ...conv,
        messages: [...conv.messages, assistantMessage],
        tokenUsed: Math.min(conv.tokenUsed + (data.tokensUsed || 0), TOKEN_TOTAL),
      }));
    } catch (err) {
      console.error('Failed to get AI response:', err);

      
      updateConversation(activeId, (conv) => ({
        ...conv,
        messages: [
          ...conv.messages,
          {
            role: 'assistant',
            content: `**Error:** Could not reach the AI backend. Ensure the server is running on port 8081.\n\n\`\`\`\n${err instanceof Error ? err.message : String(err)}\n\`\`\``,
            metrics: null,
            sources: [],
          } as Message,
        ],
      }));
    } finally {
      setIsTyping(false);
    }
  }, [activeId, updateConversation]);

  const toggleSidebar = useCallback(() => {
    setSidebarCollapsed((prev) => !prev);
  }, []);

  const togglePanel = useCallback(() => {
    setPanelOpen((prev) => !prev);
  }, []);

  return (
    <div className="chat-page">
      <ConversationSidebar
        conversations={conversations}
        activeId={activeId}
        onSelect={handleSelectConversation}
        onNew={handleNewChat}
        onDelete={handleDeleteConversation}
        collapsed={sidebarCollapsed}
        onToggleCollapse={toggleSidebar}
      />
      <div className="chat-page__right">
        <Header
          tokenUsed={tokenUsed}
          tokenTotal={TOKEN_TOTAL}
          onToggleContext={togglePanel}
        />
        <div className="chat-page__body">
          <div className="chat-page__main">
            <MessageList messages={messages} />
            {isTyping && (
              <div className="chat-page__typing">
                <div className="chat-page__typing-dot" />
                <div className="chat-page__typing-dot" />
                <div className="chat-page__typing-dot" />
              </div>
            )}
            <MessageInput onSend={handleSend} />
          </div>

          <ContextPanel
            open={panelOpen}
            onClose={() => setPanelOpen(false)}
            messages={messages}
            tokenUsed={tokenUsed}
            tokenTotal={TOKEN_TOTAL}
          />
        </div>
      </div>
    </div>
  );
}
