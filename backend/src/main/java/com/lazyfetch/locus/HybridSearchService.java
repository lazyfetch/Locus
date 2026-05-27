package com.lazyfetch.locus;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
public class HybridSearchService {

    private final SearchEngineService textSearchService;
    private final VectorSearchService vectorSearchService;

    public HybridSearchService(SearchEngineService textSearchService, VectorSearchService vectorSearchService) {
        this.textSearchService = textSearchService;
        this.vectorSearchService = vectorSearchService;
    }

    public List<Map<String, String>> hybridSearch(String query, int topK, double alpha) throws Exception {
        // Run text and vector searches in parallel
        var textFuture = CompletableFuture.supplyAsync(() -> {
            try 
            {
                return textSearchService.search(query, topK * 2); 
            } 
            catch (Exception e) 
            {
                throw new RuntimeException(e);
            }
        });
        var vectorFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return vectorSearchService.vectorSearch(query, topK * 2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        List<Map<String, String>> textResults = textFuture.get();
        List<Map<String, String>> vectorResults = vectorFuture.get();

        // Reciprocal Rank Fusion
        Map<String, Double> fusedScores = new HashMap<>();

        BiConsumer<List<Map<String, String>>, Double> addRrf = (list, weight) -> {
            int rank = 1;
            for (Map<String, String> doc : list) 
            {
                String key = doc.get("title") + "|" + doc.get("body"); // unique key
                fusedScores.merge(key, weight / (60 + rank), Double::sum);
                rank++;
            }
        };

        addRrf.accept(textResults, alpha);
        addRrf.accept(vectorResults, 1.0 - alpha);

        // Sort by fused score descending
        List<Map.Entry<String, Double>> sorted = fusedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .collect(Collectors.toList());

        List<Map<String, String>> hybridResults = new ArrayList<>();
        Map<String, Map<String, String>> allDocs = new HashMap<>();
        for (Map<String, String> doc : textResults) 
        {
            allDocs.put(doc.get("title") + "|" + doc.get("body"), doc);
        }
        for (Map<String, String> doc : vectorResults) 
        {
            allDocs.put(doc.get("title") + "|" + doc.get("body"), doc);
        }
        for (Map.Entry<String, Double> entry : sorted) 
        {
            Map<String, String> doc = allDocs.get(entry.getKey());
            doc.put("score", String.format("%.4f", entry.getValue()));
            hybridResults.add(doc);
        }
        return hybridResults;
    }
}