package com.lazyfetch.locus.search.planner;

import com.lazyfetch.locus.search.data.FundResolver;
import com.lazyfetch.locus.search.ner.NerService;
import com.lazyfetch.locus.search.ner.NerResult;
import com.lazyfetch.locus.search.ner.NerEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import com.lazyfetch.locus.search.data.EntityLinker;

@Service
public class MfQueryPlanner 
{
    private static final Pattern RETURN_PATTERN = 
        Pattern.compile("(?i)\\b(return[s]?|performance|perform(s|ed|ing)?|how did|how has|how is|how's|growth|grew|gave|gain(s|ed)?|rose|fared|doing|do|track record|past performance)\\b");
    private static final Pattern HOLDING_PATTERN = 
        Pattern.compile("(?i)\\b(holding[s]?|hold(s|ing)?|stocks|invest(s|ed|ing)?\\s+in|invest(s|ed|ing)?|portfolio|top\\s*10|top holdings|sector allocation|composition)\\b");
    private static final Pattern NAV_PATTERN = 
        Pattern.compile("(?i)\\b(nav|price|value today|latest nav|worth|valued|current value|today's nav)\\b");
    private static final Pattern COMPARE_PATTERN = 
        Pattern.compile("(?i)\\b(compare|comparison|comparing|vs|versus|difference|better|which|between|outperform|beat)\\b");
    private static final Pattern FUND_FACTS_PATTERN = 
        Pattern.compile("(?i)\\b(fund manager|expense ratio|exit load|aum|benchmark|risk ratio|sharpe|alpha|beta|sip|minimum)\\b");

    private final FundResolver fundResolver;
    private final NerService nerService;
    private final EntityLinker entityLinker;  // inject

    public MfQueryPlanner(FundResolver fundResolver, NerService nerService, EntityLinker entityLinker) {
        this.fundResolver = fundResolver;
        this.nerService = nerService;
        this.entityLinker = entityLinker;
    }

    public RetrievalPlan plan(String rawQuery) {
        RetrievalPlan plan = new RetrievalPlan(rawQuery);
        List<Integer> schemeCodes = new ArrayList<>();
        List<String> metrics = new ArrayList<>();

        // Layer 1: FundResolver 
        schemeCodes.addAll(fundResolver.resolveFunds(rawQuery));

        // Layer 2: NER entities 
        NerResult ner = nerService.extractEntities(rawQuery);
        for (NerEntity e : ner.entities()) {
            if (e.label().equals("FUND")) {
                schemeCodes.addAll(entityLinker.link(e.text()));  // ← use linker
            } else if (e.label().equals("METRIC")) {
                metrics.add(normalizeMetric(e.text()));
            }
        }

        // Layer 3: Rules/regex for intent
        List<String> regexMetrics = detectMetrics(rawQuery);
        for (String m : regexMetrics) {
            if (!metrics.contains(m)) metrics.add(m);
        }

        plan.setSchemeCodes(dedupe(schemeCodes));
        plan.setMetricTypes(dedupe(metrics));
        plan.setIntent(detectIntent(rawQuery, ner));
        return plan;
    }

    public RetrievalPlan plan(String rawQuery, List<Integer> previousSchemeCodes) 
    {
        RetrievalPlan plan = plan(rawQuery);
        List<Integer> schemeCodes = new ArrayList<>(plan.getSchemeCodes());
        if (previousSchemeCodes != null) {
            for (Integer code : previousSchemeCodes) {
                if (!schemeCodes.contains(code)) {
                    schemeCodes.add(code);
                }
            }
        }
        plan.setSchemeCodes(schemeCodes);
        return plan;
    }

    private String detectIntent(String query) 
    {
        if (COMPARE_PATTERN.matcher(query).find()) return "COMPARE_FUNDS";
        if (HOLDING_PATTERN.matcher(query).find()) return "HOLDINGS";
        if (RETURN_PATTERN.matcher(query).find()) return "FUND_DETAILS";
        if (NAV_PATTERN.matcher(query).find()) return "NAV";
        if (FUND_FACTS_PATTERN.matcher(query).find()) return "FUND_DETAILS";
        return "GENERAL";
    }

    private String detectIntent(String query, NerResult ner) {
        Set<String> nerMetrics = new LinkedHashSet<>();
        for (NerEntity e : ner.entities()) {
            if (e.label().equals("METRIC")) {
                nerMetrics.add(normalizeMetric(e.text()));
            }
        }

        if (nerMetrics.contains("returns")) return "FUND_DETAILS";
        if (nerMetrics.contains("holdings")) return "HOLDINGS";
        if (nerMetrics.contains("nav")) return "NAV";

        if (COMPARE_PATTERN.matcher(query).find()) return "COMPARE_FUNDS";

        if (HOLDING_PATTERN.matcher(query).find()) return "HOLDINGS";
        if (RETURN_PATTERN.matcher(query).find()) return "FUND_DETAILS";
        if (NAV_PATTERN.matcher(query).find()) return "NAV";
        if (FUND_FACTS_PATTERN.matcher(query).find()) return "FUND_DETAILS";
        return "GENERAL";
    }

    private List<String> detectMetrics(String query)
    {
        List<String> metrics = new ArrayList<>();
        if (RETURN_PATTERN.matcher(query).find()) metrics.add("returns");
        if (HOLDING_PATTERN.matcher(query).find()) metrics.add("holdings");
        if (NAV_PATTERN.matcher(query).find()) metrics.add("nav");
        return metrics;
    }

    private String normalizeMetric(String raw) {
        String m = raw.toLowerCase();
        if (m.equals("return") || m.equals("returns") || m.equals("performance")
            || m.equals("perform") || m.equals("growth")) return "returns";
        if (m.equals("holding") || m.equals("holdings")) return "holdings";
        if (m.equals("nav")) return "nav";
        return m;
    }

    private <T> List<T> dedupe(List<T> list) {
        Set<T> set = new LinkedHashSet<>(list);
        return new ArrayList<>(set);
    }
}
