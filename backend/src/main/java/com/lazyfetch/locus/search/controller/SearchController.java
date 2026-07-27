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
import com.lazyfetch.locus.search.planner.RetrievalPlan;
import com.lazyfetch.locus.search.data.MfDataService;
import com.lazyfetch.locus.search.context.ContextBudgetAllocator;
import com.lazyfetch.locus.search.context.BudgetAllocation;
import com.lazyfetch.locus.search.context.ContextCompressor;
import com.lazyfetch.locus.search.context.ContextAssembler;
import com.lazyfetch.locus.search.rag.RagService;
import com.lazyfetch.locus.eval.EvaluationService;
import com.lazyfetch.locus.eval.EvaluationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import com.lazyfetch.locus.search.llm.LlmClient;
import com.lazyfetch.locus.search.llm.LlmResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    private final EvaluationService evaluationService;
    private final LlmClient llmClient;
    private final ObjectMapper mapper;

    public SearchController(SearchEngineService searchEngine, HybridSearchService hybridSearchService,
                        PgVectorService pgVectorService, MfQueryPlanner mfQueryPlanner, MfDataService mfDataService,
                        ContextBudgetAllocator budgetAllocator,
                        ContextCompressor contextCompressor,
                        ContextAssembler contextAssembler, RagService ragService, EvaluationService evaluationService, LlmClient llmClient, ObjectMapper mapper) {
        this.searchEngine = searchEngine;
        this.hybridSearchService = hybridSearchService;
        this.pgVectorService = pgVectorService;
        this.mfQueryPlanner = mfQueryPlanner;
        this.mfDataService = mfDataService;
        this.budgetAllocator = budgetAllocator;
        this.contextCompressor = contextCompressor;
        this.contextAssembler = contextAssembler;
        this.ragService = ragService;
        this.evaluationService = evaluationService;
        this.llmClient = llmClient;
        this.mapper = mapper;
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

    @GetMapping("/eval/baseline")
    public EvaluationReport runBaseline() throws Exception {
        return evaluationService.evaluate();
    }

    @GetMapping("/eval/save")
    public Map<String, Object> saveBaseline(@RequestParam String phase) throws Exception {
        EvaluationReport report = evaluationService.evaluate();
        
        // Build history entry
        Map<String, Object> entry = new HashMap<>();
        entry.put("timestamp", java.time.LocalDate.now().toString());
        entry.put("phase", phase);
        entry.put("avgPrecision", report.getAvgPrecision());
        entry.put("avgRecall", report.getAvgRecall());
        entry.put("intentAccuracy", report.getIntentAccuracy());
        entry.put("metricsAccuracy", report.getMetricsAccuracy());
        entry.put("avgLatencyMs", report.getAvgLatencyMs());
        entry.put("totalTokensUsed", report.getTotalTokensUsed());
        
        // Read existing history
        Path historyPath = Paths.get("eval_history.json");
        List<Map<String, Object>> history = new ArrayList<>();
        if (Files.exists(historyPath)) {
            history = mapper.readValue(historyPath.toFile(), new TypeReference<List<Map<String, Object>>>() {});
        }
        
        // Append and save
        history.add(entry);
        mapper.writerWithDefaultPrettyPrinter().writeValue(historyPath.toFile(), history);
        
        return Map.of("status", "saved", "entry", entry);
    }

    @GetMapping("/eval/table")
    public String getEvolutionTable() throws Exception {
        Path historyPath = Paths.get("eval_history.json");
        if (!Files.exists(historyPath)) return "No history yet. Run /eval/save first.";
        
        List<Map<String, Object>> history = mapper.readValue(
            historyPath.toFile(), new TypeReference<List<Map<String, Object>>>() {});
        
        StringBuilder sb = new StringBuilder();
        sb.append("# Performance Evolution\n\n");
        sb.append("| Phase | Precision | Recall | Intent Acc | Metrics Acc | Latency (ms) | Tokens |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        
        for (var entry : history) {
            sb.append(String.format("| %s | %.2f | %.2f | %.1f%% | %.1f%% | %d | %d |\n",
                entry.get("phase"),
                (double) entry.get("avgPrecision"),
                (double) entry.get("avgRecall"),
                (double) entry.get("intentAccuracy"),
                (double) entry.get("metricsAccuracy"),
                ((Number) entry.get("avgLatencyMs")).longValue(),
                (int) entry.get("totalTokensUsed")
            ));
        }
        
        return sb.toString();
    }

    @GetMapping("/eval/compare")
    public Map<String, Object> compareWithVanilla(@RequestParam String q) throws Exception {
        // Vanilla LLM
        String vanillaPrompt = "Answer this financial question concisely. If you don't have current data, say so:\n\n" + q;
        long vanillaStart = System.currentTimeMillis();
        LlmResponse vanillaResponse = llmClient.chat(vanillaPrompt, q, 500);
        long vanillaLatency = System.currentTimeMillis() - vanillaStart;
        
        
        long locusStart = System.currentTimeMillis();
        Map<String, Object> locusResponse = ragService.ask(q, null);
        long locusLatency = System.currentTimeMillis() - locusStart;
        
        
        Map<String, Object> result = new HashMap<>();
        result.put("question", q);
        
        Map<String, Object> vanilla = new HashMap<>();
        vanilla.put("answer", vanillaResponse.getContent());
        vanilla.put("tokensUsed", vanillaResponse.getTotalTokens());
        vanilla.put("latencyMs", vanillaLatency);
        result.put("vanilla", vanilla);
        
        Map<String, Object> locus = new HashMap<>();
        locus.put("answer", locusResponse.get("answer"));
        locus.put("tokensUsed", locusResponse.get("tokensUsed"));
        locus.put("latencyMs", locusLatency);
        locus.put("sources", locusResponse.get("sources"));
        result.put("locus", locus);
        
        return result;
    }

}
