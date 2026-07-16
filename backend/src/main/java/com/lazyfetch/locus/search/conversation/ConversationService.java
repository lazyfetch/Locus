package com.lazyfetch.locus.search.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationService 
{

    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);
    private static final long TTL_HOURS = 24;
    private static final int MAX_TURNS = 50; 

    private final Map<String, ConversationState> conversations = new ConcurrentHashMap<>();

    public String createConversation() 
    {
        String id = UUID.randomUUID().toString();
        conversations.put(id, new ConversationState(id));
        logger.debug("Created conversation: {}", id);
        return id;
    }

    public Message appendMessage(String conversationId, String role, String content) 
    {
        ConversationState state = getOrCreate(conversationId);
        
        if (state.getMessages().size() >= MAX_TURNS) 
        {
            if (state.getMessages().size() >= 2) 
            {
                state.getMessages().remove(0);
                state.getMessages().remove(0);
            }
        }
        
        Message message = new Message(role, content);
        state.getMessages().add(message);
        state.setLastActivity(Instant.now());
        
        return message;
    }

    // fetches the last N messages
    public List<Message> getHistory(String conversationId, int maxTurns) 
    {
        ConversationState state = conversations.get(conversationId);
        if (state == null) return List.of();
        
        List<Message> all = state.getMessages();
        int start = Math.max(0, all.size() - (maxTurns * 2));  
        return all.subList(start, all.size());
    }

    // concatenates the last turns messages
    public String getSummary(String conversationId, int maxTurns) 
    {
        List<Message> history = getHistory(conversationId, maxTurns);
        if (history.isEmpty()) return "";
        
        StringBuilder sb = new StringBuilder();
        for (Message msg : history) 
        {
            String prefix = msg.getRole().equals("user") ? "User" : "Assistant";
            sb.append(prefix).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    // probably update this calculation later
    public void updateTokens(String conversationId, int tokens) 
    {
        ConversationState state = conversations.get(conversationId);
        if (state != null) {
            state.setTotalTokensUsed(state.getTotalTokensUsed() + tokens);
        }
    }

    // cleanup for eviction
    public void evictExpired() 
    {
        Instant cutoff = Instant.now().minusSeconds(TTL_HOURS * 3600);
        conversations.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity().isBefore(cutoff));
    }

    private ConversationState getOrCreate(String conversationId) 
    {
        return conversations.computeIfAbsent(conversationId, ConversationState::new);
    }
}
