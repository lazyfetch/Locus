package com.lazyfetch.locus.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lazyfetch.locus.search.context.*;
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
    private final ContextBudgetAllocator budgetAllocator;
    private final ContextCompressor contextCompressor;
    private final ContextAssembler contextAssembler;

    
    public EvaluationService(HybridSearchService hybridSearchService,
                             ContextBudgetAllocator budgetAllocator,
                             ContextCompressor contextCompressor,
                             ContextAssembler contextAssembler) {
        this.hybridSearchService = hybridSearchService;
        this.budgetAllocator = budgetAllocator;
        this.contextCompressor = contextCompressor;
        this.contextAssembler = contextAssembler;
    }

    public List<EvalQuery> loadQueries() throws Exception {
        return loadQueries("eval_queries.json");
    }

    public List<EvalQuery> loadQueries(String resource) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resource);
        return mapper.readValue(is, new TypeReference<List<EvalQuery>>() {});
    }

    public EvaluationReport evaluate() throws Exception {
        return evaluate(true);
    }

    public EvaluationReport evaluate(boolean useLucene) throws Exception {
        List<EvalQuery> queries = loadQueries();
        List<EvalResult> results = new ArrayList<>();

        double totalPrecision = 0, totalRecall = 0, totalPrecisionAt1 = 0;
        int intentCorrect = 0, metricsCorrect = 0;
        long totalLatency = 0;
        int totalTokens = 0;
        int applicableCount = 0, relevantCount = 0;
        int factApplicableCount = 0, factSufficientCount = 0;

        for (EvalQuery q : queries) {
            long start = System.currentTimeMillis();
            HybridSearchResponse response = hybridSearchService.hybridSearch(q.getQuery(), 10, useLucene);
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
                            c.getOrDefault("chunk_text", ""))))
                    .collect(Collectors.joining(" "))
                    .toLowerCase();
                chunkRelevant = expectedKeywords.stream()
                    .anyMatch(k -> chunkText.contains(k.toLowerCase()));
                applicableCount++;
                if (chunkRelevant) relevantCount++;
            }

           
            boolean hasData = !response.getStructured().isEmpty();
            boolean hasChunks = !response.getUnstructured().isEmpty();
            BudgetAllocation alloc = budgetAllocator.allocate(plan.getIntent(), false, hasData, hasChunks);
            List<Map<String,Object>> cData = contextCompressor.compressStructured(
                    response.getStructured(), alloc.getDataTokens());
            List<Map<String,Object>> cChunks = contextCompressor.compressChunks(
                    response.getUnstructured(), alloc.getChunkTokens());
            String contextText = contextAssembler.assemble(cData, cChunks, null, q.getQuery())
                    .toLowerCase();

            boolean contextSufficient = true;
            if (q.getExpectedFacts() != null && !q.getExpectedFacts().isEmpty()) {
                contextSufficient = q.getExpectedFacts().stream()
                    .allMatch(f -> contextText.contains(f.toLowerCase()));
                factApplicableCount++;
                if (contextSufficient) factSufficientCount++;
            }

            results.add(new EvalResult(q.getQuery(), precision, recall, intentMatch, metricsMatch,
                           chunkRelevant, latency, tokens,
                           q.getDifficulty(), q.getCategory(), contextSufficient));

            totalPrecision += precision;
            totalRecall += recall;
            totalPrecisionAt1 += precisionAt1;
            if (intentMatch) intentCorrect++;
            if (metricsMatch) metricsCorrect++;
            totalLatency += latency;
            totalTokens += tokens;
        }

        int n = queries.size();

        Map<String, List<EvalResult>> byCategory = results.stream()
            .filter(r -> r.getCategory() != null)
            .collect(Collectors.groupingBy(EvalResult::getCategory));
        Map<String, Double> precisionByCategory = new HashMap<>();
        for (var e : byCategory.entrySet()) {
            precisionByCategory.put(e.getKey(), e.getValue().stream()
                .mapToDouble(EvalResult::getPrecision).average().orElse(0));
        }

        Map<String, List<EvalResult>> byDifficulty = results.stream()
            .filter(r -> r.getDifficulty() != null)
            .collect(Collectors.groupingBy(EvalResult::getDifficulty));
        Map<String, Double> recallByDifficulty = new HashMap<>();
        for (var e : byDifficulty.entrySet()) {
            recallByDifficulty.put(e.getKey(), e.getValue().stream()
                .mapToDouble(EvalResult::getRecall).average().orElse(0));
        }

        double chunkRelevanceRate = applicableCount == 0 ? 0.0
            : relevantCount * 100.0 / applicableCount;
        double contextSufficiencyRate = factApplicableCount == 0 ? 0.0
            : factSufficientCount * 100.0 / factApplicableCount;

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
            chunkRelevanceRate,
            contextSufficiencyRate    
        );
    }

    private double computePrecision(List<Integer> retrieved, List<Integer> expected) {
        if (retrieved.isEmpty() || expected.isEmpty()) return 0;
        return (double) retrieved.stream().filter(expected::contains).count() / retrieved.size();
    }
    private double computeRecall(List<Integer> retrieved, List<Integer> expected) {
        if (retrieved.isEmpty() || expected.isEmpty()) return 0;
        return (double) retrieved.stream().filter(expected::contains).count() / expected.size();
    }
    private double computePrecisionAt1(List<Integer> retrieved, List<Integer> expected) {
        if (retrieved.isEmpty() || expected.isEmpty()) return 0;
        return expected.contains(retrieved.get(0)) ? 1.0 : 0.0;
    }
}
