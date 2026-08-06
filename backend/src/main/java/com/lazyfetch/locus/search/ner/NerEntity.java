package com.lazyfetch.locus.search.ner;

public record NerEntity(
    String text,
    String label,       // FUND, METRIC, PERIOD, SECTOR, INDEX
    double confidence,
    int start,         
    int end
) {}