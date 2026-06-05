import { useState, useCallback, useEffect } from 'react';
import type { Conversation, Message } from '../types';
import Header from '../components/Header';
import ConversationSidebar from '../components/ConversationSidebar';
import MessageList from '../components/MessageList';
import MessageInput from '../components/MessageInput';
import ContextPanel from '../components/ContextPanel';
import { getMockResponse, WELCOME_MESSAGE } from '../data/mockData';
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
    /* ignore */
  }
  return [];
}

function saveConversations(conversations: Conversation[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations));
  } catch {
    /* ignore */
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

  const handleSend = useCallback((text: string, attachment: File | null) => {
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

    setTimeout(() => {
      const response = getMockResponse(text);
      const responseTokens = estimateTokens(response.text) + 100;

      updateConversation(activeId, (conv) => ({
        ...conv,
        messages: [
          ...conv.messages,
          {
            role: 'assistant',
            content: response.text,
            metrics: response.metrics ?? null,
            sources: response.sources ?? null,
          } as Message,
        ],
        tokenUsed: Math.min(conv.tokenUsed + responseTokens, TOKEN_TOTAL),
      }));

      setIsTyping(false);
    }, 800 + Math.random() * 700);
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
