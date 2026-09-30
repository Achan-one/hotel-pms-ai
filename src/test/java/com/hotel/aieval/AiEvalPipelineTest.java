package com.hotel.aieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.config.AiModelConfig;
import com.hotel.domain.TagPreference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 평가 파이프라인(데이터셋 -> 파서 요청 -> 응답 해석 -> 지표 -> 리포트)이 끝까지 이어지는지 확인한다.
 * 실제 Gemini 대신 로컬 가짜 서버가 답한다. 실제 모델의 정확도는 AiTagExtractionEval이 잰다.
 */
class AiEvalPipelineTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private volatile Function<Map<String, String>, List<Map<String, Object>>> answerer;
    private final List<EvalCase> cases = EvalDataset.load();

    @BeforeEach
    void startFakeGemini() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            JsonNode request = JSON.readTree(exchange.getRequestBody().readAllBytes());
            String userText = request.path("contents").get(0).path("parts").get(0).path("text").asText();
            JsonNode sent = JSON.readTree(userText.substring(userText.indexOf('[')));

            // 보낸 번호 -> 요구사항. 가짜 서버는 이 요구사항 문장으로 정답 사례를 찾아 정답(또는 왜곡한 답)을 돌려준다.
            Map<String, String> noteByToken = new LinkedHashMap<>();
            sent.forEach(n -> noteByToken.put(n.path("reservationNo").asText(), n.path("requestNote").asText()));

            String answerText = JSON.writeValueAsString(answerer.apply(noteByToken));
            String body = JSON.writeValueAsString(Map.of("candidates", List.of(
                    Map.of("content", Map.of("parts", List.of(Map.of("text", answerText)))))));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private com.hotel.service.AiPreferenceParser parser() throws Exception {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/models/";
        // baseUrl을 받는 생성자는 패키지 전용이라 리플렉션으로 연다. 테스트가 운영 코드를 넓히지 않도록 하기 위함이다.
        Constructor<com.hotel.service.AiPreferenceParser> ctor = com.hotel.service.AiPreferenceParser.class.getDeclaredConstructor(
                com.hotel.repository.TagRepository.class, String.class, AiModelConfig.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(DefaultTagCatalog.newRepository(), "test-key", new AiModelConfig("fake-model", 0.1, 0), baseUrl);
    }

    private Map<String, Object> answerFor(String token, EvalCase c, boolean flipAvoid) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reservationNo", token);
        item.put("preferredTags", new ArrayList<>(c.preferred()));
        item.put("avoidTags", flipAvoid ? new ArrayList<String>() : new ArrayList<>(c.avoid()));
        return item;
    }

    private EvalCase caseForNote(String note) {
        return cases.stream().filter(c -> c.note().equals(note)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("[평가 파이프라인] 정답을 그대로 돌려주는 모델이면 모든 지표가 100%이고 리포트가 만들어진다")
    void perfectModelScoresPerfectAndWritesReport(@TempDir Path dir) throws Exception {
        answerer = notes -> notes.entrySet().stream()
                .map(e -> answerFor(e.getKey(), caseForNote(e.getValue()), false)).toList();

        Map<String, TagPreference> predictions = EvalRunner.predict(parser(), cases, EvalRunner.DEFAULT_BATCH_SIZE);
        EvalMetrics.Summary summary = EvalMetrics.evaluate(cases, predictions);

        assertEquals(cases.size(), summary.answeredCount(), "모든 사례에 답이 매핑되어야 한다");
        assertEquals(1.0, summary.overall().f1());
        assertEquals(cases.size(), summary.exactMatchCount());
        assertEquals(1.0, summary.emptyCaseAccuracy());
        assertEquals(0, summary.unknownTagCount());

        EvalReport.write(dir, summary, "fake-model", 0.1);
        String md = Files.readString(dir.resolve("report.md"));
        assertTrue(md.contains("# AI 태그 추출 정확도 평가"));
        assertTrue(md.contains("100.0%"));
        assertTrue(md.contains("HIGH_FLOOR"));
        JsonNode json = JSON.readTree(Files.readString(dir.resolve("results.json")));
        assertEquals(1.0, json.path("f1").asDouble());
        assertEquals(cases.size(), json.path("cases").asInt());
    }

    @Test
    @DisplayName("[평가 파이프라인] 기피를 하나도 못 뽑는 모델은 재현율이 떨어지고 틀린 사례가 리포트에 나온다")
    void lossyModelIsPenalizedAndListedInReport(@TempDir Path dir) throws Exception {
        answerer = notes -> notes.entrySet().stream()
                .map(e -> answerFor(e.getKey(), caseForNote(e.getValue()), true)).toList();

        Map<String, TagPreference> predictions = EvalRunner.predict(parser(), cases, EvalRunner.DEFAULT_BATCH_SIZE);
        EvalMetrics.Summary summary = EvalMetrics.evaluate(cases, predictions);

        assertTrue(summary.overall().recall() < 1.0);
        assertEquals(1.0, summary.overall().precision(), "없는 걸 지어내지는 않았으므로 정밀도는 그대로다");
        long casesWithAvoid = cases.stream().filter(c -> !c.avoid().isEmpty()).count();
        assertEquals(cases.size() - casesWithAvoid, summary.exactMatchCount());

        EvalReport.write(dir, summary, "fake-model", 0.1);
        String md = Files.readString(dir.resolve("report.md"));
        assertTrue(md.contains("## 틀린 사례"));
        assertTrue(md.contains("놓침"));
        assertTrue(md.contains("기피:"), "놓친 기피 태그가 리포트에 적혀야 한다");
    }

    @Test
    @DisplayName("[평가 파이프라인] 모델이 빈 응답을 주면 답이 없는 사례로 집계된다")
    void emptyAnswersAreCountedAsUnanswered() throws Exception {
        answerer = notes -> List.of();

        Map<String, TagPreference> predictions = EvalRunner.predict(parser(), cases, EvalRunner.DEFAULT_BATCH_SIZE);
        EvalMetrics.Summary summary = EvalMetrics.evaluate(cases, predictions);

        assertEquals(0, summary.answeredCount());
        assertEquals(0.0, summary.overall().recall());
    }

    @Test
    @DisplayName("[평가 파이프라인] 요청은 한 번에 최대 30건씩 나뉘어 나가고 모든 사례가 빠짐없이 한 번씩 전달된다")
    void requestsAreBatchedAndCarryOnlyNotes() throws Exception {
        List<Integer> batchSizes = new ArrayList<>();
        answerer = notes -> {
            batchSizes.add(notes.size());
            return notes.entrySet().stream().map(e -> answerFor(e.getKey(), caseForNote(e.getValue()), false)).toList();
        };

        EvalRunner.predict(parser(), cases, EvalRunner.DEFAULT_BATCH_SIZE);

        // 사례 수가 바뀌어도 깨지지 않도록 "30건씩, 마지막은 나머지" 규칙을 사례 수로 계산해 비교한다.
        List<Integer> expected = new ArrayList<>();
        for (int left = cases.size(); left > 0; left -= EvalRunner.DEFAULT_BATCH_SIZE) {
            expected.add(Math.min(left, EvalRunner.DEFAULT_BATCH_SIZE));
        }
        assertEquals(expected, batchSizes);
        assertEquals(cases.size(), batchSizes.stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    @DisplayName("[평가 파이프라인] 리포트 시각과 모델 정보가 들어간다")
    void reportContainsRunMetadata() {
        EvalMetrics.Summary summary = EvalMetrics.evaluate(cases, Map.of());

        String md = EvalReport.markdown(summary, "gemini-2.5-flash", 0.1, LocalDateTime.of(2026, 9, 30, 12, 0, 5));

        assertTrue(md.contains("gemini-2.5-flash"));
        assertTrue(md.contains("2026-09-30T12:00:05"));
        assertTrue(md.contains("시스템 기본 태그 7개"));
    }
}
