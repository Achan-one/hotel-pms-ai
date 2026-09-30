package com.hotel.aieval;

import com.hotel.service.PiiMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 정답 데이터셋 자체를 검증한다. 데이터가 잘못되면 AI 점수가 의미 없어지기 때문에 일반 테스트에서 항상 돈다.
 */
class EvalDatasetTest {

    private final List<EvalCase> cases = EvalDataset.load();

    @Test
    @DisplayName("[평가 데이터] 정답과 허용 태그는 시스템 기본 태그 7개만 쓴다 (환경이 달라도 같은 결과가 나오도록)")
    void onlyDefaultTagsAreUsed() {
        assertEquals(7, DefaultTagCatalog.CODES.size());
        for (EvalCase c : cases) {
            for (Set<String> tags : List.of(c.preferred(), c.avoid(), c.optionalPreferred(), c.optionalAvoid())) {
                assertTrue(DefaultTagCatalog.CODES.containsAll(tags), c.id() + " 에 기본 태그가 아닌 값이 있다: " + tags);
            }
        }
    }

    @Test
    @DisplayName("[평가 데이터] 평가용 태그 사전은 정확히 기본 태그 7개이고 DB나 다른 태그의 영향을 받지 않는다")
    void catalogRepositoryHasExactlyTheDefaults() {
        Set<String> inRepository = DefaultTagCatalog.newRepository().findAll().stream()
                .map(t -> t.code()).collect(Collectors.toSet());
        assertEquals(DefaultTagCatalog.CODES, inRepository);
    }

    @Test
    @DisplayName("[평가 데이터] ID와 메모가 겹치지 않고 언어가 고르게 들어 있다")
    void idsUniqueAndLanguagesBalanced() {
        assertEquals(cases.size(), cases.stream().map(EvalCase::id).collect(Collectors.toSet()).size(), "ID 중복");
        assertEquals(cases.size(), cases.stream().map(EvalCase::note).collect(Collectors.toSet()).size(), "같은 메모가 중복");
        assertTrue(cases.size() >= 50, "사례가 너무 적다: " + cases.size());

        Map<String, Long> perLanguage = cases.stream().collect(Collectors.groupingBy(EvalCase::lang, Collectors.counting()));
        assertEquals(Set.of("ko", "ja", "en"), perLanguage.keySet());
        perLanguage.values().forEach(n -> assertTrue(n >= 15, "언어별 사례가 부족하다: " + perLanguage));
    }

    @Test
    @DisplayName("[평가 데이터] 일곱 태그가 모두 선호 또는 기피 정답으로 충분히 나오고, 태그와 무관한 메모도 들어 있다")
    void everyTagIsCoveredAndNegativesExist() {
        for (String code : DefaultTagCatalog.CODES) {
            long uses = cases.stream().filter(c -> c.preferred().contains(code) || c.avoid().contains(code)).count();
            assertTrue(uses >= 3, code + " 정답 사례가 " + uses + "건뿐이다");
        }
        long negatives = cases.stream().filter(EvalCase::expectsNothing).count();
        assertTrue(negatives >= 9, "태그와 무관한 메모가 부족하다: " + negatives);
        assertTrue(cases.stream().anyMatch(c -> !c.avoid().isEmpty()), "기피 정답 사례가 없다");
        assertTrue(cases.stream().anyMatch(c -> !c.preferred().isEmpty() && !c.avoid().isEmpty()), "선호와 기피가 같이 있는 사례가 없다");
    }

    @Test
    @DisplayName("[평가 데이터] 같은 태그가 정답, 허용, 반대 극성에 동시에 들어 있지 않다")
    void labelsDoNotContradict() {
        for (EvalCase c : cases) {
            Set<String> all = new HashSet<>();
            for (Set<String> tags : List.of(c.preferred(), c.avoid(), c.optionalPreferred(), c.optionalAvoid())) {
                for (String tag : tags) {
                    // 같은 태그가 선호와 기피 양쪽 정답이면 모순이다. 허용 목록이 정답과 겹치는 것도 의미가 없다.
                    assertTrue(all.add(tag), c.id() + " 에서 " + tag + " 가 여러 목록에 들어 있다");
                }
            }
        }
    }

    @Test
    @DisplayName("[평가 데이터] 메모에는 이메일이나 전화번호 같은 개인정보가 없다 (외부 API로 나가는 값이므로)")
    void noPersonalDataInNotes() {
        for (EvalCase c : cases) {
            assertEquals(c.note(), PiiMasker.mask(c.note()), c.id() + " 메모에 연락처 형태의 값이 있다");
        }
    }
}
