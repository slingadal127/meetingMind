package com.meetingnotes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ClaudeService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL   = "llama-3.3-70b-versatile";
    private final ObjectMapper mapper   = new ObjectMapper();
    private final HttpClient client     = HttpClient.newHttpClient();

    public String ask(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "max_tokens", 4096,
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "You are an expert meeting analyst. Always respond with valid JSON only."),
                            Map.of("role", "user", "content", prompt)
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            log.info("Groq status: {}", response.statusCode());

            JsonNode root = mapper.readTree(response.body());

            if (response.statusCode() != 200) {
                log.error("Groq error: {}", response.body());
                return "Error: " + root.path("error").path("message").asText();
            }

            return root.path("choices").get(0)
                    .path("message").path("content").asText();

        } catch (Exception e) {
            log.error("Groq API error: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    public String analyzeMeeting(String transcript) {
        String prompt = """
            Analyze this meeting transcript and return ONLY valid JSON with exactly these fields.
            No markdown, no backticks, no explanation — just the raw JSON object.

            {
              "summary": "3-5 bullet points of key discussion points, use • for each bullet",
              "decisions": "bullet list of decisions made, use • for each bullet, or write 'No explicit decisions made'",
              "followUpEmail": "professional follow-up email draft based on the meeting",
              "actionItems": [
                {
                  "description": "clear action item",
                  "owner": "person responsible or 'Unassigned'",
                  "dueDate": "YYYY-MM-DD or null"
                }
              ]
            }

            TRANSCRIPT:
            %s
            """.formatted(transcript);

        return ask(prompt);
    }
}