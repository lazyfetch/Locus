package com.lazyfetch.locus;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class RetrievalPlan {
    private String textQuery;
    private String ticker;
    private List<String> metrics;
    private LocalDate startDate;
    private LocalDate endDate;
}