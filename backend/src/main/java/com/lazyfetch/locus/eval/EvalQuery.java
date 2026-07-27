package com.lazyfetch.locus.eval;

import java.util.List;

public class EvalQuery {
    private String query;
    private List<Integer> expectedFundCodes;
    private String expectedIntent;
    private List<String> expectedMetrics;
    private String difficulty;  // "easy", "medium", "hard"
    private String category;    // "fund_lookup", "comparison", "follow_up", "edge_case"

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public List<Integer> getExpectedFundCodes() { return expectedFundCodes; }
    public void setExpectedFundCodes(List<Integer> codes) { this.expectedFundCodes = codes; }
    public String getExpectedIntent() { return expectedIntent; }
    public void setExpectedIntent(String intent) { this.expectedIntent = intent; }
    public List<String> getExpectedMetrics() { return expectedMetrics; }
    public void setExpectedMetrics(List<String> metrics) { this.expectedMetrics = metrics; }
}