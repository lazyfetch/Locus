package com.lazyfetch.locus;

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

@RestController
public class SearchController {

    private final SearchEngineService searchEngine;
    private final HybridSearchService hybridSearchService;

    public SearchController(SearchEngineService searchEngine, HybridSearchService hybridSearchService) {
        this.searchEngine = searchEngine;
        this.hybridSearchService = hybridSearchService;
    }

    @PostMapping("/index")
    public String index(@RequestBody Map<String, Object> body) throws Exception {
        String title = (String) body.get("title");
        String docBody = (String) body.get("body");

        List<String> tickers = new ArrayList<>();
        Object tickerValue = body.get("ticker");
        if (tickerValue instanceof String && !((String) tickerValue).isBlank()) {
            tickers.add(((String) tickerValue).toUpperCase());
        }

        Object tickersValue = body.get("tickers");
        if (tickersValue instanceof List<?>) {
            for (Object t : (List<?>) tickersValue) {
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

    @PostMapping("/hybrid-search")
    public HybridSearchResponse hybridSearch(@RequestBody Map<String, Object> body) throws Exception {
        String query = (String) body.get("query");
        int topK = body.containsKey("topK") ? (int) body.get("topK") : 5;
        double alpha = body.containsKey("alpha") ? ((Number) body.get("alpha")).doubleValue() : 0.5;
        return hybridSearchService.hybridSearch(query, topK, alpha);
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
}