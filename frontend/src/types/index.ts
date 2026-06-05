export type MessageRole = 'user' | 'assistant';

export interface Metric {
  label: string;
  value: string;
  date: string;
}

export interface Source {
  title: string;
  snippet: string;
}

export interface Message {
  role: MessageRole;
  content: string;
  metrics: Metric[] | null;
  sources: Source[] | null;
}

export interface Conversation {
  id: string;
  title: string;
  messages: Message[];
  tokenUsed: number;
  createdAt: number;
}

export interface UserProfile {
  email: string;
  username: string;
}

export interface MockResponse {
  text: string;
  metrics: Metric[] | null;
  sources: Source[] | null;
}

export type Theme = 'light' | 'dark';

export interface AuthContextType {
  user: UserProfile | null;
  login: (email: string, password: string) => boolean;
  signup: (name: string, email: string, password: string) => boolean;
  logout: () => void;
}

export interface ThemeContextType {
  theme: Theme;
  toggleTheme: () => void;
}

// Component prop types
export interface HeaderProps {
  tokenUsed: number;
  tokenTotal: number;
  onToggleContext: () => void;
}

export interface ConversationSidebarProps {
  conversations: Conversation[];
  activeId: string | null;
  onSelect: (id: string) => void;
  onNew: () => void;
  onDelete: (id: string) => void;
  collapsed: boolean;
  onToggleCollapse: () => void;
}

export interface MessageListProps {
  messages: Message[];
}

export interface MessageProps {
  message: Message;
}

export interface MessageInputProps {
  onSend: (text: string, attachment: File | null) => void;
}

export interface TokenBudgetIndicatorProps {
  used: number;
  total: number;
}

export interface ContextPanelProps {
  open: boolean;
  onClose: () => void;
  messages: Message[];
  tokenUsed: number;
  tokenTotal: number;
}

export interface ProtectedRouteProps {
  children: React.ReactNode;
}
