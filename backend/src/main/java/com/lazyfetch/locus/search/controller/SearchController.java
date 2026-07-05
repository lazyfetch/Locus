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

@RestController
public class SearchController {

    private final SearchEngineService searchEngine;
    private final HybridSearchService hybridSearchService;
    private final PgVectorService pgVectorService;
    private final MfQueryPlanner mfQueryPlanner;
    private final MfDataService mfDataService;


    public SearchController(SearchEngineService searchEngine, HybridSearchService hybridSearchService,
                        PgVectorService pgVectorService, MfQueryPlanner mfQueryPlanner, MfDataService mfDataService) {
        this.searchEngine = searchEngine;
        this.hybridSearchService = hybridSearchService;
        this.pgVectorService = pgVectorService;
        this.mfQueryPlanner = mfQueryPlanner;
        this.mfDataService = mfDataService;

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
}
