package com.lazyfetch.locus.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lazyfetch.locus.search.dto.HybridSearchResponse;
import com.lazyfetch.locus.search.hybrid.HybridSearchService;
import com.lazyfetch.locus.search.planner.RetrievalPlan;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvaluationService {

    private final HybridSearchService hybridSearchService;
    private final ObjectMapper mapper = new ObjectMapper();

    public EvaluationService(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    public List<EvalQuery> loadQueries() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("eval_queries.json");
        return mapper.readValue(is, new TypeReference<List<EvalQuery>>() {});
    }

    public EvaluationReport evaluate() throws Exception {
        List<EvalQuery> queries = loadQueries();
        List<EvalResult> results = new ArrayList<>();
        
        double totalPrecision = 0;
        double totalRecall = 0;
        int intentCorrect = 0;
        int metricsCorrect = 0;
        long totalLatency = 0;
        int totalTokens = 0;
        double totalPrecisionAt1 = 0;

        int applicableCount = 0;
        int relevantCount = 0;

        for (EvalQuery q : queries) {
            long start = System.currentTimeMillis();
            HybridSearchResponse response = hybridSearchService.hybridSearch(q.getQuery(), 10);
            long latency = System.currentTimeMillis() - start;
            
            RetrievalPlan plan = response.getPlan();
            
            List<Integer> retrievedCodes = plan.getSchemeCodes();
            double precision = computePrecision(retrievedCodes, q.getExpectedFundCodes());
            double recall = computeRecall(retrievedCodes, q.getExpectedFundCodes());
            double precisionAt1 = computePrecisionAt1(retrievedCodes, q.getExpectedFundCodes());
            
            boolean intentMatch = q.getExpectedIntent() != null 
                && q.getExpectedIntent().equals(plan.getIntent());
            
            boolean metricsMatch = q.getExpectedMetrics() != null
                && plan.getMetricTypes() != null
                && plan.getMetricTypes().containsAll(q.getExpectedMetrics());
            
            int tokens = response.getStructured().size() * 20 
                       + response.getUnstructured().size() * 50;
            
            List<String> expectedKeywords = q.getExpectedKeywords();  
            boolean chunkRelevant = true;  

            if (expectedKeywords != null && !expectedKeywords.isEmpty()) {
                String chunkText = response.getUnstructured().stream()
                    .map(c -> String.valueOf(c.getOrDefault("chunk_text_full", 
                    c.getOrDefault("chunk_text", ""))))   // prefer full, fall back
                    .collect(Collectors.joining(" "))
                    .toLowerCase();

                chunkRelevant = expectedKeywords.stream()
                    .anyMatch(k -> chunkText.contains(k.toLowerCase()));

                applicableCount++;
                if (chunkRelevant) relevantCount++;
            }

            results.add(new EvalResult(q.getQuery(), precision, recall, intentMatch, metricsMatch, 
                           chunkRelevant, latency, tokens,
                           q.getDifficulty(), q.getCategory()));
            
            totalPrecision += precision;
            totalRecall += recall;
            totalPrecisionAt1 += precisionAt1;
            if (intentMatch) intentCorrect++;
            if (metricsMatch) metricsCorrect++;
            totalLatency += latency;
            totalTokens += tokens;
        }

        int n = queries.size();
        
        // Compute category breakdowns
        Map<String, List<EvalResult>> byCategory = results.stream()
            .filter(r -> r.getCategory() != null)
            .collect(Collectors.groupingBy(EvalResult::getCategory));

        Map<String, Double> precisionByCategory = new HashMap<>();
        for (var entry : byCategory.entrySet()) {
            double avg = entry.getValue().stream()
                .mapToDouble(EvalResult::getPrecision).average().orElse(0);
            precisionByCategory.put(entry.getKey(), avg);
        }

        Map<String, List<EvalResult>> byDifficulty = results.stream()
            .filter(r -> r.getDifficulty() != null)
            .collect(Collectors.groupingBy(EvalResult::getDifficulty));

        Map<String, Double> recallByDifficulty = new HashMap<>();
        for (var entry : byDifficulty.entrySet()) {
            double avg = entry.getValue().stream()
                .mapToDouble(EvalResult::getRecall).average().orElse(0);
            recallByDifficulty.put(entry.getKey(), avg);
        }
        
        double chunkRelevanceRate = applicableCount == 0 ? 0.0
            : relevantCount * 100.0 / applicableCount;

        return new EvaluationReport(
            totalPrecision / n,
            totalRecall / n,
            (double) intentCorrect / n * 100,
            (double) metricsCorrect / n * 100,
            totalLatency / n,
            totalTokens,
            results,
            precisionByCategory,
            recallByDifficulty,
            totalPrecisionAt1 / n,
            chunkRelevanceRate
        );
    }

    private double computePrecision(List<Integer> retrieved, List<Integer> expected) {
        if (retrieved.isEmpty() || expected.isEmpty()) return 0;
        long correct = retrieved.stream().filter(expected::contains).count();
        return (double) correct / retrieved.size();
    }

    private double computeRecall(List<Integer> retrieved, List<Integer> expected) {
        if (retrieved.isEmpty() || expected.isEmpty()) return 0;
        long correct = retrieved.stream().filter(expected::contains).count();
        return (double) correct / expected.size();
    }

    private double computePrecisionAt1(List<Integer> retrieved, List<Integer> expected) {
        if (retrieved.isEmpty() || expected.isEmpty()) return 0;
        return expected.contains(retrieved.get(0)) ? 1.0 : 0.0;
    }
}
