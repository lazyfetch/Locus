package com.lazyfetch.locus.search.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ConversationState 
{
    private String conversationId;
    private List<Message> messages;
    private int totalTokensUsed;
    private Instant lastActivity;

    public ConversationState(String conversationId) 
    {
        this.conversationId = conversationId;
        this.messages = new ArrayList<>();
        this.totalTokensUsed = 0;
        this.lastActivity = Instant.now();
    }

    public String getConversationId() { return conversationId; }
    public List<Message> getMessages() { return messages; }
    public int getTotalTokensUsed() { return totalTokensUsed; }
    public void setTotalTokensUsed(int totalTokensUsed) { this.totalTokensUsed = totalTokensUsed; }
    public Instant getLastActivity() { return lastActivity; }
    public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }
}
