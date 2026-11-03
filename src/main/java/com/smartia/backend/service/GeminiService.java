package com.smartia.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private final RestClient client;
    private final String apiKey;
    private final String model;

    public GeminiService(RestClient.Builder builder,
                         @Value("${gemini.api-key}") String apiKey,
                         @Value("${gemini.model}") String model) {
        this.client = builder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public String generar(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Falta configurar la variable de entorno GEMINI_API_KEY");
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

        // Si un modelo esta saturado (503) o no existe (404), prueba con el siguiente
        List<String> modelos = List.of(model, "gemini-3.5-flash", "gemini-3.1-flash-lite")
                .stream().distinct().toList();
        String ultimoError = "";

        for (String m : modelos) {
            for (int intento = 0; intento < 2; intento++) {
                try {
                    JsonNode res = client.post()
                            .uri("/v1beta/models/{m}:generateContent", m)
                            .header("x-goog-api-key", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class);
                    String texto = res == null ? "" : res.path("candidates").path(0)
                            .path("content").path("parts").path(0).path("text").asText("");
                    if (!texto.isBlank()) return texto.trim();
                } catch (RestClientResponseException e) {
                    int code = e.getStatusCode().value();
                    ultimoError = code + ": " + e.getResponseBodyAsString();
                    System.err.println("GEMINI ERROR (" + m + ") " + ultimoError);
                    if (code != 503 && code != 429 && code != 500 && code != 404) break;
                    try {
                        Thread.sleep(800);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } catch (RestClientException e) {
                    ultimoError = e.getMessage();
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "La IA no respondio. " + ultimoError);
    }
}