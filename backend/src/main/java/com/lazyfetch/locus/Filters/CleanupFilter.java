package com.lazyfetch.locus.Filters;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;

public class CleanupFilter extends TokenFilter {

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    public CleanupFilter(TokenStream input) 
    {
        super(input);
    }

    @Override
    public final boolean incrementToken() throws IOException 
    {
        if (!input.incrementToken()) 
        {
            return false;
        }

        char[] buffer = termAtt.buffer();
        int length = termAtt.length();

        // finding the start of the token
        int start = 0;
        while (start < length && isPunctuation(buffer[start]) && !isSpecial(buffer[start])) 
        {
            start++;
        }

        // finding the end index of the token
        int end = length - 1;
        while (end >= start && isPunctuation(buffer[end]) && !isSpecial(buffer[end])) 
        {
            end--;
        }

        if (start <= end) 
        {
            termAtt.copyBuffer(buffer, start, end - start + 1);
        }

        return true;
    }

    private boolean isPunctuation(char c) 
    {
        return !Character.isLetterOrDigit(c) && c != '$' && c != '%' && c != '/';
    }

    private boolean isSpecial(char c) 
    {
        return c == '$' || c == '%' || c == '/';
    }
}
