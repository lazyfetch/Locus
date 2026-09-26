package com.lazyfetch.locus.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lazyfetch.locus.search.context.*;
import com.lazyfetch.locus.search.dto.HybridSearchResponse;
import com.lazyfetch.locus.search.hybrid.HybridSearchService;
import com.lazyfetch.locus.search.planner.RetrievalPlan;
import org.springframework.stereotype.Service;
import com.lazyfetch.locus.search.rag.RagService;
import com.lazyfetch.locus.search.tokens.TokenCounter;

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
    private final RagService ragService;
    private final TokenCounter tokenCounter;

    
    public EvaluationService(HybridSearchService hybridSearchService,
                             ContextBudgetAllocator budgetAllocator,
                             ContextCompressor contextCompressor,
                             ContextAssembler contextAssembler,
                             RagService ragService,
                             TokenCounter tokenCounter) {
        this.hybridSearchService = hybridSearchService;
        this.budgetAllocator = budgetAllocator;
        this.contextCompressor = contextCompressor;
        this.contextAssembler = contextAssembler;
        this.ragService = ragService;
        this.tokenCounter = tokenCounter;
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
                   q.getDifficulty(), q.getCategory(), contextSufficient,
                   false, 0.0));

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
            contextSufficiencyRate,
            0.0,   
            0.0    
        );
    }

    public EvaluationReport evaluateLlm() throws Exception {
        List<EvalQuery> queries = loadQueries("eval_queries_llm.json");
        List<EvalResult> results = new ArrayList<>();

        int groundedCount = 0, groundedApplicable = 0;
        double totalEfficiency = 0; int effCount = 0;

        for (EvalQuery q : queries) {
            long start = System.currentTimeMillis();
            Map<String, Object> res = ragService.ask(q.getQuery(), null);
            long latency = System.currentTimeMillis() - start;

            String answer = String.valueOf(res.getOrDefault("answer", ""));
            String ctx = String.valueOf(res.getOrDefault("contextText", "")).toLowerCase();

            Boolean grounded = null;
            if (q.getExpectedFacts() != null && !q.getExpectedFacts().isEmpty()) {
                grounded = q.getExpectedFacts().stream()
                    .allMatch(f -> answer.toLowerCase().contains(f.toLowerCase()));
                groundedApplicable++;
                if (grounded) groundedCount++;
            }

            int allocatedData = ((Number) res.getOrDefault("dataTokens", 0)).intValue();
            int allocatedChunk = ((Number) res.getOrDefault("chunkTokens", 0)).intValue();
            int allocated = allocatedData + allocatedChunk;
            int cited = 0;
            List<Map<String,Object>> sources = (List<Map<String,Object>>) res.getOrDefault("sources", List.of());
            Set<String> answerWords = Arrays.stream(answer.toLowerCase().split("\\W+"))
                .filter(w -> w.length() > 4).collect(Collectors.toSet());
            for (Map<String,Object> s : sources) {
                String text = String.valueOf(s.getOrDefault("chunk_text", ""));
                if (text.isEmpty()) continue;
                Set<String> srcWords = Arrays.stream(text.toLowerCase().split("\\W+"))
                    .filter(w -> w.length() > 4).collect(Collectors.toSet());
                if (srcWords.isEmpty()) continue;
                long overlap = srcWords.stream().filter(answerWords::contains).count();
                double ratio = (double) overlap / srcWords.size();
                if (ratio > 0.30) {                       
                    cited += tokenCounter.countForProvider(text);
                }
            }
            double efficiency = allocated == 0 ? 0.0 : Math.min(1.0, (double) cited / allocated);
            totalEfficiency += efficiency; effCount++;

            results.add(new EvalResult(q.getQuery(), 0, 0, false, false,
                       false, latency, 0, q.getDifficulty(), q.getCategory(),
                       false, grounded != null && grounded, efficiency));
        }

        int n = queries.size();
        double groundingRate = groundedApplicable == 0 ? 0.0
            : groundedCount * 100.0 / groundedApplicable;
        double avgEfficiency = effCount == 0 ? 0.0 : totalEfficiency / effCount;

        System.out.printf("Grounding: %d/%d applicable = %.1f%%%n",
    groundedCount, groundedApplicable, groundingRate);
        return new EvaluationReport(
            0, 0, 0, 0, 0, 0, results,
            Map.of(), Map.of(), 0, 0, 0,
            groundingRate, avgEfficiency);
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

    public static List<String> extractNumbers(String answer) {
        return answer.chars()
            .filter(Character::isDigit)
            .mapToObj(c -> String.valueOf(c))
            .collect(Collectors.toList());
    }

    public static boolean isGrounded(String answer, List<String> expectedFacts) {
        return expectedFacts == null || expectedFacts.isEmpty()
            || expectedFacts.stream().allMatch(f -> answer.contains(f));
    }
}
