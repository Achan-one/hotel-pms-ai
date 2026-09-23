package com.hotel.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * 객실에 동적으로 부여되는 태그 정의 (불변 레코드)
 */
public record RoomTag(
        String code,
        String name,
        String description,
        TagCategory category,
        TagStrictness strictness,
        int defaultWeight,
        @JsonProperty("isSystemDefault") boolean isSystemDefault
) {
    public RoomTag {
        Objects.requireNonNull(code, "태그 코드는 필수입니다.");
        Objects.requireNonNull(name, "태그 이름은 필수입니다.");
        description = (description != null && !description.isBlank()) ? description.trim() : name;
        category = (category != null) ? category : TagCategory.ETC;
        strictness = (strictness != null) ? strictness : TagStrictness.SOFT;
    }

    public RoomTag(String code, String name, String description, TagCategory category, TagStrictness strictness, int defaultWeight) {
        this(code, name, description, category, strictness, defaultWeight, false);
    }

    public RoomTag withStrictness(TagStrictness newStrictness) {
        return new RoomTag(this.code, this.name, this.description, this.category, newStrictness, this.defaultWeight, this.isSystemDefault);
    }

    public enum TagCategory {
        FLOOR("층수"),
        LOCATION("위치/동선"),
        VIEW("전망"),
        AMENITY("특수설비"),
        NOISE("소음"),
        ETC("기타");

        private final String desc;
        TagCategory(String desc) { this.desc = desc; }
        public String getDesc() { return desc; }
    }

    // 🔒 시스템 기본 물리 태그 (삭제 불가)
    public static final RoomTag HIGH_FLOOR = new RoomTag(
            "HIGH_FLOOR", "고층", "10층 이상의 상층부 객실. 고층 전망, 뷰 선호", TagCategory.FLOOR, TagStrictness.SOFT, 15, true);

    public static final RoomTag LOW_FLOOR = new RoomTag(
            "LOW_FLOOR", "저층", "6층 이하 저층 객실. 보행 편의, 어르신/유아 동반", TagCategory.FLOOR, TagStrictness.HARD, 15, true);

    public static final RoomTag NEAR_ELEVATOR = new RoomTag(
            "NEAR_ELEVATOR", "엘리베이터 인접", "엘리베이터와 가까워 이동이 편함", TagCategory.LOCATION, TagStrictness.HARD, 20, true);

    public static final RoomTag AWAY_FROM_ELEVATOR = new RoomTag(
            "AWAY_FROM_ELEVATOR", "엘리베이터 이격", "엘리베이터와 멀리 떨어진 복도 안쪽 방. 소음 차단", TagCategory.LOCATION, TagStrictness.SOFT, 20, true);

    public static final RoomTag CORNER_ROOM = new RoomTag(
            "CORNER_ROOM", "코너룸", "건물 모퉁이 끝방. 2면 창문, 독립적 공간", TagCategory.VIEW, TagStrictness.SOFT, 15, true);

    public static final RoomTag QUIET_ZONE = new RoomTag(
            "QUIET_ZONE", "조용한 방", "소음 민감 고객, 아기 동반, 수면 방해 최소화", TagCategory.NOISE, TagStrictness.HARD, 25, true);

    public static final RoomTag ACCESSIBLE = new RoomTag(
            "ACCESSIBLE", "배리어프리", "휠체어 이동 및 장애인/노약자 편의 설비 완비", TagCategory.AMENITY, TagStrictness.HARD, 40, true);
}