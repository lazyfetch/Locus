package com.lazyfetch.locus;

import com.lazyfetch.locus.SearchEngineService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
}