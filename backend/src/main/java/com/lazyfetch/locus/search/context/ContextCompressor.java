package com.lazyfetch.locus.search.context;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;


@Service
public class ContextCompressor {


    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() / 4;
    }

    public List<Map<String, Object>> compress(List<Map<String, Object>> items, int budget, String textKey, int maxItems) 
    {
    
        if (items == null || items.isEmpty()) return new ArrayList<>();
        
        List<Map<String, Object>> result = new ArrayList<>();
        int tokensUsed = 0;
        
        for (Map<String, Object> item : items) 
        {
            if (result.size() >= maxItems) break;
            
            int itemTokens = estimateItemTokens(item, textKey);
            
            // If item fits within remaining budget do nothing
            if (tokensUsed + itemTokens <= budget) 
            {
                result.add(item);
                tokensUsed += itemTokens;
            } 

            // If item doesn't fit then truncate
            else if (tokensUsed < budget) 
            {
                int remaining = budget - tokensUsed;
                Map<String, Object> truncated = truncateItem(item, textKey, remaining);
                if (truncated != null)
                {
                    result.add(truncated);
                    tokensUsed = budget;  
                }
                break;
            } 
            else 
            {
                break;
            }
        }
        
        return result;
    }

    public List<Map<String, Object>> compressChunks(List<Map<String, Object>> chunks, int budget) {
        if (chunks == null || chunks.isEmpty()) return new ArrayList<>();
        
        // Sorting by similarity 
        List<Map<String, Object>> sorted = new ArrayList<>(chunks);
        sorted.sort((a, b) -> {
            double simA = (double) a.getOrDefault("similarity", 0.0);
            double simB = (double) b.getOrDefault("similarity", 0.0);
            return Double.compare(simB, simA);
        });
        
        return compress(sorted, budget, "chunk_text", 10);
    }

   // compresses structured data
    public List<Map<String, Object>> compressStructured(List<Map<String, Object>> data, int budget) 
    {
        return compress(data, budget, null, 20);
    }

    private int estimateItemTokens(Map<String, Object> item, String textKey) 
    {
        int tokens = 0;
        for (Map.Entry<String, Object> entry : item.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (key.equals("scheme_code") || key.equals("id") || key.equals("distance")) continue;
            
            if (value instanceof String) 
            {
                tokens += estimateTokens((String) value);
            }
        }
        return Math.max(tokens, 1);  
    }

    private Map<String, Object> truncateItem(Map<String, Object> item, String textKey, int maxTokens) 
    {
        if (textKey == null || !item.containsKey(textKey)) 
        {
            return null;
        }
        
        String text = (String) item.get(textKey);
        int maxChars = maxTokens * 4;  
        
        if (text.length() <= maxChars) 
        {
            return item;  
        }
        
        String truncated = text.substring(0, Math.min(maxChars, text.length()));
        int lastPeriod = truncated.lastIndexOf('.');
        if (lastPeriod > maxChars / 2) 
        {
            truncated = truncated.substring(0, lastPeriod + 1);
        } 
        else 
        {
            truncated += "...";
        }
        
        Map<String, Object> result = new HashMap<>(item);
        result.put(textKey, truncated);
        return result;
    }
}
