package com.lazyfetch.locus;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
public class HybridSearchService {

    private final SearchEngineService textSearchService;
    private final VectorSearchService vectorSearchService;
    private final QueryPlanner queryPlanner;
    private final StructuredDataService structuredDataService;

    public HybridSearchService(
            SearchEngineService textSearchService,
            VectorSearchService vectorSearchService,
            QueryPlanner queryPlanner,
            StructuredDataService structuredDataService) {
        this.textSearchService = textSearchService;
        this.vectorSearchService = vectorSearchService;
        this.queryPlanner = queryPlanner;
        this.structuredDataService = structuredDataService;
    }

    public HybridSearchResponse hybridSearch(String query, int topK, double alpha) throws Exception {
        RetrievalPlan plan = queryPlanner.plan(query);

        var textFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return textSearchService.search(query, topK * 2);
            } catch (Exception e) {
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

        CompletableFuture<List<Map<String, Object>>> structuredFuture =
            CompletableFuture.supplyAsync(() -> {
                if (plan.getTicker() != null) {
                    return structuredDataService.queryMetrics(
                        plan.getTicker(),
                        plan.getMetrics(),
                        plan.getStartDate(),
                        plan.getEndDate()
                    );
                }
                return Collections.<Map<String, Object>>emptyList();
            });

        List<Map<String, String>> textResults = textFuture.get();
        List<Map<String, String>> vectorResults = vectorFuture.get();
        List<Map<String, Object>> structuredRows = structuredFuture.get();

        Map<String, Double> fusedScores = new HashMap<>();

        BiConsumer<List<Map<String, String>>, Double> addRrf = (list, weight) -> {
            int rank = 1;
            for (Map<String, String> doc : list) {
                String key = doc.get("title") + "|" + doc.get("body");
                fusedScores.merge(key, weight / (60 + rank), Double::sum);
                rank++;
            }
        };

        double textWeight = alpha * 0.5;
        double vectorWeight = alpha * 0.5;

        addRrf.accept(textResults, textWeight);
        addRrf.accept(vectorResults, vectorWeight);

        List<Map.Entry<String, Double>> sorted = fusedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .collect(Collectors.toList());

        Map<String, Map<String, String>> allDocs = new HashMap<>();
        for (Map<String, String> doc : textResults) {
            allDocs.put(doc.get("title") + "|" + doc.get("body"), doc);
        }
        for (Map<String, String> doc : vectorResults) {
            allDocs.put(doc.get("title") + "|" + doc.get("body"), doc);
        }

        List<Map<String, String>> unstructuredResults = new ArrayList<>();
        for (Map.Entry<String, Double> entry : sorted) {
            Map<String, String> doc = allDocs.get(entry.getKey());
            if (doc != null) {
                doc.put("score", String.format("%.4f", entry.getValue()));
                unstructuredResults.add(doc);
            }
        }

        return new HybridSearchResponse(unstructuredResults, structuredRows);
    }
}