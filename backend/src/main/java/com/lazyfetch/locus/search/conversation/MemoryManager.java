package com.lazyfetch.locus.search.conversation;

import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class MemoryManager 
{

    private static final int DEFAULT_MAX_TURNS = 3; // total turn existing in memory
    private final ConversationService conversationService;

    public MemoryManager(ConversationService conversationService) 
    {
        this.conversationService = conversationService;
    }

    public String getHistoryForPrompt(String conversationId, int historyBudget) 
    {
        if (conversationId == null) return null;
        
        String summary = conversationService.getSummary(conversationId, DEFAULT_MAX_TURNS);
        if (summary.isEmpty()) return null;
        
        int estimatedTokens = summary.length() / 4;
        if (estimatedTokens > historyBudget) 
        {
            int maxChars = historyBudget * 4;
            summary = summary.substring(0, Math.min(maxChars, summary.length()));
            int lastNewline = summary.lastIndexOf('\n');
            if (lastNewline > maxChars / 2) 
            {
                summary = summary.substring(0, lastNewline);
            }
            summary += "\n...(earlier history omitted)";
        }
        
        return summary;
    }
}
