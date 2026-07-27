package com.lazyfetch.locus.eval;

import java.util.List;
import java.util.Map;

public class EvaluationReport {
    private final double avgPrecision;
    private final double avgRecall;
    private final double intentAccuracy;
    private final double metricsAccuracy;
    private final long avgLatencyMs;
    private final int totalTokensUsed;
    private final List<EvalResult> results;
    private final Map<String, Double> precisionByCategory;
    private final Map<String, Double> recallByDifficulty;

    public EvaluationReport(double avgPrecision, double avgRecall, double intentAccuracy, double metricsAccuracy,
                             long avgLatencyMs, int totalTokensUsed,
                             List<EvalResult> results,
                             Map<String, Double> precisionByCategory,
                             Map<String, Double> recallByDifficulty) {
        this.avgPrecision = avgPrecision;
        this.avgRecall = avgRecall;
        this.intentAccuracy = intentAccuracy;
        this.metricsAccuracy = metricsAccuracy;
        this.avgLatencyMs = avgLatencyMs;
        this.totalTokensUsed = totalTokensUsed;
        this.results = results;
        this.precisionByCategory = precisionByCategory;
        this.recallByDifficulty = recallByDifficulty;
    }


    public double getAvgPrecision() { return avgPrecision; }
    public double getAvgRecall() { return avgRecall; }
    public double getIntentAccuracy() { return intentAccuracy; }
    public double getMetricsAccuracy() { return metricsAccuracy; }
    public long getAvgLatencyMs() { return avgLatencyMs; }
    public int getTotalTokensUsed() { return totalTokensUsed; }
    public List<EvalResult> getResults() { return results; }
    public Map<String, Double> getPrecisionByCategory() { return precisionByCategory; }
    public Map<String, Double> getRecallByDifficulty() { return recallByDifficulty; }
}
