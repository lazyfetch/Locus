package com.lazyfetch.locus;

import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class EmbeddingService 
{
    // random logic for now
    
    public float[] embed(String text) 
    {
        Random random = new Random(text.hashCode()); 
        float[] vec = new float[384];
        for (int i = 0; i < 384; i++) vec[i] = random.nextFloat() * 2 - 1;
        float norm = 0;
        for (float v : vec) norm += v * v;
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < 384; i++) vec[i] /= norm;
        return vec;
    }
}