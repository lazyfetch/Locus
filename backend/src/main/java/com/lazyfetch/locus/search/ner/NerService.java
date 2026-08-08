package com.lazyfetch.locus.search.ner;

import ai.onnxruntime.*;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Service
public class NerService {

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private final ObjectMapper mapper = new ObjectMapper();
    private Map<Integer, String> idToLabel;   // from labels.json

    @PostConstruct
    public void init() throws Exception {
        var modelPath = Paths.get("models", "ner", "model.onnx");
        var tokenizerPath = Paths.get("models", "ner", "tokenizer.json");
        var labelsPath = Paths.get("models", "ner", "labels.json");

        tokenizer = HuggingFaceTokenizer.builder()
            .optTokenizerPath(tokenizerPath)
            .optPadding(true)
            .optMaxLength(128)
            .build();

        env = OrtEnvironment.getEnvironment();
        session = env.createSession(modelPath.toString(), new OrtSession.SessionOptions());

        JsonNode root = mapper.readTree(Files.readAllBytes(labelsPath));

        JsonNode id2labelNode = root.has("id2label") ? root.get("id2label") : root;

        idToLabel = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = id2labelNode.fields();
        while (fields.hasNext()) {
            var e = fields.next();
            idToLabel.put(Integer.parseInt(e.getKey()), e.getValue().asText());
        }
    }

    public NerResult extractEntities(String query) {
        try {
            Encoding encoding = tokenizer.encode(query);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[][] offsets = getOffsets(encoding, query);

            try (var inputTensor = OnnxTensor.createTensor(env, new long[][]{inputIds});
                 var maskTensor = OnnxTensor.createTensor(env, new long[][]{attentionMask});
                 var result = session.run(Map.of("input_ids", inputTensor, "attention_mask", maskTensor))) {

                float[][][] logits = (float[][][]) result.get(0).getValue();
                int seqLen = inputIds.length;

                String[] predictedLabels = new String[seqLen];
                double[] confidences = new double[seqLen];
                for (int i = 0; i < seqLen; i++) {
                    int maxIdx = 0;
                    for (int j = 1; j < logits[0][i].length; j++) {
                        if (logits[0][i][j] > logits[0][i][maxIdx]) maxIdx = j;
                    }
                    predictedLabels[i] = idToLabel.getOrDefault(maxIdx, "O");
                    confidences[i] = softmaxConfidence(logits[0][i], maxIdx);
                }

                List<NerEntity> entities = aggregateBio(
                    query, predictedLabels, confidences, offsets);

                return new NerResult(entities, 0.8);
            }
        } catch (Exception e) {
            return new NerResult(List.of(), 0.0);
        }
    }

    private List<NerEntity> aggregateBio(String query, String[] labels,
                                          double[] confidences, long[][] offsets) {
        List<NerEntity> entities = new ArrayList<>();
        int i = 0;
        while (i < labels.length) {
            String label = labels[i];
            if (label.startsWith("B-")) {
                String type = label.substring(2);
                int start = (int) offsets[i][0];
                int end = (int) offsets[i][1];
                double confSum = confidences[i];
                int count = 1;
                int j = i + 1;
                while (j < labels.length && 
                       (labels[j].equals("I-" + type) || labels[j].equals("B-" + type))) {
                    end = (int) offsets[j][1];
                    confSum += confidences[j];
                    count++;
                    j++;
                }
                String text = query.substring(start, end);
                String cleaned = cleanEntity(text);
                if (!cleaned.isEmpty()) {
                    entities.add(new NerEntity(cleaned, type, confSum / count, start, end));
                }
                i = j;
            } else {
                i++;
            }
        }
        return entities;
    }

    private boolean isContinuation(String query, int offset) {
        return offset > 0 && !Character.isWhitespace(query.charAt(offset - 1));
    }

    private String cleanEntity(String raw) {
        // Remove boundary punctuation: "hdfc flexicap??" -> "hdfc flexicap"
        String cleaned = raw.trim();
        cleaned = cleaned.replaceAll("^[^a-zA-Z0-9]+", "");  // leading punct
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9]+$", "");  // trailing punct
        return cleaned;
    }

    private long[][] getOffsets(Encoding encoding, String query) {
        String[] tokens = encoding.getTokens();
        long[][] offsets = new long[tokens.length][2];
        int queryPos = 0;

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];

            if (token.equals("[CLS]") || token.equals("[SEP]") || token.equals("[PAD]")) {
                offsets[i][0] = queryPos;
                offsets[i][1] = queryPos;
                continue;
            }

            String clean = token.startsWith("##") ? token.substring(2) : token;
            String lower = clean.toLowerCase();

            int found = indexOfIgnoreCase(query, lower, queryPos);
            if (found >= 0) {
                offsets[i][0] = found;
                offsets[i][1] = found + clean.length();
                queryPos = found + clean.length();
            } else {
                // Fallback: advance by token length
                offsets[i][0] = queryPos;
                offsets[i][1] = queryPos + clean.length();
                queryPos += clean.length();
            }
        }
        return offsets;
    }

    private int indexOfIgnoreCase(String haystack, String needle, int from) {
        String h = haystack.toLowerCase();
        String n = needle.toLowerCase();
        return h.indexOf(n, from);
    }

    private double softmaxConfidence(float[] logits, int maxIdx) {
        double max = logits[maxIdx];
        double sum = 0;
        for (float v : logits) {
            sum += Math.exp(v - max);
        }
        return Math.exp(logits[maxIdx] - max) / sum;
    }
}
