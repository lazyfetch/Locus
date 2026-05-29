package com.lazyfetch.locus;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
        List<String> tickers = plan.getTickers();

        var textFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return textSearchService.search(query, topK * 2, tickers);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        var vectorFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return vectorSearchService.vectorSearch(query, topK * 2, tickers);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<List<Map<String, Object>>> structuredFuture =
            CompletableFuture.supplyAsync(() -> {
                if (tickers != null && !tickers.isEmpty()) {
                    return structuredDataService.queryMetricsForTickers(
                        tickers,
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

        final Map<String, Double> fusedScores = new HashMap<>();
        double textWeight = alpha;
        double vectorWeight = 1 - alpha;

        addRrf(fusedScores, textResults, textWeight);
        addRrf(fusedScores, vectorResults, vectorWeight);

        Map<String, Map<String, String>> allDocs = new HashMap<>();
        for (Map<String, String> doc : textResults) {
            allDocs.put(doc.get("title") + "|" + doc.get("body"), doc);
        }
        for (Map<String, String> doc : vectorResults) {
            allDocs.put(doc.get("title") + "|" + doc.get("body"), doc);
        }

        List<Map.Entry<String, Double>> sorted = fusedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        int maxUnstructured = Math.min(topK, 3);
        if (plan.getMetrics() != null && !plan.getMetrics().isEmpty()) {
            maxUnstructured = Math.min(maxUnstructured, 2);
        }

        double minScore = 0.0;
        if (!sorted.isEmpty()) {
            double topScore = sorted.get(0).getValue();
            minScore = topScore * 0.60;
        }

        List<Map<String, String>> unstructuredResults = new ArrayList<>();
        for (Map.Entry<String, Double> entry : sorted) {
            if (unstructuredResults.size() >= maxUnstructured) {
                break;
            }
            if (entry.getValue() < minScore) {
                continue;
            }
            Map<String, String> doc = allDocs.get(entry.getKey());
            if (doc != null) {
                doc.put("score", String.format("%.4f", entry.getValue()));
                unstructuredResults.add(doc);
            }
        }

        if (unstructuredResults.isEmpty() && !sorted.isEmpty()) {
            Map<String, String> doc = allDocs.get(sorted.get(0).getKey());
            if (doc != null) {
                doc.put("score", String.format("%.4f", sorted.get(0).getValue()));
                unstructuredResults.add(doc);
            }
        }

        return new HybridSearchResponse(unstructuredResults, structuredRows);
    }

    private void addRrf(Map<String, Double> fusedScores, List<Map<String, String>> list, double weight) {
        int rank = 1;
        for (Map<String, String> doc : list) {
            String key = doc.get("title") + "|" + doc.get("body");
            fusedScores.merge(key, weight / (60 + rank), Double::sum);
            rank++;
        }
    }
}