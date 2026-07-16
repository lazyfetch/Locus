package com.lazyfetch.locus.search.llm;

public interface LlmClient {
    LlmResponse chat(String systemPrompt, String userPrompt, int maxTokens);
}

