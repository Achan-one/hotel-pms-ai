package com.hotel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.config.AiModelConfig;
import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.service.dto.GeminiBatchPreferenceDto;
import com.hotel.service.dto.GeminiPreferenceDto;
import com.hotel.util.EnvLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiPreferenceParser {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final AiModelConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // 기본 생성자: .env 자동 구성
    public AiPreferenceParser() {
        this(EnvLoader.get("GEMINI_API_KEY"), AiModelConfig.fromEnvOrDefault());
    }

    // 주입용 생성자
    public AiPreferenceParser(String apiKey, AiModelConfig config) {
        this.apiKey = apiKey;
        this.config = config != null ? config : AiModelConfig.fromEnvOrDefault();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ==========================================
    // 1. 단일 예약 파싱 (Single)
    // ==========================================
    public GuestPreference parse(String requestText) {
        if (requestText == null || requestText.trim().isEmpty()) {
            return GuestPreference.empty();
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[AiPreferenceParser] API 키가 없어 기본 선호도를 반환합니다.");
            return GuestPreference.empty();
        }

        try {
            String endpoint = BASE_URL + config.getModelName() + ":generateContent?key=" + apiKey;
            String requestPayload = buildPromptPayload(requestText.trim());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("[AiPreferenceParser] API 오류 (" + response.statusCode() + "): " + response.body());
                return GuestPreference.empty();
            }

            return extractPreferenceFromJson(response.body());

        } catch (Exception e) {
            System.err.println("[AiPreferenceParser] 단일 파싱 예외 발생: " + e.getMessage());
            return GuestPreference.empty();
        }
    }

    // ==========================================
    // 2. 대량 예약 일괄 파싱 (Batch - 단 1회 API 호출)
    // ==========================================
    public Map<String, GuestPreference> parseBatch(List<Reservation> reservations) {
        Map<String, GuestPreference> resultMap = new HashMap<>();
        if (reservations == null || reservations.isEmpty()) {
            return resultMap;
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[AiPreferenceParser] API 키가 없어 빈 맵을 반환합니다.");
            return resultMap;
        }

        try {
            String endpoint = BASE_URL + config.getModelName() + ":generateContent?key=" + apiKey;
            String requestPayload = buildBatchPromptPayload(reservations);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(45))
                    .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("[AiPreferenceParser] 일괄 파싱 API 오류 (" + response.statusCode() + "): " + response.body());
                return resultMap;
            }

            return extractBatchPreferencesFromJson(response.body());

        } catch (Exception e) {
            System.err.println("[AiPreferenceParser] 일괄 파싱 중 예외 발생: " + e.getMessage());
            return resultMap;
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
                
                반드시 아래 규격으로만 응답하세요:
                {
                  "floorPref": "HIGH" | "LOW" | "NONE",
                  "elevatorPref": "AWAY" | "NEAR" | "NONE",
                  "cornerPref": "PREFER" | "AVOID" | "NONE",
                  "preferQuiet": boolean
                }
                """;

        var root = objectMapper.createObjectNode();
        var genConfig = root.putObject("generationConfig");
        genConfig.put("response_mime_type", "application/json");
        genConfig.put("temperature", config.getTemperature());

        if (config.getThinkingBudget() > 0) {
            var thinkingConfig = genConfig.putObject("thinkingConfig");
            thinkingConfig.put("thinkingBudget", config.getThinkingBudget());
        }

        var sysInst = root.putObject("systemInstruction");
        sysInst.putArray("parts").addObject().put("text", systemInstruction);

        var contents = root.putArray("contents");
        contents.addObject().putArray("parts").addObject().put("text", "요청 메모: \"" + userText + "\"");

        return objectMapper.writeValueAsString(root);
    }

    private String buildBatchPromptPayload(List<Reservation> reservations) throws IOException {
        String systemInstruction = """
                당신은 일본 호텔 PMS 객실 배정 지원 시스템입니다.
                제공된 복수의 예약 요청 메모 목록을 각각 분석하여 아래 규격의 JSON 배열(List)로 반환하세요.
                
                [판단 규칙]
                - reservationId: 원본 데이터의 예약 ID 그대로 유지
                - floorPref: "HIGH" (고층, 전망), "LOW" (저층, 어르신/유아 동반, 보행 편의), "NONE"
                - elevatorPref: "AWAY" (소음 기피, 안쪽 방), "NEAR" (엘베 인접, 동선 단축), "NONE"
                - cornerPref: "PREFER" (코너/끝방 선호), "AVOID" (끝방 기피), "NONE"
                - preferQuiet: true (소음 민감, 휴식 목적), false
                
                [출력 규격 예시]
                [
                  {
                    "reservationId": "RSV-001",
                    "floorPref": "HIGH",
                    "elevatorPref": "AWAY",
                    "cornerPref": "NONE",
                    "preferQuiet": true
                  }
                ]
                """;

        var root = objectMapper.createObjectNode();

        var genConfig = root.putObject("generationConfig");
        genConfig.put("response_mime_type", "application/json");
        genConfig.put("temperature", config.getTemperature());

        if (config.getThinkingBudget() > 0) {
            var thinkingConfig = genConfig.putObject("thinkingConfig");
            thinkingConfig.put("thinkingBudget", config.getThinkingBudget());
        }

        var sysInst = root.putObject("systemInstruction");
        sysInst.putArray("parts").addObject().put("text", systemInstruction);

        var contents = root.putArray("contents");
        var parts = contents.addObject().putArray("parts");

        var reservationListArray = objectMapper.createArrayNode();
        for (Reservation r : reservations) {
            var node = reservationListArray.addObject();
            node.put("reservationId", r.getReservationId());
            node.put("guestName", r.getGuestName());
            node.put("roomType", r.getBookedRoomType().name());
            node.put("nights", r.getStayNights());
            node.put("requestNote", r.getRawRequestText() != null ? r.getRawRequestText() : "");
        }

        parts.addObject().put("text", "분석할 예약 데이터 목록:\n" + objectMapper.writeValueAsString(reservationListArray));

        return objectMapper.writeValueAsString(root);
    }

    private GuestPreference extractPreferenceFromJson(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidate = root.path("candidates").get(0);
        String jsonText = candidate.path("content").path("parts").get(0).path("text").asText();

        GeminiPreferenceDto dto = objectMapper.readValue(jsonText, GeminiPreferenceDto.class);
        return dto.toDomain();
    }

    private Map<String, GuestPreference> extractBatchPreferencesFromJson(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidate = root.path("candidates").get(0);
        String jsonText = candidate.path("content").path("parts").get(0).path("text").asText();

        List<GeminiBatchPreferenceDto> dtoList = objectMapper.readValue(
                jsonText,
                new TypeReference<>() {}
        );

        Map<String, GuestPreference> map = new HashMap<>();
        for (GeminiBatchPreferenceDto dto : dtoList) {
            if (dto.getReservationId() != null) {
                map.put(dto.getReservationId(), dto.toDomain());
            }
        }
        return map;
    }

    public AiModelConfig getConfig() {
        return config;
    }
}