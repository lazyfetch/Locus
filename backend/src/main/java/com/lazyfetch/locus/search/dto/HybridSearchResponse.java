package com.lazyfetch.locus.search.dto;

import com.lazyfetch.locus.search.planner.RetrievalPlan;
import java.util.List;
import java.util.Map;

public class HybridSearchResponse {
    private final RetrievalPlan plan;
    private final List<Map<String, Object>> structured;
    private final List<Map<String, Object>> unstructured;

    public HybridSearchResponse(
            RetrievalPlan plan,
            List<Map<String, Object>> structured,
            List<Map<String, Object>> unstructured) {
        this.plan = plan;
        this.structured = structured;
        this.unstructured = unstructured;
    }

    public RetrievalPlan getPlan() { return plan; }
    public List<Map<String, Object>> getStructured() { return structured; }
    public List<Map<String, Object>> getUnstructured() { return unstructured; }
}
