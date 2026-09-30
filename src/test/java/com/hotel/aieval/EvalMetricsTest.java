package com.hotel.aieval;

import com.hotel.domain.TagPreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalMetricsTest {

    private static EvalCase c(String id, String kind, Set<String> pref, Set<String> avoid, Set<String> optPref, Set<String> optAvoid) {
        return new EvalCase(id, "ko", kind, "메모 " + id, pref, avoid, optPref, optAvoid);
    }

    private static TagPreference p(Set<String> pref, Set<String> avoid) {
        return new TagPreference(pref, avoid);
    }

    @Test
    @DisplayName("[지표] 정답과 완전히 같으면 정밀도, 재현율, F1이 모두 100%이다")
    void perfectPrediction() {
        List<EvalCase> cases = List.of(
                c("A", "단일", Set.of("HIGH_FLOOR"), Set.of(), Set.of(), Set.of()),
                c("B", "복합", Set.of("LOW_FLOOR"), Set.of("HIGH_FLOOR"), Set.of(), Set.of()));
        Map<String, TagPreference> predictions = Map.of(
                "A", p(Set.of("HIGH_FLOOR"), Set.of()),
                "B", p(Set.of("LOW_FLOOR"), Set.of("HIGH_FLOOR")));

        EvalMetrics.Summary s = EvalMetrics.evaluate(cases, predictions);

        assertEquals(1.0, s.overall().precision());
        assertEquals(1.0, s.overall().recall());
        assertEquals(1.0, s.overall().f1());
        assertEquals(2, s.exactMatchCount());
        assertEquals(0, s.polarityFlipCount());
    }

    @Test
    @DisplayName("[지표] 오탐과 놓침을 따로 세고 정밀도와 재현율을 정확히 계산한다")
    void countsFalsePositivesAndMisses() {
        List<EvalCase> cases = List.of(
                c("A", "단일", Set.of("HIGH_FLOOR", "CORNER_ROOM"), Set.of(), Set.of(), Set.of()));
        // HIGH_FLOOR 맞힘, CORNER_ROOM 놓침, QUIET_ZONE 오탐
        Map<String, TagPreference> predictions = Map.of("A", p(Set.of("HIGH_FLOOR", "QUIET_ZONE"), Set.of()));

        EvalMetrics.Summary s = EvalMetrics.evaluate(cases, predictions);

        assertEquals(1, s.overall().tp());
        assertEquals(1, s.overall().fp());
        assertEquals(1, s.overall().fn());
        assertEquals(0.5, s.overall().precision());
        assertEquals(0.5, s.overall().recall());
        assertEquals(0.5, s.overall().f1());
        assertEquals(0, s.exactMatchCount());
        assertEquals(List.of("선호:QUIET_ZONE"), s.results().get(0).falsePositives());
        assertEquals(List.of("선호:CORNER_ROOM"), s.results().get(0).falseNegatives());
    }

    @Test
    @DisplayName("[지표] 허용(optional) 태그는 예측해도 오탐이 아니고, 예측하지 않아도 놓침이 아니다")
    void optionalTagsAreNeutral() {
        List<EvalCase> cases = List.of(
                c("A", "단일", Set.of("QUIET_ZONE"), Set.of(), Set.of("AWAY_FROM_ELEVATOR"), Set.of()));

        EvalMetrics.Summary withOptional = EvalMetrics.evaluate(cases,
                Map.of("A", p(Set.of("QUIET_ZONE", "AWAY_FROM_ELEVATOR"), Set.of())));
        EvalMetrics.Summary withoutOptional = EvalMetrics.evaluate(cases,
                Map.of("A", p(Set.of("QUIET_ZONE"), Set.of())));

        assertEquals(1.0, withOptional.overall().precision());
        assertEquals(1.0, withOptional.overall().recall());
        assertEquals(1, withOptional.exactMatchCount());
        assertEquals(1.0, withoutOptional.overall().recall());
        assertEquals(1, withoutOptional.exactMatchCount());
    }

    @Test
    @DisplayName("[지표] 선호와 기피를 뒤집으면 오탐과 놓침이 각각 하나씩 생기고 뒤집힘으로도 센다")
    void polarityFlipIsDetected() {
        List<EvalCase> cases = List.of(c("A", "기피", Set.of(), Set.of("NEAR_ELEVATOR"), Set.of(), Set.of()));
        // 정답은 기피인데 선호로 예측
        Map<String, TagPreference> predictions = Map.of("A", p(Set.of("NEAR_ELEVATOR"), Set.of()));

        EvalMetrics.Summary s = EvalMetrics.evaluate(cases, predictions);

        assertEquals(1, s.overall().fp());
        assertEquals(1, s.overall().fn());
        assertEquals(1, s.polarityFlipCount());
        assertTrue(s.results().get(0).polarityFlips().get(0).contains("NEAR_ELEVATOR"));
    }

    @Test
    @DisplayName("[지표] 사전에 없는 태그를 만들면 오탐이면서 별도로 집계된다")
    void unknownTagsAreCounted() {
        List<EvalCase> cases = List.of(c("A", "단일", Set.of("HIGH_FLOOR"), Set.of(), Set.of(), Set.of()));
        Map<String, TagPreference> predictions = Map.of("A", p(Set.of("HIGH_FLOOR", "VIEW_TOWER"), Set.of()));

        EvalMetrics.Summary s = EvalMetrics.evaluate(cases, predictions);

        assertEquals(1, s.unknownTagCount());
        assertEquals(1, s.overall().fp());
        assertEquals(List.of("VIEW_TOWER"), s.results().get(0).unknownTags());
    }

    @Test
    @DisplayName("[지표] 태그와 무관한 메모는 빈 결과로 답해야 맞고, AI가 답하지 않은 사례는 빈 예측으로 채점하되 표시한다")
    void emptyCasesAndMissingAnswers() {
        List<EvalCase> cases = List.of(
                c("N1", "무관", Set.of(), Set.of(), Set.of(), Set.of()),
                c("N2", "무관", Set.of(), Set.of(), Set.of(), Set.of()),
                c("A", "단일", Set.of("HIGH_FLOOR"), Set.of(), Set.of(), Set.of()));
        Map<String, TagPreference> predictions = new HashMap<>();
        predictions.put("N1", p(Set.of(), Set.of()));                 // 올바르게 비움
        predictions.put("N2", p(Set.of("QUIET_ZONE"), Set.of()));     // 없는 요구를 지어냄
        // "A"는 응답 자체가 없음

        EvalMetrics.Summary s = EvalMetrics.evaluate(cases, predictions);

        assertEquals(2, s.emptyCases());
        assertEquals(1, s.emptyCasesCorrect());
        assertEquals(0.5, s.emptyCaseAccuracy());
        assertEquals(2, s.answeredCount());
        assertFalse(s.results().get(2).answered());
        assertEquals(1, s.results().get(2).falseNegatives().size());
    }

    @Test
    @DisplayName("[지표] 정답이 빈 사례에서 허용(optional) 태그만 답한 것은 맞은 것으로 센다")
    void emptyCaseWithOnlyOptionalTagsIsCorrect() {
        // "도쿄타워가 보이는 방": 기본 태그에 전망이 없어 정답은 비어 있고, 고층 태그는 방어 가능해서 허용으로 둔다.
        List<EvalCase> cases = List.of(
                c("T1", "함정", Set.of(), Set.of(), Set.of("HIGH_FLOOR"), Set.of()),
                c("T2", "함정", Set.of(), Set.of(), Set.of("HIGH_FLOOR"), Set.of()));
        Map<String, TagPreference> predictions = Map.of(
                "T1", p(Set.of("HIGH_FLOOR"), Set.of()),                 // 허용된 답 -> 맞음
                "T2", p(Set.of("HIGH_FLOOR", "QUIET_ZONE"), Set.of()));  // 허용되지 않은 QUIET_ZONE을 지어냄 -> 틀림

        EvalMetrics.Summary s = EvalMetrics.evaluate(cases, predictions);

        assertEquals(2, s.emptyCases());
        assertEquals(1, s.emptyCasesCorrect());
        assertEquals(1, s.exactMatchCount());
    }

    @Test
    @DisplayName("[지표] 태그별, 언어별, 유형별로 나눠 집계한다")
    void breakdowns() {
        List<EvalCase> cases = List.of(
                new EvalCase("K1", "ko", "단일", "a", Set.of("HIGH_FLOOR"), Set.of(), Set.of(), Set.of()),
                new EvalCase("E1", "en", "복합", "b", Set.of("LOW_FLOOR"), Set.of("HIGH_FLOOR"), Set.of(), Set.of()));
        Map<String, TagPreference> predictions = Map.of(
                "K1", p(Set.of("HIGH_FLOOR"), Set.of()),
                "E1", p(Set.of("LOW_FLOOR"), Set.of()));   // 기피 HIGH_FLOOR를 놓침

        EvalMetrics.Summary s = EvalMetrics.evaluate(cases, predictions);

        assertEquals(1.0, s.byLanguage().get("ko").f1());
        // 영어 사례: LOW_FLOOR 선호는 맞혔고 HIGH_FLOOR 기피는 놓쳤다 -> 재현율 50%, 정밀도 100%
        assertEquals(0.5, s.byLanguage().get("en").recall());
        assertEquals(1.0, s.byLanguage().get("en").precision());
        assertEquals(1, s.byLanguage().get("en").fn());
        assertEquals(1, s.byTag().get("LOW_FLOOR").tp());
        assertEquals(1, s.byTag().get("HIGH_FLOOR").fn());
        assertEquals(1.0, s.exactMatchByKind().get("단일")[1] / s.exactMatchByKind().get("단일")[0]);
        assertEquals(0.0, s.exactMatchByKind().get("복합")[1] / s.exactMatchByKind().get("복합")[0]);
    }
}
