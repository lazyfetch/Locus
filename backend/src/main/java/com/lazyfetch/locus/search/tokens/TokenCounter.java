package com.lazyfetch.locus.search.tokens;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Service;

@Service
public class TokenCounter {


    private static final double CALIBRATION_SLOPE = 1.13;
    private static final int CALIBRATION_INTERCEPT = 5;
    private static final int CHAT_OVERHEAD = 5;  

    private final Encoding encoding;

    public TokenCounter() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.O200K_BASE);
    }

    public int count(String text) {
        if (text == null || text.isEmpty()) return 0;
        return encoding.countTokens(text);
    }

    
    public int countAll(Iterable<String> texts) {
        int total = 0;
        for (String t : texts) total += count(t);
        return total;
    }

    public int countForProvider(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.round(count(text) * CALIBRATION_SLOPE) + CALIBRATION_INTERCEPT;
    }

    public int countPrompt(String systemPrompt, String userPrompt) {
        int raw = count(systemPrompt) + count(userPrompt);
        return (int) Math.round(raw * CALIBRATION_SLOPE) + CALIBRATION_INTERCEPT;
    }
}
