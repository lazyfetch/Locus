package com.lazyfetch.locus.search.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
@Primary

public class GroqClient implements LlmClient 
{

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public GroqClient(@Value("${groq.api.key}") String apiKey) 
    {
        this.apiKey = apiKey;
        this.model = "llama-3.3-70b-versatile";  
    }

    @Override
    public LlmResponse chat(String systemPrompt, String userPrompt, int maxTokens) 
    {
        try 
        {
            Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
                ),
                "max_tokens", maxTokens,
                "temperature", 0.3
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            
            
            System.out.println("Groq response status: " + response.statusCode());
            System.out.println("Groq response body: " + responseBody);
            
            JsonNode json = mapper.readTree(responseBody);

            if (json.has("error")) {
                String errorMsg = json.get("error").get("message").asText();
                return new LlmResponse("Error from Groq: " + errorMsg, 0, 0);
            }

            
            JsonNode choices = json.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                return new LlmResponse("Error: Groq returned no choices. Response: " + responseBody, 0, 0);
            }

            String content = choices.get(0).get("message").get("content").asText();
            int inputTokens = json.get("usage").get("prompt_tokens").asInt();
            int outputTokens = json.get("usage").get("completion_tokens").asInt();

            return new LlmResponse(content, inputTokens, outputTokens);

        } 
        catch (Exception e) 
        {
            return new LlmResponse("Error calling Groq: " + e.getMessage(), 0, 0);
        }
    }
}
