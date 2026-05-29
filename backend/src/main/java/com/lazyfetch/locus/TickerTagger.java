package com.lazyfetch.locus;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@DependsOn("dataSourceScriptDatabaseInitializer")
public class TickerTagger {
    private static final Logger logger = LoggerFactory.getLogger(TickerTagger.class);
    private static final Pattern TICKER_PATTERN = Pattern.compile("\\b[A-Z]{1,5}\\b");

    private final StructuredDataService structuredDataService;
    private final Set<String> knownTickers = new LinkedHashSet<>();
    private final Map<String, String> nameToTicker = new HashMap<>();

    public TickerTagger(StructuredDataService structuredDataService) {
        this.structuredDataService = structuredDataService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        knownTickers.clear();
        nameToTicker.clear();

        try {
            List<Map<String, Object>> companies = structuredDataService.listCompanies();
            for (Map<String, Object> row : companies) 
            {
                String ticker = row.get("ticker") != null ? row.get("ticker").toString().toUpperCase() : null;
                String name = row.get("name") != null ? row.get("name").toString().toLowerCase() : null;

                if (ticker != null && !ticker.isBlank()) {
                    knownTickers.add(ticker);
                }
                if (name != null && !name.isBlank() && ticker != null) {
                    nameToTicker.put(name, ticker);
                }
            }
        } catch (Exception e) {
            logger.warn("TickerTagger init skipped (company table not ready).", e);
        }
    }

    public List<String> extractTickers(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        Set<String> found = new LinkedHashSet<>();
        String upper = text.toUpperCase();
        Matcher matcher = TICKER_PATTERN.matcher(upper);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (knownTickers.contains(candidate)) {
                found.add(candidate);
            }
        }

        String lower = text.toLowerCase();
        for (Map.Entry<String, String> entry : nameToTicker.entrySet()) {
            if (lower.contains(entry.getKey())) {
                found.add(entry.getValue());
            }
        }

        return new ArrayList<>(found);
    }
}