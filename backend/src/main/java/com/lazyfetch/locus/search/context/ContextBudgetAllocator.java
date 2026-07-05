package com.lazyfetch.locus.search.context;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ContextBudgetAllocator {

    private final int totalBudget;

    public ContextBudgetAllocator() {
        this.totalBudget = 6000;  
    }

    public ContextBudgetAllocator(int totalBudget) {
        this.totalBudget = totalBudget;
    }

    public BudgetAllocation allocate(String intent, boolean hasHistory, boolean hasData, boolean hasChunks) 
    {

       n
        int historyPct = hasHistory ? 25 : 0;  // 25% if history exists, else 0%

        int dataPct;
        int chunkPct;

        if (!hasData && !hasChunks) 
        {
            dataPct = 0;
            chunkPct = 0;
        } 
        else if (intent.equals("NAV") || intent.equals("FUND_DETAILS") || intent.equals("COMPARE_FUNDS")) 
        {
            // User wants numbers → bias toward structured data
            dataPct = 60;
            chunkPct = 40;
        } 
        else if (intent.equals("HOLDINGS"))
        {
            // Holdings are structured data
            dataPct = 70;
            chunkPct = 30;
        } 
        else 
        {
            // GENERAL or unknown → equal split
            dataPct = 50;
            chunkPct = 50;
        }

        if (!hasData) 
        {
            chunkPct += dataPct;
            dataPct = 0;
        }
        if (!hasChunks)
        {
            dataPct += chunkPct;
            chunkPct = 0;
        }

        // scaling down to available tokens

        int remainingPct = 100 - historyPct;
        dataPct = dataPct * remainingPct / 100;
        chunkPct = chunkPct * remainingPct / 100;

        int historyTokens = (totalBudget * historyPct) / 100;
        int dataTokens = (totalBudget * dataPct) / 100;
        int chunkTokens = totalBudget - historyTokens - dataTokens;

        return new BudgetAllocation(historyTokens, dataTokens, chunkTokens);
    }
}
