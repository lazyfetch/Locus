package com.lazyfetch.locus.search.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


public class Message 
{
    private String role;          
    private String content;
    private Instant timestamp;
    private List<Integer> schemeCodes;   
    private List<String> citations;     

    public Message(String role, String content) 
    {
        this.role = role;
        this.content = content;
        this.timestamp = Instant.now();
        this.schemeCodes = new ArrayList<>();
        this.citations = new ArrayList<>();
    }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getTimestamp() { return timestamp; }
    public List<Integer> getSchemeCodes() { return schemeCodes; }
    public void setSchemeCodes(List<Integer> schemeCodes) { this.schemeCodes = schemeCodes; }
    public List<String> getCitations() { return citations; }
    public void setCitations(List<String> citations) { this.citations = citations; }
}
