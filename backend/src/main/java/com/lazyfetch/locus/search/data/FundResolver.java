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
            List<Map<String, Object>> allFunds = mfDataService.searchFundByName("");
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
        if (text == null || text.isBlank()) 
        {
            return List.of();
        }

        Set<Integer> found = new LinkedHashSet<>();
        String lower = text.toLowerCase();

        for (Map.Entry<String, Integer> entry : nameToSchemeCode.entrySet()) 
        {
            if (lower.contains(entry.getKey())) 
            {
                found.add(entry.getValue());
            }
        }

        return new ArrayList<>(found);
    }
}
