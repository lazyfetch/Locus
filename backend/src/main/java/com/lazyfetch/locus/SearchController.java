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

    public SearchController(SearchEngineService searchEngine) {
        this.searchEngine = searchEngine;
    }

    @PostMapping("/index")
    public String index(@RequestBody Map<String, String> body) throws Exception {
        searchEngine.indexDocument(
            body.get("title"),
            body.get("body")
        );
        return "Indexed successfully!";
    }

    @GetMapping("/search")
    public List<Map<String, String>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int n) throws Exception {
        return searchEngine.search(q, n);
    }

   

    @GetMapping("/debug-tokens")
    public Map<String, List<String>> debugTokens(@RequestParam String text) throws Exception 
    {
        Map<String, List<String>> result = new HashMap<>();

        List<String> standardTokens = getTokens(new StandardAnalyzer(), text);
        result.put("StandardAnalyzer", standardTokens);

        List<String> customTokens = getTokens(new CustomAnalyzer(), text);
        result.put("CustomAnalyzer", customTokens);

        return result;
    }

    private List<String> getTokens(Analyzer analyzer, String text) throws Exception 
    {
        List<String> tokens = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream("body", text)) 
        {
            CharTermAttribute attr = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) 
            {
                tokens.add(attr.toString());
            }
            stream.end();
        }
        return tokens;
    }
}