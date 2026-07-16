package com.lazyfetch.locus.search.controller;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import com.lazyfetch.locus.Filters.CustomAnalyzer;
import com.lazyfetch.locus.search.dto.HybridSearchResponse;
import com.lazyfetch.locus.search.engine.SearchEngineService;
import com.lazyfetch.locus.search.hybrid.HybridSearchService;
import com.lazyfetch.locus.search.pgvector.PgVectorService;
import com.lazyfetch.locus.search.planner.MfQueryPlanner;
import com.lazyfetch.locus.records.VectorSearchResult;
import com.lazyfetch.locus.search.planner.MfQueryPlanner;
import com.lazyfetch.locus.search.planner.RetrievalPlan;
import com.lazyfetch.locus.search.data.MfDataService;
import com.lazyfetch.locus.search.context.ContextBudgetAllocator;
import com.lazyfetch.locus.search.context.BudgetAllocation;
import com.lazyfetch.locus.search.context.ContextCompressor;
import com.lazyfetch.locus.search.context.ContextAssembler;
import com.lazyfetch.locus.search.rag.RagService;

@RestController
public class SearchController {

    private final SearchEngineService searchEngine;
    private final HybridSearchService hybridSearchService;
    private final PgVectorService pgVectorService;
    private final MfQueryPlanner mfQueryPlanner;
    private final MfDataService mfDataService;
    private final ContextBudgetAllocator budgetAllocator;
    private final ContextCompressor contextCompressor;
    private final ContextAssembler contextAssembler;
    private final RagService ragService;


    public SearchController(SearchEngineService searchEngine, HybridSearchService hybridSearchService,
                        PgVectorService pgVectorService, MfQueryPlanner mfQueryPlanner, MfDataService mfDataService,
                        ContextBudgetAllocator budgetAllocator,
                        ContextCompressor contextCompressor,
                        ContextAssembler contextAssembler, RagService ragService) {
        this.searchEngine = searchEngine;
        this.hybridSearchService = hybridSearchService;
        this.pgVectorService = pgVectorService;
        this.mfQueryPlanner = mfQueryPlanner;
        this.mfDataService = mfDataService;
        this.budgetAllocator = budgetAllocator;
        this.contextCompressor = contextCompressor;
        this.contextAssembler = contextAssembler;
        this.ragService = ragService;

    }

    @PostMapping("/index")
    public String index(@RequestBody Map<String, Object> body) throws Exception {
        String title = (String) body.get("title");
        String docBody = (String) body.get("body");

        List<String> tickers = new ArrayList<>();
        Object tickerValue = body.get("ticker");
        if (tickerValue instanceof String && !((String) tickerValue).isBlank()) 
        {
            tickers.add(((String) tickerValue).toUpperCase());
        }

        Object tickersValue = body.get("tickers");
        if (tickersValue instanceof List<?> list) {
            for (Object t : list) {
                if (t != null) {
                    String s = t.toString().trim();
                    if (!s.isEmpty()) {
                        tickers.add(s.toUpperCase());
                    }
                }
            }
        }

        searchEngine.indexDocument(title, docBody, tickers);
        return "Indexed successfully!";
    }

    @GetMapping("/search")
    public List<Map<String, String>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int n) throws Exception {
        return searchEngine.search(q, n);
    }

    @GetMapping("/debug-tokens")
    public Map<String, List<String>> debugTokens(@RequestParam String text) throws Exception {
        Map<String, List<String>> result = new HashMap<>();

        List<String> standardTokens = getTokens(new StandardAnalyzer(), text);
        result.put("StandardAnalyzer", standardTokens);

        List<String> customTokens = getTokens(new CustomAnalyzer(), text);
        result.put("CustomAnalyzer", customTokens);

        return result;
    }

    @PostMapping("/api/search")
    public HybridSearchResponse apiSearch(@RequestBody Map<String, Object> body) throws Exception {
        String query = (String) body.get("query");
        int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 5;
        return hybridSearchService.hybridSearch(query, topK);
    }

    private List<String> getTokens(Analyzer analyzer, String text) throws Exception {
        List<String> tokens = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream("body", text)) {
            CharTermAttribute attr = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                tokens.add(attr.toString());
            }
            stream.end();
        }
        return tokens;
    }

    @GetMapping("/test-pgvector")
    public List<VectorSearchResult> testPgVector(@RequestParam String q) throws Exception {
        return pgVectorService.search(q, 5, null);
    }

    @GetMapping("/test-plan")
    public RetrievalPlan testPlan(@RequestParam String q) {
        return mfQueryPlanner.plan(q);
    }

    @GetMapping("/debug-funds")
    public List<Map<String, Object>> debugFunds(@RequestParam String q) throws Exception {
        List<Integer> codes = mfQueryPlanner.plan(q).getSchemeCodes();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Integer code : codes) 
        {
            Map<String, Object> details = mfDataService.getFundDetails(code);
            if (details != null) 
            {
                result.add(details);
            }
            if (result.size() >= 20) break;  
        }
        return result;
    }

    @GetMapping("/test-prompt")
    public Map<String, Object> testPrompt(@RequestParam String q) throws Exception {
        HybridSearchResponse searchResults = hybridSearchService.hybridSearch(q, 5);
        
        // allocate budget
        boolean hasData = !searchResults.getStructured().isEmpty();
        boolean hasChunks = !searchResults.getUnstructured().isEmpty();
        BudgetAllocation allocation = budgetAllocator.allocate(
            searchResults.getPlan().getIntent(), false, hasData, hasChunks);
        
        // compress
        List<Map<String, Object>> compressedData = contextCompressor.compressStructured(
            searchResults.getStructured(), allocation.getDataTokens());
        List<Map<String, Object>> compressedChunks = contextCompressor.compressChunks(
            searchResults.getUnstructured(), allocation.getChunkTokens());
        
        // assemble
        String prompt = contextAssembler.assemble(
            compressedData, compressedChunks, null, q);
        
        // return
        Map<String, Object> result = new HashMap<>();
        result.put("prompt", prompt);
        result.put("estimatedTokens", prompt.length() / 4);
        result.put("budgetAllocation", Map.of(
            "history", allocation.getHistoryTokens(),
            "data", allocation.getDataTokens(),
            "chunks", allocation.getChunkTokens(),
            "total", allocation.getTotal()
        ));
        result.put("compressedDataCount", compressedData.size());
        result.put("compressedChunksCount", compressedChunks.size());
        result.put("rawDataCount", searchResults.getStructured().size());
        result.put("rawChunksCount", searchResults.getUnstructured().size());
        
        return result;
    }

    @PostMapping("/api/ask")
    public Map<String, Object> ask(@RequestBody Map<String, Object> body) throws Exception {
        String question = (String) body.get("question");
        String conversationId = (String) body.get("conversationId");
        return ragService.ask(question, conversationId);
    }
}
