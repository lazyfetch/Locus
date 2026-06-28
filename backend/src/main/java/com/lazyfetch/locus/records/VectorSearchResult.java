package com.lazyfetch.locus.records;

public record VectorSearchResult(
    int id,
    int schemeCode,
    String sectionType,
    String chunkText,
    double distance   // lower = more similar
) {}
