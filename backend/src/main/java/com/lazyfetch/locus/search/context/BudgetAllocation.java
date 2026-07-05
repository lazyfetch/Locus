package com.lazyfetch.locus.search.context;

import java.util.List;


public class BudgetAllocation 
{
    private final int historyTokens;
    private final int dataTokens;
    private final int chunkTokens;

    public BudgetAllocation(int historyTokens, int dataTokens, int chunkTokens) 
    {
        this.historyTokens = historyTokens;
        this.dataTokens = dataTokens;
        this.chunkTokens = chunkTokens;
    }

    public int getHistoryTokens() { return historyTokens; }
    public int getDataTokens() { return dataTokens; }
    public int getChunkTokens() { return chunkTokens; }
    public int getTotal() { return historyTokens + dataTokens + chunkTokens; }
}
