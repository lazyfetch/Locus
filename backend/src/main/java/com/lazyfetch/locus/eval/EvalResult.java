package com.lazyfetch.locus.eval;

public class EvalResult {
    private String query;
    private double precision;      // How many expected funds were found / total found
    private double recall;         // How many expected funds were found / total expected
    private boolean intentMatch;   // Did intent match?
    private boolean metricsMatch;  // Did metrics match?
    private long latencyMs;        // How long the query took
    private int tokensUsed;        // How many tokens were used
    private String difficulty;
    private String category;

 
    public EvalResult(String query, double precision, double recall, 
                       boolean intentMatch, boolean metricsMatch, 
                       long latencyMs, int tokensUsed,
                       String difficulty, String category) {
        this.query = query;
        this.precision = precision;
        this.recall = recall;
        this.intentMatch = intentMatch;
        this.metricsMatch = metricsMatch;
        this.latencyMs = latencyMs;
        this.tokensUsed = tokensUsed;
        this.difficulty = difficulty;
        this.category = category;
    }

    public String getQuery() { return query; }
    public double getPrecision() { return precision; }
    public double getRecall() { return recall; }
    public boolean isIntentMatch() { return intentMatch; }
    public boolean isMetricsMatch() { return metricsMatch; }
    public long getLatencyMs() { return latencyMs; }
    public int getTokensUsed() { return tokensUsed; }
    public String getDifficulty() { return difficulty; }
    public String getCategory() { return category; }
}