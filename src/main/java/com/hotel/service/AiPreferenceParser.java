package com.hotel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.domain.GuestPreference;
import com.hotel.service.dto.GeminiPreferenceDto;
import com.hotel.util.EnvLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AiPreferenceParser {

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiPreferenceParser() {
        this(EnvLoader.get("GEMINI_API_KEY"));
    }

    public AiPreferenceParser(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public GuestPreference parse(String requestText) {
        if (requestText == null || requestText.trim().isEmpty()) {
            return GuestPreference.empty();
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[AiPreferenceParser] API 키를 찾을 수 없어 기본 선호도를 반환합니다.");
            return GuestPreference.empty();
        }

        try {
            String requestPayload = buildPromptPayload(requestText.trim());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_API_URL + apiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("[AiPreferenceParser] API 호출 실패 (" + response.statusCode() + "): " + response.body());
                return GuestPreference.empty();
            }

            return extractPreferenceFromJson(response.body());

        } catch (Exception e) {
            System.err.println("[AiPreferenceParser] 파싱 중 오류 발생: " + e.getMessage());
            return GuestPreference.empty();
        }
    }

    private String buildPromptPayload(String userText) throws IOException {
        String systemInstruction = """
                당신은 일본 호텔 PMS 객실 배정 지원 시스템입니다.
                고객의 비정형 요청 메모를 분석하여 객실 선호도 JSON 객체 1개만 반환하세요.
                
                [규칙]
                - floorPref: "HIGH" (고층, 전망), "LOW" (저층, 어르신/아이 동반, 이동 편리), "NONE"
                - elevatorPref: "AWAY" (엘베 먼 곳, 안쪽 방), "NEAR" (엘베 가까운 곳), "NONE"
                - cornerPref: "PREFER" (코너/끝방 선호), "AVOID" (끝방 기피), "NONE"
                - preferQuiet: true (조용한 곳, 소음 민감), false
                
                반드시 아래 JSON 스키마 규격으로만 응답하세요:
                {
                  "floorPref": "HIGH" | "LOW" | "NONE",
                  "elevatorPref": "AWAY" | "NEAR" | "NONE",
                  "cornerPref": "PREFER" | "AVOID" | "NONE",
                  "preferQuiet": boolean
                }
                """;

        var root = objectMapper.createObjectNode();

        var config = root.putObject("generationConfig");
        config.put("response_mime_type", "application/json");

        var sysInst = root.putObject("systemInstruction");
        sysInst.putArray("parts").addObject().put("text", systemInstruction);

        var contents = root.putArray("contents");
        contents.addObject().putArray("parts").addObject().put("text", "요청 메모: \"" + userText + "\"");

        return objectMapper.writeValueAsString(root);
    }

    private GuestPreference extractPreferenceFromJson(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidate = root.path("candidates").get(0);
        String jsonText = candidate.path("content").path("parts").get(0).path("text").asText();

        GeminiPreferenceDto dto = objectMapper.readValue(jsonText, GeminiPreferenceDto.class);
        return dto.toDomain();
    }
}