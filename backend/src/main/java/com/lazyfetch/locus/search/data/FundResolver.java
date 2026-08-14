package com.lazyfetch.locus.search.data;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FundResolver 
{

    private static final Logger logger = LoggerFactory.getLogger(FundResolver.class);

    private final MfDataService mfDataService;
    private final Map<String, Integer> nameToSchemeCode = new HashMap<>();

    public FundResolver(MfDataService mfDataService) 
    {
        this.mfDataService = mfDataService;
    }

    @PostConstruct
    public void init() {
        nameToSchemeCode.clear();
        try {
            List<Map<String, Object>> allFunds = mfDataService.getAllFunds();
            for (Map<String, Object> fund : allFunds) 
            {
                String name = (String) fund.get("scheme_name");
                Integer code = (Integer) fund.get("scheme_code");
                if (name != null && code != null) 
                {
                    nameToSchemeCode.put(name.toLowerCase(), code);
                }
            }
            logger.info("FundResolver loaded {} funds", nameToSchemeCode.size());
        } 
        catch (Exception e) 
        {
            logger.warn("FundResolver init failed: {}", e.getMessage());
        }
    }

    
    public List<Integer> resolveFunds(String text) 
    {
        if (text == null || text.isBlank()) return List.of();
        String lower = text.toLowerCase();
        Map<String, Integer> byCoreName = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : nameToSchemeCode.entrySet()) 
        {
            if (isFundReferenced(entry.getKey(), lower)) 
            {
                String core = entry.getKey().split("\\s*-\\s*")[0].trim();
                byCoreName.putIfAbsent(core, entry.getValue());
            }
        }
        return new ArrayList<>(byCoreName.values());
    }

    private static final Set<String> SKIP_WORDS = Set.of(
        "-", "–", "fund", "plan", "direct", "regular", "growth", "idcw",
        "option", "the", "a", "an", "of", "in", "for", "and", "reinvestment"
    );

    private boolean isFundReferenced(String fundName, String queryLower) 
    {
        String coreName = fundName.split("\\s*-\\s*")[0].trim();
        String[] fundWords = coreName.split("\\s+");

        List<String> meaningful = new ArrayList<>();
        for (String w : fundWords) {
            String word = w.trim().toLowerCase();
            if (!word.isEmpty() && !SKIP_WORDS.contains(word)) {
                meaningful.add(word);
            }
        }
        if (meaningful.isEmpty()) return false;

        String fundHouse = meaningful.get(0);
        if (!queryLower.contains(fundHouse)) {
            return false;
        }

        int matched = 0;
        for (String word : meaningful) {
            if (queryLower.contains(word)) matched++;
        }

        double ratio = (double) matched / meaningful.size();
        return matched >= 2 && ratio >= 0.85;
    }

    public List<Integer> resolveFundEntity(String entityText) {
        return resolveFunds(entityText);
    }

    public Map<String, Integer> getAllFundsMap() {
        return nameToSchemeCode;
    }
}
