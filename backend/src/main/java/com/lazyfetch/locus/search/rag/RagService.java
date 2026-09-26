package com.lazyfetch.locus.search.rag;

import com.lazyfetch.locus.search.context.*;
import com.lazyfetch.locus.search.conversation.ConversationService;
import com.lazyfetch.locus.search.conversation.MemoryManager;
import com.lazyfetch.locus.search.conversation.Message;
import com.lazyfetch.locus.search.dto.HybridSearchResponse;
import com.lazyfetch.locus.search.hybrid.HybridSearchService;
import com.lazyfetch.locus.search.llm.LlmClient;
import com.lazyfetch.locus.search.llm.LlmResponse;
import com.lazyfetch.locus.search.tokens.TokenCounter;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagService 
{

    private final HybridSearchService hybridSearchService;
    private final ContextBudgetAllocator budgetAllocator;
    private final ContextCompressor contextCompressor;
    private final ContextAssembler contextAssembler;
    private final ConversationService conversationService;
    private final MemoryManager memoryManager;
    private final LlmClient llmClient;
    private final TokenCounter tokenCounter;

    public RagService(
            HybridSearchService hybridSearchService,
            ContextBudgetAllocator budgetAllocator,
            ContextCompressor contextCompressor,
            ContextAssembler contextAssembler,
            ConversationService conversationService,
            MemoryManager memoryManager,
            LlmClient llmClient,
            TokenCounter tokenCounter) {
        this.hybridSearchService = hybridSearchService;
        this.budgetAllocator = budgetAllocator;
        this.contextCompressor = contextCompressor;
        this.contextAssembler = contextAssembler;
        this.conversationService = conversationService;
        this.memoryManager = memoryManager;
        this.llmClient = llmClient;
        this.tokenCounter = tokenCounter;
    }

    public Map<String, Object> ask(String question, String conversationId) throws Exception 
    {
        
        // 1. Create or get conversation
        if (conversationId == null) 
        {
            conversationId = conversationService.createConversation();
        }

        // 1b. Get scheme codes from previous turns
        List<Integer> previousCodes = new ArrayList<>();
        List<Message> prevMessages = conversationService.getHistory(conversationId, 3);
        for (Message msg : prevMessages) 
        {
            if (msg.getSchemeCodes() != null) 
            {
                previousCodes.addAll(msg.getSchemeCodes());
            }
        }

        HybridSearchResponse searchResults = hybridSearchService.hybridSearch(question, 5, previousCodes);

        // 3. Allocate budget
        boolean hasData = !searchResults.getStructured().isEmpty();
        boolean hasChunks = !searchResults.getUnstructured().isEmpty();
        boolean hasHistory = conversationService.getHistory(conversationId, 1).size() > 1;
        
        BudgetAllocation allocation = budgetAllocator.allocate(searchResults.getPlan().getIntent(), hasHistory, hasData, hasChunks);

        // 4. Get history string for prompt 
        String historyStr = memoryManager.getHistoryForPrompt(conversationId, allocation.getHistoryTokens());

        // 5. Compress
        
        List<Map<String, Object>> compressedData = contextCompressor.compressStructured( searchResults.getStructured(), allocation.getDataTokens());
        List<Map<String, Object>> compressedChunks = contextCompressor.compressChunks(searchResults.getUnstructured(), allocation.getChunkTokens());

        // 6. Assemble prompt
        String prompt = contextAssembler.assemble(compressedData, compressedChunks, historyStr, question);

        // 7. Call LLM
        LlmResponse llmResponse = llmClient.chat(prompt, question, 1000);

        // 8. Store in conversation history
        conversationService.appendMessage(conversationId, "user", question);
        conversationService.appendMessage(conversationId, "assistant", llmResponse.getContent());
        conversationService.updateTokens(conversationId, llmResponse.getTotalTokens());

        // 9. Return response
        Map<String, Object> result = new HashMap<>();
        result.put("answer", llmResponse.getContent());
        result.put("conversationId", conversationId);
        result.put("tokensUsed", llmResponse.getTotalTokens());
        result.put("sources", searchResults.getUnstructured().stream()
            .map(c -> Map.of(
                "section_type", String.valueOf(c.getOrDefault("section_type", "")),
                "chunk_text", String.valueOf(c.getOrDefault("chunk_text_full",
                       c.getOrDefault("chunk_text", "")))))
            .collect(Collectors.toList()));
        result.put("contextText", prompt);                    
        String renderedData = compressedData.toString();
        String renderedChunks = compressedChunks.toString();
        result.put("dataTokens", tokenCounter.countForProvider(renderedData));
        result.put("chunkTokens", tokenCounter.countForProvider(renderedChunks));
        result.put("allocatedDataTokens", allocation.getDataTokens());
        result.put("allocatedChunkTokens", allocation.getChunkTokens());
        
        int allocatedData = ((Number) result.getOrDefault("dataTokens", 0)).intValue();
        int allocatedChunk = ((Number) result.getOrDefault("chunkTokens", 0)).intValue();
        
        return result;
    }
}
