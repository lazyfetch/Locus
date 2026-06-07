package com.lazyfetch.locus.search.dto;

import java.util.List;
import java.util.Map;

public class HybridSearchResponse {
    private final List<Map<String, String>> unstructured;
    private final List<Map<String, Object>> structured;

    public HybridSearchResponse(
            List<Map<String, String>> unstructured,
            List<Map<String, Object>> structured) {
        this.unstructured = unstructured;
        this.structured = structured;
    }

    public List<Map<String, String>> getUnstructured() {
        return unstructured;
    }

    public List<Map<String, Object>> getStructured() {
        return structured;
    }
}
