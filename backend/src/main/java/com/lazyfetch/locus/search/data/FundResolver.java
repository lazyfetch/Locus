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
        if (text == null || text.isBlank()) 
        {
            return List.of();
        }

        Set<Integer> found = new LinkedHashSet<>();
        String lower = text.toLowerCase();

        for (Map.Entry<String, Integer> entry : nameToSchemeCode.entrySet()) 
        {
            String fundName = entry.getKey();  
            if (isFundReferenced(fundName, lower)) 
            {
                found.add(entry.getValue());
            }
        }

        return new ArrayList<>(found);
    }

    private static final Set<String> SKIP_WORDS = Set.of(
        "-", "–", "fund", "plan", "direct", "regular", "growth", "idcw",
        "option", "the", "a", "an", "of", "in", "for", "and", "reinvestment"
    );

    private boolean isFundReferenced(String fundName, String queryLower) 
    {
        String coreName = fundName.split("\\s*-\\s*")[0].trim();
        String[] fundWords = coreName.split("\\s+");
        
        String firstWord = null;
        for (String w : fundWords) {
            if (!SKIP_WORDS.contains(w.trim().toLowerCase())) {
                firstWord = w.trim().toLowerCase();
                break;
            }
        }
        if (firstWord == null) return false;
        
        for (int startIdx = 0; startIdx < fundWords.length; startIdx++) 
        {
            StringBuilder consecutive = new StringBuilder();
            int meaningfulWords = 0;
            
            for (int i = startIdx; i < fundWords.length; i++) 
            {
                String word = fundWords[i].trim().toLowerCase();
                if (word.isEmpty() || SKIP_WORDS.contains(word)) continue;
                
                if (consecutive.length() > 0) consecutive.append(" ");
                consecutive.append(word);
                meaningfulWords++;
                
                if (meaningfulWords >= 2 
                    && consecutive.toString().contains(firstWord)
                    && queryLower.contains(consecutive.toString())) 
                {
                    return true;
                }
            }
        }
        
        return false;
    }
}
