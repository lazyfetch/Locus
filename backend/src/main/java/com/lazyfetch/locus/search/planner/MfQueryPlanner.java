package com.lazyfetch.locus.search.planner;

import com.lazyfetch.locus.search.data.FundResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class MfQueryPlanner 
{

    private final FundResolver fundResolver;

    private static final Pattern RETURN_PATTERN = 
        Pattern.compile("(?i)\\b(return[s]?|performance|how did|growth|gave)\\b");
    private static final Pattern HOLDING_PATTERN = 
        Pattern.compile("(?i)\\b(holding[s]?|stocks|invested in|portfolio|top\\s*10)\\b");
    private static final Pattern NAV_PATTERN = 
        Pattern.compile("(?i)\\b(nav|price|value today|latest nav)\\b");
    private static final Pattern COMPARE_PATTERN = 
        Pattern.compile("(?i)\\b(compare|vs|versus|difference|better|which)\\b");

    public MfQueryPlanner(FundResolver fundResolver) 
    {
        this.fundResolver = fundResolver;
    }

    public RetrievalPlan plan(String rawQuery) 
    {
        RetrievalPlan plan = new RetrievalPlan(rawQuery);

        // 1. Find which funds are mentioned
        List<Integer> schemeCodes = fundResolver.resolveFunds(rawQuery);
        plan.setSchemeCodes(schemeCodes);

        // 2. Detect intent from keywords
        String intent = detectIntent(rawQuery);
        plan.setIntent(intent);

        // 3. Detect metric types
        List<String> metrics = detectMetrics(rawQuery);
        plan.setMetricTypes(metrics);

        return plan;
    }

    public RetrievalPlan plan(String rawQuery, List<Integer> previousSchemeCodes) 
    {
        RetrievalPlan plan = new RetrievalPlan(rawQuery);

        // 1. Find funds mentioned in current query
        List<Integer> schemeCodes = fundResolver.resolveFunds(rawQuery);
        
        // 2. ALSO include funds from previous turns (for follow-ups)
        if (previousSchemeCodes != null) 
        {
            for (Integer code : previousSchemeCodes) 
            {
                if (!schemeCodes.contains(code)) {
                    schemeCodes.add(code);
                }
            }
        }
        
        plan.setSchemeCodes(schemeCodes);

        // 3. Detect intent
        String intent = detectIntent(rawQuery);
        plan.setIntent(intent);

        // 4. Detect metric types
        List<String> metrics = detectMetrics(rawQuery);
        plan.setMetricTypes(metrics);

        return plan;
    }

    private String detectIntent(String query) 
    {
        if (COMPARE_PATTERN.matcher(query).find()) 
        {
            return "COMPARE_FUNDS";
        }
        if (HOLDING_PATTERN.matcher(query).find()) 
        {
            return "HOLDINGS";
        }
        if (RETURN_PATTERN.matcher(query).find()) 
        {
            return "FUND_DETAILS";
        }
        if (NAV_PATTERN.matcher(query).find()) 
        {
            return "NAV";
        }
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
}
