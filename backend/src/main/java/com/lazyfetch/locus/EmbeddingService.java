package com.lazyfetch.locus;

import ai.onnxruntime.*;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final int MAX_LENGTH = 256;

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private boolean useTokenTypeIds;

    @PostConstruct
    public void init() throws Exception {
        Path modelPath = resolveModelPath("all-MiniLM-L6-v2.onnx");
        Path tokenizerPath = resolveModelPath("tokenizer.json");

        tokenizer =
            HuggingFaceTokenizer.builder()
                .optTokenizerPath(tokenizerPath)
                .optPadding(true)
                .optPadToMaxLength()
                .optTruncation(true)
                .optMaxLength(MAX_LENGTH)
                .build();

        env = OrtEnvironment.getEnvironment();
        session = env.createSession(modelPath.toString(), new OrtSession.SessionOptions());
        useTokenTypeIds = session.getInputNames().contains("token_type_ids");
    }

    public float[] embed(String text) throws Exception {
        Encoding encoded = tokenizer.encode(text);

        long[] inputIds = encoded.getIds();
        long[] attentionMask = encoded.getAttentionMask();
        long[] tokenTypeIds = encoded.getTypeIds();

        if (attentionMask == null || attentionMask.length == 0) 
        {
            attentionMask = new long[inputIds.length];
            Arrays.fill(attentionMask, 1L);
        }
        if (tokenTypeIds == null || tokenTypeIds.length == 0) 
        {
            tokenTypeIds = new long[inputIds.length];
        }

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, new long[][] { inputIds });
             OnnxTensor maskTensor = OnnxTensor.createTensor(env, new long[][] { attentionMask });
             OnnxTensor typeIdsTensor = OnnxTensor.createTensor(env, new long[][] { tokenTypeIds });
             OrtSession.Result result = session.run(buildInputs(inputTensor, maskTensor, typeIdsTensor))) 
        {

            float[][][] embeddings = (float[][][]) result.get(0).getValue();
            int seqLen = embeddings[0].length;
            int dim = embeddings[0][0].length;

            float[] pooled = new float[dim];
            int tokenCount = 0;

            for (int i = 0; i < seqLen; i++) 
            {
                if (attentionMask[i] == 0) 
                {
                    continue;
                }
                for (int j = 0; j < dim; j++) 
                {
                    pooled[j] += embeddings[0][i][j];
                }
                tokenCount++;
            }

            if (tokenCount > 0) 
            {
                for (int j = 0; j < dim; j++) 
                {
                    pooled[j] /= tokenCount;
                }
            }

            float norm = 0f;
            for (float v : pooled) 
            {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);

            if (norm > 0f) {
                for (int i = 0; i < pooled.length; i++) {
                    pooled[i] /= norm;
                }
            }

            return pooled;
        }
    }

    private Map<String, OnnxTensor> buildInputs(
            OnnxTensor inputTensor,
            OnnxTensor maskTensor,
            OnnxTensor typeIdsTensor) 
    {
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", inputTensor);
        inputs.put("attention_mask", maskTensor);
        if (useTokenTypeIds) {
            inputs.put("token_type_ids", typeIdsTensor);
        }
        return inputs;
    }

    private Path resolveModelPath(String filename) {
        Path direct = Paths.get("models", filename);
        if (Files.exists(direct)) {
            return direct;
        }
        return Paths.get("..", "models", filename);
    }
}