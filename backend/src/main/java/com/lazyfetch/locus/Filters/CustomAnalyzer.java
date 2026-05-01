package com.lazyfetch.locus.Filters;

import org.apache.lucene.analysis.TokenStream;  
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;

public class CustomAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();
        TokenStream filter = new LowerCaseFilter(tokenizer);
        filter = new CleanupFilter(filter);
        return new TokenStreamComponents(tokenizer, filter);
    }
}
