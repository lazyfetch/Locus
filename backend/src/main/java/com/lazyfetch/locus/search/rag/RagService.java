package com.lazyfetch.locus.search.rag;

import com.lazyfetch.locus.search.context.*;
import com.lazyfetch.locus.search.conversation.ConversationService;
import com.lazyfetch.locus.search.conversation.MemoryManager;
import com.lazyfetch.locus.search.conversation.Message;
import com.lazyfetch.locus.search.dto.HybridSearchResponse;
import com.lazyfetch.locus.search.hybrid.HybridSearchService;
import com.lazyfetch.locus.search.llm.LlmClient;
import com.lazyfetch.locus.search.llm.LlmResponse;
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

    public RagService(
            HybridSearchService hybridSearchService,
            ContextBudgetAllocator budgetAllocator,
            ContextCompressor contextCompressor,
            ContextAssembler contextAssembler,
            ConversationService conversationService,
            MemoryManager memoryManager,
            LlmClient llmClient) {
        this.hybridSearchService = hybridSearchService;
        this.budgetAllocator = budgetAllocator;
        this.contextCompressor = contextCompressor;
        this.contextAssembler = contextAssembler;
        this.conversationService = conversationService;
        this.memoryManager = memoryManager;
        this.llmClient = llmClient;
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
            .map(c -> Map.of("section_type", c.get("section_type"), "chunk_text", c.get("chunk_text")))
            .collect(Collectors.toList()));
        
        return result;
    }
}
