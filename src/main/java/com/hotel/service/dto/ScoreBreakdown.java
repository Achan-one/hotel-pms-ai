package com.hotel.service.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 객실 하나에 매겨진 배정 점수와 그 내역. total은 항상 항목 점수의 합이다.
 * 관리자가 배정 결과를 검증할 때만 쓰는 값이라 일반 화면 응답에는 싣지 않는다.
 */
public record ScoreBreakdown(List<Component> components) {

    public enum Category {
        PREFERRED_MATCH("선호 태그 가산"),
        CONFLICT("상반 조건 감점"),
        AVOID_MATCH("기피 태그 감점"),
        TAG_WASTE("특수 태그 낭비 감점"),
        GUEST_PREFERENCE("개인 선호 점수"),
        LONG_STAY("연박 가중"),
        FLOOR_ADJUSTMENT("층 보정");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * @param category 어떤 규칙에서 나온 점수인지
     * @param detail   사람이 읽는 설명 (예: 태그 코드)
     * @param points   더해진 점수. 감점은 음수
     */
    public record Component(Category category, String detail, int points) {}

    public ScoreBreakdown {
        components = List.copyOf(components);
    }

    public int total() {
        return components.stream().mapToInt(Component::points).sum();
    }

    /** 규칙(카테고리)별 합계. 표의 열로 쓴다. */
    public Map<Category, Integer> totalsByCategory() {
        return components.stream().collect(Collectors.groupingBy(
                Component::category, () -> new java.util.EnumMap<>(Category.class),
                Collectors.summingInt(Component::points)));
    }

    /** "선호 태그 가산(HIGH_FLOOR): +15 | 특수 태그 낭비 감점(TOKYO_TOWER): -30" 형태의 한 줄 설명. */
    public String describe() {
        return components.stream()
                .map(c -> c.category().label()
                        + (c.detail().isEmpty() ? "" : "(" + c.detail() + ")")
                        + ": " + (c.points() > 0 ? "+" : "") + c.points())
                .collect(Collectors.joining(" | "));
    }

    public static ScoreBreakdown of(List<Component> components) {
        return new ScoreBreakdown(components);
    }
}
