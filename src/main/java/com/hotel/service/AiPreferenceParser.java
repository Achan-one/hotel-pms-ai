package com.hotel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.config.AiModelConfig;
import com.hotel.domain.Reservation;
import com.hotel.domain.TagPreference;
import com.hotel.repository.TagRepository;
import com.hotel.repository.memory.InMemoryTagRepository;
import com.hotel.service.dto.GeminiBatchTagDto;
import com.hotel.util.EnvLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(AiPreferenceParser.class);

    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final TagRepository tagRepository;
    private final String apiKey;
    private final String baseUrl;
    private final AiModelConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // 1. 생성자 오버로딩 (구현체 InMemoryTagRepository 위임)

    // [호환 1] Main, ReservationService 기본 생성자 호출부
    public AiPreferenceParser() {
        this(new InMemoryTagRepository(), EnvLoader.get("GEMINI_API_KEY"), AiModelConfig.fromEnvOrDefault());
    }

    // [호환 2] TagRepository 단독 주입 생성자
    public AiPreferenceParser(TagRepository tagRepository) {
        this(tagRepository, EnvLoader.get("GEMINI_API_KEY"), AiModelConfig.fromEnvOrDefault());
    }

    // [호환 3] ReservationServiceTest 가짜 스텁(Stub) 주입 생성자
    public AiPreferenceParser(String apiKey, AiModelConfig config) {
        this(new InMemoryTagRepository(), apiKey, config);
    }

    // 모든 의존성을 받는 기본 생성자
    public AiPreferenceParser(TagRepository tagRepository, String apiKey, AiModelConfig config) {
        this(tagRepository, apiKey, config, DEFAULT_BASE_URL);
    }

    // 요청을 받는 주소를 바꿀 수 있는 생성자. 테스트에서 실제 Gemini 대신 로컬 서버로 나가는 요청을 확인할 때 쓴다.
    AiPreferenceParser(TagRepository tagRepository, String apiKey, AiModelConfig config, String baseUrl) {
        this.baseUrl = baseUrl;
        this.tagRepository = (tagRepository != null) ? tagRepository : new InMemoryTagRepository();
        this.apiKey = apiKey;
        this.config = (config != null) ? config : AiModelConfig.fromEnvOrDefault();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // 2. 단일 메모 파싱 (Single)
    public TagPreference parse(String requestText) {
        if (requestText == null || requestText.trim().isEmpty()) {
            return TagPreference.empty();
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API 키가 없어 빈 태그 선호도를 반환합니다.");
            return TagPreference.empty();
        }

        try {
            String endpoint = baseUrl + config.getModelName() + ":generateContent";
            String requestPayload = buildPromptPayload(requestText.trim());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Gemini API 오류 status={}", response.statusCode());
                return TagPreference.empty();
            }

            return extractSingleTagPreferenceFromJson(response.body());

        } catch (Exception e) {
            log.warn("Gemini 단일 파싱 실패", e);
            return TagPreference.empty();
        }
    }

    // 3. 대량 예약 일괄 파싱 (Batch - 단 1회 API 호출)
    public Map<String, TagPreference> parseBatch(List<Reservation> reservations) {
        Map<String, TagPreference> resultMap = new HashMap<>();
        if (reservations == null || reservations.isEmpty()) {
            return resultMap;
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API 키가 없어 빈 결과를 반환합니다.");
            return resultMap;
        }

        try {
            String endpoint = baseUrl + config.getModelName() + ":generateContent";
            // AI에게는 PMS 예약 번호와 요구사항만 보낸다. 응답에 담겨 오는 번호를 내부 예약 ID로 되돌리려고 대응표를 만든다.
            Map<String, String> reservationIdByToken = new HashMap<>();
            String requestPayload = buildBatchPromptPayload(reservations, reservationIdByToken);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(45))
                    .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Gemini 일괄 파싱 API 오류 status={}", response.statusCode());
                return resultMap;
            }

            return extractBatchTagPreferencesFromJson(response.body(), reservationIdByToken);

        } catch (Exception e) {
            log.warn("Gemini 일괄 파싱 실패", e);
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
        contents.addObject().putArray("parts").addObject().put("text", "요청 메모: \"" + PiiMasker.mask(userText) + "\"");

        return objectMapper.writeValueAsString(root);
    }

    /**
     * 일괄 요청 본문을 만든다. 예약마다 {reservationNo, requestNote} 두 필드만 싣는다.
     * 투숙객 이름, OTA 예약 ID, 객실 타입, 박수는 태그를 뽑는 데 필요 없으므로 보내지 않는다.
     *
     * @param reservationIdByToken 출력 인자. AI에게 보낸 번호 -> 내부 예약 ID 대응표를 채운다.
     */
    private String buildBatchPromptPayload(List<Reservation> reservations, Map<String, String> reservationIdByToken) throws IOException {
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
        int sequence = 0;
        for (Reservation r : reservations) {
            sequence++;
            // 저장된 예약은 PMS 예약 번호를 쓰고, 아직 저장 전이라 번호가 없으면 이번 요청에서만 쓰는 임시 번호를 쓴다.
            String token = (r.getPmsReservationNo() != null && !r.getPmsReservationNo().isBlank())
                    ? r.getPmsReservationNo() : "REQ-" + sequence;
            reservationIdByToken.put(token, r.getReservationId());

            var node = reservationListArray.addObject();
            node.put("reservationNo", token);
            node.put("requestNote", PiiMasker.mask(r.getRawRequestText()));
        }

        parts.addObject().put("text", "분석할 예약 데이터 목록:\n" + objectMapper.writeValueAsString(reservationListArray));

        return objectMapper.writeValueAsString(root);
    }

    private String getTagSystemInstruction(boolean isBatch) {
        String dynamicTagDictionary = tagRepository.buildPromptTagDictionary();

        String format = isBatch ? """
                [
                  {
                    "reservationNo": "PMS-260920-A1B2C3D4",
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
                고객이 선호하는 태그(preferredTags)와 기피/거부하는 태그(avoidTags)를 정확히 판별하세요.

                [객실 동적 태그 사전 (관리자 실시간 등록)]
                %s

                [판단 원칙]
                1. 고객 메모가 각 태그의 [설명]과 부합할 때 해당 태그의 스위치를 켜세요.
                2. [중요] 고객이 직접적으로 기피를 언급한 경우는 물론, 특정 위치/환경에 대해 불편, 소음 불안, 보행 어려움 등의 우려를 언급한 경우(예: '엘리베이터 소음이 걱정돼요', '계단이나 먼 복도는 걷기 힘들어요' 등)에도 문맥상 회피 의도로 판단하여 avoidTags에 넣으세요.
                3. 식음(F&B), 단순 인사 등 객실 물리 태그와 무관한 내용은 무시하고 빈 배열([])을 반환하세요.
                4. 사전에 등록되지 않은 임의의 태그 코드는 절대로 생성하지 마세요.

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

    private Map<String, TagPreference> extractBatchTagPreferencesFromJson(String responseBody,
                                                                          Map<String, String> reservationIdByToken) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String jsonText = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

        List<GeminiBatchTagDto> dtoList = objectMapper.readValue(
                jsonText,
                new TypeReference<>() {}
        );

        // 응답의 예약 번호를 내부 예약 ID로 되돌린다. 우리가 보내지 않은 번호는 무시한다.
        Map<String, TagPreference> map = new HashMap<>();
        for (GeminiBatchTagDto dto : dtoList) {
            String reservationId = (dto.getReservationNo() != null) ? reservationIdByToken.get(dto.getReservationNo()) : null;
            if (reservationId != null) {
                map.put(reservationId, dto.toDomain());
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