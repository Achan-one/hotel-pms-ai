package com.hotel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.config.AiModelConfig;
import com.hotel.domain.Reservation;
import com.hotel.domain.TagPreference;
import com.hotel.repository.TagRepository;
import com.hotel.service.dto.GeminiBatchTagDto;
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
import java.util.Objects;

public class AiPreferenceParser {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final TagRepository tagRepository;
    private final String apiKey;
    private final AiModelConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // ==========================================
    // 1. 생성자 오버로딩 (모든 호출부 호환 보장)
    // ==========================================

    // [호환 1] Main, ReservationService 기본 생성자 호출부
    public AiPreferenceParser() {
        this(new TagRepository(), EnvLoader.get("GEMINI_API_KEY"), AiModelConfig.fromEnvOrDefault());
    }

    // [호환 2] TagRepository 단독 주입 생성자
    public AiPreferenceParser(TagRepository tagRepository) {
        this(tagRepository, EnvLoader.get("GEMINI_API_KEY"), AiModelConfig.fromEnvOrDefault());
    }

    // [호환 3] ReservationServiceTest 가짜 스텁(Stub) 주입 생성자
    public AiPreferenceParser(String apiKey, AiModelConfig config) {
        this(new TagRepository(), apiKey, config);
    }

    // [마스터 생성자] 모든 의존성 주입 기준점
    public AiPreferenceParser(TagRepository tagRepository, String apiKey, AiModelConfig config) {
        this.tagRepository = (tagRepository != null) ? tagRepository : new TagRepository();
        this.apiKey = apiKey;
        this.config = (config != null) ? config : AiModelConfig.fromEnvOrDefault();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ==========================================
    // 2. 단일 메모 파싱 (Single)
    // ==========================================
    public TagPreference parse(String requestText) {
        if (requestText == null || requestText.trim().isEmpty()) {
            return TagPreference.empty();
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[AiPreferenceParser] API 키가 없어 빈 태그 선호도를 반환합니다.");
            return TagPreference.empty();
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
                return TagPreference.empty();
            }

            return extractSingleTagPreferenceFromJson(response.body());

        } catch (Exception e) {
            System.err.println("[AiPreferenceParser] 단일 파싱 예외 발생: " + e.getMessage());
            return TagPreference.empty();
        }
    }

    // ==========================================
    // 3. 대량 예약 일괄 파싱 (Batch - 단 1회 API 호출)
    // ==========================================
    public Map<String, TagPreference> parseBatch(List<Reservation> reservations) {
        Map<String, TagPreference> resultMap = new HashMap<>();
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

            return extractBatchTagPreferencesFromJson(response.body());

        } catch (Exception e) {
            System.err.println("[AiPreferenceParser] 일괄 파싱 중 예외 발생: " + e.getMessage());
            return resultMap;
        }
    }

    private String buildPromptPayload(String userText) throws IOException {
        String systemInstruction = getTagSystemInstruction(false);

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
        String systemInstruction = getTagSystemInstruction(true);

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

    private String getTagSystemInstruction(boolean isBatch) {
        String dynamicTagDictionary = tagRepository.buildPromptTagDictionary();

        String format = isBatch ? """
                [
                  {
                    "reservationId": "RSV-001",
                    "preferredTags": ["HIGH_FLOOR", "VIEW_TOWER"],
                    "avoidTags": ["NEAR_ELEVATOR"]
                  }
                ]
                """ : """
                {
                  "preferredTags": ["HIGH_FLOOR", "VIEW_TOWER"],
                  "avoidTags": ["NEAR_ELEVATOR"]
                }
                """;

        return String.format("""
                당신은 일본 호텔 PMS 객실 배정 지원 시스템입니다.
                고객의 비정형 요청 메모(한국어, 일본어, 영어 등)를 분석하여
                아래 관리자가 실시간 정의한 [객실 동적 태그 사전] 중에서
                고객이 선호하는 태그(preferredTags)와 기피하는 태그(avoidTags)를 정확히 판별하세요.

                [객실 동적 태그 사전 (관리자 실시간 등록)]
                %s

                [판단 원칙]
                1. 고객 메모가 각 태그의 [설명]과 부합할 때만 해당 태그의 스위치를 켜세요.
                2. 고객이 명시적으로 싫어하거나 피하고 싶어하는 조건은 avoidTags에 넣으세요.
                3. 식음(F&B), 단순 인사 등 객실 물리 태그와 무관한 내용은 무시하고 빈 배열([])을 반환하세요.
                4. 사전에 없는 태그 코드는 절대로 임의 생성하지 마세요.

                [출력 JSON 규격]
                %s
                """, dynamicTagDictionary, format);
    }

    private TagPreference extractSingleTagPreferenceFromJson(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String jsonText = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        GeminiBatchTagDto dto = objectMapper.readValue(jsonText, GeminiBatchTagDto.class);
        return dto.toDomain();
    }

    private Map<String, TagPreference> extractBatchTagPreferencesFromJson(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String jsonText = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

        List<GeminiBatchTagDto> dtoList = objectMapper.readValue(
                jsonText,
                new TypeReference<>() {}
        );

        Map<String, TagPreference> map = new HashMap<>();
        for (GeminiBatchTagDto dto : dtoList) {
            if (dto.getReservationId() != null) {
                map.put(dto.getReservationId(), dto.toDomain());
            }
        }
        return map;
    }

    public TagRepository getTagRepository() {
        return tagRepository;
    }

    public AiModelConfig getConfig() {
        return config;
    }
}