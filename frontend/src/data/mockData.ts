import type { MockResponse, Message } from '../types';

export const MOCK_RESPONSES: Record<string, MockResponse> = {
  tesla: {
    text: "**Tesla Inc. (TSLA)** — As of the latest filings, Tesla continues to show strong growth in vehicle deliveries and energy storage deployment.\n\nHere are the key financial metrics:",
    metrics: [
      { label: 'P/E Ratio', value: '25.4', date: '2024-Q1' },
      { label: 'Revenue', value: '$81.5B', date: '2024-Q1' },
      { label: 'Net Income', value: '$11.2B', date: '2024-Q1' },
      { label: 'Gross Margin', value: '18.2%', date: '2024-Q1' },
    ],
    sources: [
      { title: 'Tesla Q1 2024 Financial Report', snippet: 'Revenue reached $81.5B, driven by record vehicle deliveries and growth in energy storage deployment.' },
      { title: 'Bloomberg — Tesla Earnings Analysis', snippet: "Tesla's P/E ratio of 25.4 reflects market optimism about future growth in autonomous driving and energy." },
    ],
  },
  'pe ratio': {
    text: "**Price-to-Earnings (P/E) Ratio** is a valuation metric calculated as:\n\n> **P/E = Stock Price ÷ Earnings Per Share (EPS)**\n\nIt tells you how much investors are willing to pay for $1 of a company's earnings.\n\n| Company | P/E Ratio | Sector Avg |\n|---|---|---|\n| TSLA | 25.4 | 18.2 |\n| AAPL | 28.1 | 25.0 |\n| MSFT | 33.5 | 25.0 |\n| GOOGL | 24.8 | 25.0 |",
    metrics: [
      { label: 'TSLA P/E', value: '25.4', date: '2024-Q1' },
      { label: 'AAPL P/E', value: '28.1', date: '2024-Q1' },
      { label: 'MSFT P/E', value: '33.5', date: '2024-Q1' },
    ],
    sources: [
      { title: 'Yahoo Finance — Sector PE Ratios', snippet: 'Technology sector average P/E is approximately 25.0 as of Q1 2024.' },
      { title: 'Investopedia — P/E Ratio Guide', snippet: "The P/E ratio helps investors determine the market value of a stock compared to the company's earnings." },
    ],
  },
  revenue: {
    text: "**Revenue comparison** across major tech companies for the most recent fiscal year:\n\n| Company | Revenue | Growth (YoY) |\n|---|---|---|\n| AAPL | $383.3B | +2.1% |\n| MSFT | $211.9B | +15.8% |\n| GOOGL | $307.4B | +13.5% |\n| AMZN | $574.8B | +12.4% |",
    metrics: [
      { label: 'AAPL Revenue', value: '$383.3B', date: 'FY2023' },
      { label: 'MSFT Revenue', value: '$211.9B', date: 'FY2023' },
      { label: 'GOOGL Revenue', value: '$307.4B', date: 'FY2023' },
      { label: 'AMZN Revenue', value: '$574.8B', date: 'FY2023' },
    ],
    sources: [
      { title: 'SEC Filings — Annual Reports (10-K)', snippet: 'All four companies reported fiscal year results with Microsoft showing the strongest growth at 15.8%.' },
    ],
  },
  nvidia: {
    text: "**NVIDIA Corporation (NVDA)** — The AI chip leader continues its remarkable growth trajectory driven by demand for data center GPUs.\n\nKey metrics:",
    metrics: [
      { label: 'P/E Ratio', value: '72.8', date: '2024-Q1' },
      { label: 'Revenue', value: '$60.9B', date: 'FY2024' },
      { label: 'Data Center Revenue', value: '$47.5B', date: 'FY2024' },
      { label: 'Net Income', value: '$29.8B', date: 'FY2024' },
    ],
    sources: [
      { title: 'NVIDIA FY2024 Annual Report', snippet: 'Data center revenue grew 217% year-over-year, representing 78% of total revenue.' },
      { title: 'Reuters — NVIDIA Earnings', snippet: "NVIDIA's market cap crossed $2T as AI chip demand shows no signs of slowing." },
    ],
  },
  apple: {
    text: "**Apple Inc. (AAPL)** — Services revenue continues to grow as a percentage of total revenue, reducing reliance on iPhone sales cycles.\n\nKey financial data:",
    metrics: [
      { label: 'P/E Ratio', value: '28.1', date: '2024-Q1' },
      { label: 'Revenue', value: '$383.3B', date: 'FY2023' },
      { label: 'Services Revenue', value: '$85.2B', date: 'FY2023' },
      { label: 'Net Income', value: '$97.0B', date: 'FY2023' },
    ],
    sources: [
      { title: 'Apple 10-K Filing FY2023', snippet: 'Services revenue reached an all-time high of $85.2B, now representing 22% of total revenue.' },
      { title: 'CNBC — Apple Earnings Recap', snippet: "Apple's installed base of active devices surpassed 2.2 billion, driving recurring services growth." },
    ],
  },
  default: {
    text: "I've searched the available knowledge base for your query. Here's what I found:\n\nBased on current financial data, the market is showing mixed signals with technology sectors outperforming traditional industrials. Interest rate decisions by the Federal Reserve continue to influence market sentiment.\n\n**Current Market Overview:**\n- **S&P 500:** 5,234.16 (+0.42%)\n- **NASDAQ:** 16,428.82 (+0.63%)\n- **10Y Treasury Yield:** 4.28%\n\nWould you like me to dive deeper into a specific company, sector, or financial metric?",
    metrics: [
      { label: 'S&P 500', value: '5,234.16', date: 'Latest' },
      { label: 'NASDAQ', value: '16,428.82', date: 'Latest' },
      { label: '10Y Treasury', value: '4.28%', date: 'Latest' },
    ],
    sources: [
      { title: 'MarketWatch — Market Summary', snippet: 'U.S. equities closed mixed as investors weighed corporate earnings against fresh economic data.' },
      { title: 'Federal Reserve — Latest Minutes', snippet: 'The Fed maintained interest rates at 5.25-5.50%, signaling a cautious approach to future cuts.' },
    ],
  },
};

export function getMockResponse(query: string): MockResponse {
  if (!query || typeof query !== 'string') return MOCK_RESPONSES.default;

  const q = query.toLowerCase();

  if (q.includes('tesla') || q.includes('tsla')) return MOCK_RESPONSES.tesla;
  if (q.includes('pe') || q.includes('p/e') || q.includes('price to earnings')) return MOCK_RESPONSES['pe ratio'];
  if (q.includes('revenue') || q.includes('earnings') || q.includes('financials')) return MOCK_RESPONSES.revenue;
  if (q.includes('nvidia') || q.includes('nvda')) return MOCK_RESPONSES.nvidia;
  if (q.includes('apple') || q.includes('aapl') || q.includes('iphone')) return MOCK_RESPONSES.apple;

  return MOCK_RESPONSES.default;
}

export const WELCOME_MESSAGE: Message = {
  role: 'assistant',
  content: "Hello! I'm **Locus AI**, your financial intelligence assistant.\n\nI can help you with:\n- **Company financials** — Try \"Show me Tesla's metrics\"\n- **Market comparisons** — Ask \"Compare P/E ratios\"\n- **Revenue data** — Query \"Revenue for major tech companies\"\n- **Stock insights** — Ask about NVIDIA, Apple, or any ticker\n\nWhat would you like to explore?",
  metrics: null,
  sources: [],
};
