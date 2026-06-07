package com.lazyfetch.locus.search.planner;

import org.springframework.stereotype.Service;

import com.lazyfetch.locus.data.company.StructuredDataService;
import com.lazyfetch.locus.data.company.TickerTagger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QueryPlanner {
    private static final Pattern TICKER_PATTERN = Pattern.compile("\\b[A-Z]{1,5}\\b");
    private static final Set<String> KNOWN_TICKERS = Set.of("TSLA", "F", "AAPL");   // fallback if DB empty
    private final TickerTagger tickerTagger;

    private static final Pattern METRIC_PATTERN = Pattern.compile(
            "(?i)\\b(P/E|PE|revenue|profit|ratio|earnings|eps|market cap)\\b");

    private final StructuredDataService structuredDataService;

    public QueryPlanner(StructuredDataService structuredDataService, TickerTagger tickerTagger) {
        this.structuredDataService = structuredDataService;
        this.tickerTagger = tickerTagger;
    }

    public RetrievalPlan plan(String rawQuery) {
        RetrievalPlan plan = new RetrievalPlan();
        plan.setTextQuery(rawQuery);

        //  1. Try TickerTagger (the best source)
        List<String> tickers = tickerTagger.extractTickers(rawQuery);

        // 2. Fallback: regex ticker + per‑word company database search
        if (tickers.isEmpty()) {
            List<String> fallbackTickers = new ArrayList<>();

            // 2a. known tickers via regex
            Matcher tickerMatcher = TICKER_PATTERN.matcher(rawQuery);
            if (tickerMatcher.find()) {
                String candidate = tickerMatcher.group();
                if (KNOWN_TICKERS.contains(candidate)) {
                    fallbackTickers.add(candidate);
                }
            }

            // 2b. per‑word company name lookup (collect ALL matches)
            String[] words = rawQuery.split("\\s+");
            for (String word : words) {
                if (word.length() > 1 && Character.isUpperCase(word.charAt(0))) {
                    List<Map<String, Object>> companies = structuredDataService.searchCompanies(word);
                    for (Map<String, Object> company : companies) {
                        String ticker = (String) company.get("ticker");
                        if (ticker != null && !fallbackTickers.contains(ticker)) {
                            fallbackTickers.add(ticker);
                        }
                    }
                }
            }

            tickers = fallbackTickers;
        }

        plan.setTickers(tickers);
        plan.setTicker(tickers.isEmpty() ? null : tickers.get(0));

        // 3. Metric extraction 
        Matcher metricMatcher = METRIC_PATTERN.matcher(rawQuery);
        List<String> metrics = new ArrayList<>();
        while (metricMatcher.find()) {
            metrics.add(normalizeMetric(metricMatcher.group()));
        }
        plan.setMetrics(metrics);

        plan.setStartDate(LocalDate.now().minusYears(5));
        plan.setEndDate(LocalDate.now());
        return plan;
    }

    private String normalizeMetric(String raw) {
        String m = raw.toLowerCase();
        if (m.equals("pe") || m.equals("p/e")) {
            return "p/e";
        }
        return m;
    }
}