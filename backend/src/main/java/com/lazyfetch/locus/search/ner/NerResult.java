package com.lazyfetch.locus.search.ner;

import java.util.List;

public record NerResult(
    List<NerEntity> entities,
    double confidence
) {}