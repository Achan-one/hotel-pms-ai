package com.hotel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.config.AiModelConfig;
import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.domain.TagPreference;
import com.hotel.repository.memory.InMemoryTagRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 외부 AI 서비스로 나가는 요청 본문을 로컬 서버로 받아서 확인한다. 실제 Gemini는 호출하지 않는다.
 */
class AiPreferenceParserPrivacyTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedApiKey = new AtomicReference<>();
    private volatile String cannedText = "[]";

    @BeforeEach
    void startCaptureServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedApiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            String responseJson = JSON.writeValueAsString(Map.of("candidates", List.of(
                    Map.of("content", Map.of("parts", List.of(Map.of("text", cannedText)))))));
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private AiPreferenceParser parser() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/models/";
        return new AiPreferenceParser(new InMemoryTagRepository(), "test-key",
                new AiModelConfig("test-model", 0.1, 0), baseUrl);
    }

    private Reservation reservation(String id, String pmsNo, String guest, String note) {
        Reservation r = new Reservation(id, guest, RoomType.SUPERIOR_TWIN, LocalDate.of(2026, 9, 20), 3, note, GuestPreference.empty());
        r.setPmsReservationNo(pmsNo);
        return r;
    }

    private JsonNode sentReservations() throws Exception {
        JsonNode root = JSON.readTree(receivedBody.get());
        String userText = root.path("contents").get(0).path("parts").get(0).path("text").asText();
        return JSON.readTree(userText.substring(userText.indexOf('[')));
    }

    @Test
    @DisplayName("[개인정보] AI에는 PMS 예약 번호와 요구사항만 나가고 이름, OTA 예약 ID, 객실 타입, 박수는 나가지 않는다")
    void onlyPmsNumberAndRequestAreSent() throws Exception {
        Reservation r = reservation("OTA-AGODA-99120", "PMS-260920-ABCD2345", "Tanaka Kenji", "조용한 방 부탁드립니다");

        parser().parseBatch(List.of(r));

        JsonNode sent = sentReservations();
        assertEquals(1, sent.size());
        JsonNode item = sent.get(0);
        assertEquals("PMS-260920-ABCD2345", item.path("reservationNo").asText());
        assertEquals("조용한 방 부탁드립니다", item.path("requestNote").asText());
        assertEquals(2, item.size(), "예약 번호와 요구사항, 딱 두 필드만 있어야 한다");

        String wholeRequest = receivedBody.get();
        assertFalse(wholeRequest.contains("Tanaka"), "투숙객 이름이 나가면 안 된다");
        assertFalse(wholeRequest.contains("OTA-AGODA-99120"), "OTA 예약 ID가 나가면 안 된다");
        assertFalse(wholeRequest.contains("SUPERIOR_TWIN"), "객실 타입은 태그 분석에 필요 없다");
    }

    @Test
    @DisplayName("[개인정보] 요구사항 안에 적힌 이메일과 전화번호는 가려서 보낸다")
    void contactDetailsInRequestAreMasked() throws Exception {
        Reservation r = reservation("R1", "PMS-260920-ABCD2345", "Suzuki",
                "고층 원해요. 연락처 010-1234-5678, taro.suzuki@example.com 입니다. 10층 이상, 2박 3일");

        parser().parseBatch(List.of(r));

        String note = sentReservations().get(0).path("requestNote").asText();
        assertFalse(note.contains("1234-5678"));
        assertFalse(note.contains("taro.suzuki@example.com"));
        assertTrue(note.contains("고층 원해요"));
        assertTrue(note.contains("10층 이상, 2박 3일"), "10층, 2박 3일 같은 일반 표현은 그대로 남아야 한다");
    }

    @Test
    @DisplayName("[개인정보] 단일 메모 분석에도 이메일과 전화번호 마스킹이 적용된다")
    void singleParseMasksContactDetails() throws Exception {
        cannedText = "{\"preferredTags\":[],\"avoidTags\":[]}";

        parser().parse("Call me at +81 90 1234 5678 or mail me@example.com, quiet room please");

        String text = JSON.readTree(receivedBody.get()).path("contents").get(0).path("parts").get(0).path("text").asText();
        assertFalse(text.contains("5678"));
        assertFalse(text.contains("me@example.com"));
        assertTrue(text.contains("quiet room please"));
    }

    @Test
    @DisplayName("[개인정보] API 키는 URL이 아니라 헤더로만 보낸다")
    void apiKeyIsSentInHeaderOnly() throws Exception {
        parser().parseBatch(List.of(reservation("R1", "PMS-260920-ABCD2345", "Kim", "메모")));

        assertEquals("test-key", receivedApiKey.get());
        assertFalse(receivedBody.get().contains("test-key"));
    }

    @Test
    @DisplayName("[매핑] AI가 돌려준 PMS 예약 번호를 내부 예약 ID로 되돌려 결과를 돌려준다")
    void responseIsMappedBackToInternalReservationId() {
        cannedText = "[{\"reservationNo\":\"PMS-260920-ABCD2345\",\"preferredTags\":[\"HIGH_FLOOR\"],\"avoidTags\":[\"NEAR_ELEVATOR\"]}]";
        Reservation r = reservation("OTA-AGODA-99120", "PMS-260920-ABCD2345", "Tanaka", "고층, 엘리베이터 소음 싫어요");

        Map<String, TagPreference> result = parser().parseBatch(List.of(r));

        TagPreference pref = result.get("OTA-AGODA-99120");
        assertNotNull(pref);
        assertEquals(java.util.Set.of("HIGH_FLOOR"), pref.preferredTags());
        assertEquals(java.util.Set.of("NEAR_ELEVATOR"), pref.avoidTags());
    }

    @Test
    @DisplayName("[매핑] 모델이 예전 키(reservationId)로 답해도 받아 주고, 보내지 않은 번호는 무시한다")
    void legacyKeyAccepted_unknownNumbersIgnored() {
        cannedText = "[{\"reservationId\":\"PMS-260920-ABCD2345\",\"preferredTags\":[\"QUIET_ZONE\"],\"avoidTags\":[]},"
                + "{\"reservationNo\":\"PMS-999999-ZZZZZZZZ\",\"preferredTags\":[\"HIGH_FLOOR\"],\"avoidTags\":[]}]";
        Reservation r = reservation("R1", "PMS-260920-ABCD2345", "Kim", "조용한 방");

        Map<String, TagPreference> result = parser().parseBatch(List.of(r));

        assertEquals(1, result.size());
        assertEquals(java.util.Set.of("QUIET_ZONE"), result.get("R1").preferredTags());
    }

    @Test
    @DisplayName("[매핑] 아직 저장 전이라 PMS 번호가 없는 예약은 임시 번호로 보내고, 실제 예약 ID는 나가지 않는다")
    void unsavedReservationGetsTemporaryToken() throws Exception {
        cannedText = "[{\"reservationNo\":\"REQ-1\",\"preferredTags\":[\"CORNER_ROOM\"],\"avoidTags\":[]}]";
        Reservation unsaved = reservation("SECRET-OTA-ID", null, "Kim", "코너룸");

        Map<String, TagPreference> result = parser().parseBatch(List.of(unsaved));

        assertEquals("REQ-1", sentReservations().get(0).path("reservationNo").asText());
        assertFalse(receivedBody.get().contains("SECRET-OTA-ID"));
        assertEquals(java.util.Set.of("CORNER_ROOM"), result.get("SECRET-OTA-ID").preferredTags());
    }
}
