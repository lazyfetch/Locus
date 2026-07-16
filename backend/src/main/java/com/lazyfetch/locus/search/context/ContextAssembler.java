package com.lazyfetch.locus.search.context;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class ContextAssembler 
{

    private static final String SYSTEM_PROMPT = """
        You are Locus AI, a financial intelligence assistant specializing in Indian mutual funds and markets.

        You may receive structured financial data and fund document excerpts to help answer questions.
        When data IS provided:
        - Use it as your primary source for specific numbers and facts.
        - Cite the data when making factual claims about fund performance or holdings.

        When data IS NOT provided for a specific fund or question:
        - Use your general knowledge about the fund, market, or topic.
        - Clearly indicate when you're using general knowledge vs. provided data.
        - You may mention well-known facts, past performance trends, fund house reputation, etc.

        General guidelines:
        - If you are aware of any past scams, regulatory issues, or governance concerns, mention them.
        - When showing returns, always mention the period (1Y, 3Y, 5Y, etc.).
        - When comparing funds, present data in a clear comparison format.
        - Keep answers concise, factual, and well-structured.
        - Do NOT fabricate specific numbers. If you don't know a number, say so.
    """;

    public String assemble(List<Map<String, Object>> structured, List<Map<String, Object>> chunks, String history, String userQuery) 
    {
        StringBuilder prompt = new StringBuilder();

        // 1. System prompt (ALWAYS present)
        prompt.append(SYSTEM_PROMPT);
        prompt.append("\n\n---\n\n");

        // 2. Conversation history (if any)
        if (history != null && !history.isEmpty()) {
            prompt.append("## Conversation History\n");
            prompt.append(history);
            prompt.append("\n\n---\n\n");
        }

        // 3. Financial Data
        if (structured != null && !structured.isEmpty()) 
        {
            prompt.append("## Financial Data\n");

            // Detect what data we have
            boolean hasFundDetails = false;
            boolean hasReturns = false;
            boolean hasHoldings = false;

            for (Map<String, Object> item : structured) 
            {
                if (item.containsKey("scheme_name") && !item.containsKey("period") && !item.containsKey("stock_name"))
                {
                    hasFundDetails = true;
                }
                if (item.containsKey("period")) 
                {
                    hasReturns = true;
                }
                if (item.containsKey("stock_name")) 
                {
                    hasHoldings = true;
                }
            }

            // Fund Details section
            if (hasFundDetails) 
            {
                prompt.append("### Fund Details\n");
                for (Map<String, Object> item : structured) 
                {
                    if (item.containsKey("scheme_name") && !item.containsKey("period") && !item.containsKey("stock_name")) 
                    {
                        prompt.append("- **").append(item.get("scheme_name")).append("**");
                        if (item.get("fund_house") != null) 
                        {
                            prompt.append(" (").append(item.get("fund_house")).append(")");
                        }
                        if (item.get("scheme_category") != null) 
                        {
                            prompt.append(" — ").append(item.get("scheme_category"));
                        }
                        prompt.append("\n");
                    }
                }
                prompt.append("\n");
            }

            if (hasReturns) 
            {
                prompt.append("### Returns\n");
                
                
                java.util.Set<String> periods = new java.util.LinkedHashSet<>();
                for (Map<String, Object> item : structured) {
                    if (item.containsKey("period")) {
                        periods.add((String) item.get("period"));
                    }
                }
                
                prompt.append("| Fund |");
                for (String p : periods) {
                    prompt.append(" ").append(p).append(" |");
                }
                prompt.append("\n|");
                for (int i = 0; i <= periods.size(); i++) {
                    prompt.append("---|");
                }
                prompt.append("\n");

                java.util.Map<String, Map<String, String>> returnsByFund = new java.util.HashMap<>();
                for (Map<String, Object> item : structured) {
                    if (item.containsKey("period")) {
                        String fundName = (String) item.get("scheme_name");
                        String period = (String) item.get("period");
                        String value = (String) item.get("fund_return_pct");
                        returnsByFund.computeIfAbsent(fundName, k -> new java.util.HashMap<>());
                        returnsByFund.get(fundName).put(period, value);
                    }
                }

                for (java.util.Map.Entry<String, Map<String, String>> entry : returnsByFund.entrySet()) {
                    prompt.append("| ").append(entry.getKey()).append(" |");
                    for (String p : periods) {
                        String val = entry.getValue().get(p);
                        prompt.append(" ").append(val != null ? val + "%" : "—").append(" |");
                    }
                    prompt.append("\n");
                }
                prompt.append("\n");
            }

            // Holdings section
            if (hasHoldings) {
                prompt.append("### Top Holdings\n");
                prompt.append("| Stock | Allocation |\n");
                prompt.append("|---|---|\n");
                for (Map<String, Object> item : structured) {
                    if (item.containsKey("stock_name")) {
                        prompt.append("| ").append(item.get("stock_name"))
                              .append(" | ").append(item.get("percentage")).append("% |\n");
                    }
                }
                prompt.append("\n");
            }
        }

        // 4. Fund Documents
        if (chunks != null && !chunks.isEmpty()) {
            prompt.append("## Relevant Fund Documents\n");
            for (Map<String, Object> chunk : chunks) {
                String sectionType = (String) chunk.getOrDefault("section_type", "General");
                String text = (String) chunk.get("chunk_text");
                prompt.append("- **[").append(sectionType).append("]** ");
                prompt.append(text).append("\n\n");
            }
        }

        // 5. User's current question
        prompt.append("\n---\n\n");
        prompt.append("## User Question\n");
        prompt.append(userQuery);

        return prompt.toString().trim();
    }
}
