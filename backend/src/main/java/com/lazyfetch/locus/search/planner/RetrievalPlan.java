package com.lazyfetch.locus.search.planner;

import java.util.List;

public class RetrievalPlan {
    private String textQuery;
    private List<Integer> schemeCodes;
    private String intent;       
    private List<String> metricTypes;  

    public RetrievalPlan(String textQuery) {
        this.textQuery = textQuery;
    }

    
    public String getTextQuery() { return textQuery; }
    public void setTextQuery(String textQuery) { this.textQuery = textQuery; }

    public List<Integer> getSchemeCodes() { return schemeCodes; }
    public void setSchemeCodes(List<Integer> schemeCodes) { this.schemeCodes = schemeCodes; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public List<String> getMetricTypes() { return metricTypes; }
    public void setMetricTypes(List<String> metricTypes) { this.metricTypes = metricTypes; }
}
