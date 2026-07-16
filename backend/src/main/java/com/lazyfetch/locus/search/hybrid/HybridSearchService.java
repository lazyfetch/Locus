package com.lazyfetch.locus.search.hybrid;

import com.lazyfetch.locus.search.data.MfDataService;
import com.lazyfetch.locus.search.dto.HybridSearchResponse;
import com.lazyfetch.locus.search.pgvector.PgVectorService;
import com.lazyfetch.locus.search.planner.MfQueryPlanner;
import com.lazyfetch.locus.search.planner.RetrievalPlan;
import com.lazyfetch.locus.records.VectorSearchResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class HybridSearchService {

    private final MfQueryPlanner queryPlanner;
    private final MfDataService mfDataService;
    private final PgVectorService pgVectorService;

    public HybridSearchService(
            MfQueryPlanner queryPlanner,
            MfDataService mfDataService,
            PgVectorService pgVectorService) {
        this.queryPlanner = queryPlanner;
        this.mfDataService = mfDataService;
        this.pgVectorService = pgVectorService;
    }

    public HybridSearchResponse hybridSearch(String query, int topK) throws Exception {
        RetrievalPlan plan = queryPlanner.plan(query);
        List<Integer> schemeCodes = plan.getSchemeCodes();
        String intent = plan.getIntent();
        List<String> metrics = plan.getMetricTypes();

        CompletableFuture<List<Map<String, Object>>> structuredFuture =
            CompletableFuture.supplyAsync(() -> {
                List<Map<String, Object>> results = new ArrayList<>();
                
                if (schemeCodes.isEmpty()) return results;
                
                for (Integer code : schemeCodes) {
                    Map<String, Object> details = mfDataService.getFundDetails(code);
                    if (details != null) results.add(details);
                }
                
                results.addAll(mfDataService.getReturns(schemeCodes));
                
                // Top holdings
                for (Integer code : schemeCodes) {
                    List<Map<String, Object>> holdings = mfDataService.getTopHoldings(code, 10);
                    for (Map<String, Object> h : holdings) {
                        h.put("scheme_code", code);
                        results.add(h);
                    }
                }
                
                // NAV 
                if (metrics.contains("nav") || intent.equals("NAV")) {
                    for (Integer code : schemeCodes) {
                        Map<String, Object> nav = mfDataService.getLatestNav(code);
                        if (nav != null) {
                            nav.put("scheme_code", code);
                            results.add(nav);
                        }
                    }
                }
                
                return results;
            });

        CompletableFuture<List<Map<String, Object>>> vectorFuture =
            CompletableFuture.supplyAsync(() -> {
                try {
                    List<VectorSearchResult> results = pgVectorService.search(query, topK * 2, 
                        schemeCodes.isEmpty() ? null : schemeCodes);
                    
                    return results.stream().map(r -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", r.id());
                        map.put("scheme_code", r.schemeCode());
                        map.put("section_type", r.sectionType());
                        String text = r.chunkText();
                        map.put("chunk_text", text.length() > 300 ? text.substring(0, 300) + "..." : text);
                        map.put("distance", r.distance());
                        map.put("similarity", 1.0 - (r.distance() / 2.0));
                        return map;
                    }).collect(Collectors.toList());
                    
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

        List<Map<String, Object>> structuredResults = structuredFuture.get();
        List<Map<String, Object>> vectorResults = vectorFuture.get();

        return new HybridSearchResponse(plan, structuredResults, vectorResults);
    }

    public HybridSearchResponse hybridSearch(String query, int topK, List<Integer> previousCodes) throws Exception 
    {
        RetrievalPlan plan = queryPlanner.plan(query, previousCodes);
        List<Integer> schemeCodes = plan.getSchemeCodes();
        String intent = plan.getIntent();
        List<String> metrics = plan.getMetricTypes();

        CompletableFuture<List<Map<String, Object>>> structuredFuture =
            CompletableFuture.supplyAsync(() -> {
                List<Map<String, Object>> results = new ArrayList<>();
                
                if (schemeCodes.isEmpty()) return results;
                
                for (Integer code : schemeCodes) {
                    Map<String, Object> details = mfDataService.getFundDetails(code);
                    if (details != null) results.add(details);
                }
                
                results.addAll(mfDataService.getReturns(schemeCodes));
                
                // Top holdings
                for (Integer code : schemeCodes) {
                    List<Map<String, Object>> holdings = mfDataService.getTopHoldings(code, 10);
                    for (Map<String, Object> h : holdings) {
                        h.put("scheme_code", code);
                        results.add(h);
                    }
                }
                
                // NAV 
                if (metrics.contains("nav") || intent.equals("NAV")) {
                    for (Integer code : schemeCodes) {
                        Map<String, Object> nav = mfDataService.getLatestNav(code);
                        if (nav != null) {
                            nav.put("scheme_code", code);
                            results.add(nav);
                        }
                    }
                }
                
                return results;
            });

        CompletableFuture<List<Map<String, Object>>> vectorFuture =
            CompletableFuture.supplyAsync(() -> {
                try {
                    List<VectorSearchResult> results = pgVectorService.search(query, topK * 2, 
                        schemeCodes.isEmpty() ? null : schemeCodes);
                    
                    return results.stream().map(r -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", r.id());
                        map.put("scheme_code", r.schemeCode());
                        map.put("section_type", r.sectionType());
                        String text = r.chunkText();
                        map.put("chunk_text", text.length() > 300 ? text.substring(0, 300) + "..." : text);
                        map.put("distance", r.distance());
                        map.put("similarity", 1.0 - (r.distance() / 2.0));
                        return map;
                    }).collect(Collectors.toList());
                    
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

        List<Map<String, Object>> structuredResults = structuredFuture.get();
        List<Map<String, Object>> vectorResults = vectorFuture.get();

        return new HybridSearchResponse(plan, structuredResults, vectorResults);
    }
}