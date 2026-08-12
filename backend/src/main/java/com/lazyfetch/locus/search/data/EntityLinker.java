package com.lazyfetch.locus.search.data;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EntityLinker {

    private final FundResolver fundResolver;
    private final Map<String, String> aliases = new HashMap<>();

    public EntityLinker(FundResolver fundResolver) {
        this.fundResolver = fundResolver;
        aliases.put("fc", "flexi cap");
        aliases.put("lc", "large cap");
        aliases.put("sc", "small cap");
        aliases.put("mc", "mid cap");
        aliases.put("fof", "fund of funds");
    }

    
    public List<Integer> link(String entityText) {
        if (entityText == null || entityText.isBlank()) return List.of();

        // expand aliases
        String expanded = expandAliases(entityText.toLowerCase());

        // trying direct resolution
        List<Integer> direct = fundResolver.resolveFundEntity(expanded);
        if (!direct.isEmpty()) return direct;


        return fuzzyMatch(expanded);
    }

    private String expandAliases(String text) {
        String result = text;
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            result = result.replaceAll("\\b" + e.getKey() + "\\b", e.getValue());
        }
        return result;
    }

    private List<Integer> fuzzyMatch(String text) {
        Map<String, Integer> funds = fundResolver.getAllFundsMap();
        String[] queryTokens = text.split("\\s+");

        List<ScoredFund> scored = new ArrayList<>();
        for (Map.Entry<String, Integer> f : funds.entrySet()) {
            String fundCore = f.getKey().split("\\s*-\\s*")[0].toLowerCase();
            double score = tokenSetSimilarity(queryTokens, fundCore.split("\\s+"));
            if (score >= 0.7) {  // threshold
                scored.add(new ScoredFund(f.getValue(), score));
            }
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));

        return scored.isEmpty() ? List.of() : List.of(scored.get(0).code);
    }

    private double tokenSetSimilarity(String[] queryTokens, String[] fundTokens) {
        int matches = 0;
        for (String qt : queryTokens) {
            for (String ft : fundTokens) {
                if (fuzzyEquals(qt, ft)) { matches++; break; }
            }
        }
        return (double) matches / queryTokens.length;
    }

    private boolean fuzzyEquals(String a, String b) {
        if (a.equals(b)) return true;
        if (Math.abs(a.length() - b.length()) > 2) return false;
        return levenshtein(a, b) <= 2;  // allow up to 2 edits
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i-1) == b.charAt(j-1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i-1][j] + 1, dp[i][j-1] + 1), dp[i-1][j-1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private record ScoredFund(int code, double score) {}
}
