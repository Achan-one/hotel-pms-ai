package com.hotel.domain;

import java.util.Objects;

/**
 * 객실에 동적으로 부여되는 태그 정의 (불변 레코드)
 */
public record RoomTag(
        String code,            // 고유 코드 (예: "HIGH_FLOOR", "VIEW_TOWER")
        String name,            // 화면 표시명 (예: "고층", "타워 전망")
        String description,     // AI 프롬프트 지침용 상세 설명
        TagCategory category,   // 카테고리
        TagStrictness strictness, // [신규] 관리자가 지정하는 엄격도 (HARD / SOFT)
        int defaultWeight       // 매칭 시 부여할 기본 가중치
) {
    public RoomTag {
        Objects.requireNonNull(code, "태그 코드는 필수입니다.");
        Objects.requireNonNull(name, "태그 이름은 필수입니다.");
        description = (description != null && !description.isBlank()) ? description.trim() : name;
        category = (category != null) ? category : TagCategory.ETC;
        strictness = (strictness != null) ? strictness : TagStrictness.SOFT;
    }

    // 기존 5개 인자 호출 호환용 생성자 (기본 strictness: SOFT)
    public RoomTag(String code, String name, String description, TagCategory category, int defaultWeight) {
        this(code, name, description, category, TagStrictness.SOFT, defaultWeight);
    }

    public RoomTag withStrictness(TagStrictness newStrictness) {
        return new RoomTag(this.code, this.name, this.description, this.category, newStrictness, this.defaultWeight);
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

    // 시스템 기본 프리셋 상수 (초기 엄격도 지정)
    public static final RoomTag HIGH_FLOOR = new RoomTag(
            "HIGH_FLOOR", "고층", "10층 이상의 상층부 객실. 고층 전망, 뷰 선호", TagCategory.FLOOR, TagStrictness.SOFT, 15);

    public static final RoomTag LOW_FLOOR = new RoomTag(
            "LOW_FLOOR", "저층", "6층 이하 저층 객실. 보행 편의, 어르신/유아 동반", TagCategory.FLOOR, TagStrictness.HARD, 15);

    public static final RoomTag NEAR_ELEVATOR = new RoomTag(
            "NEAR_ELEVATOR", "엘리베이터 인접", "엘리베이터와 가까워 이동이 편함", TagCategory.LOCATION, TagStrictness.HARD, 20);

    public static final RoomTag AWAY_FROM_ELEVATOR = new RoomTag(
            "AWAY_FROM_ELEVATOR", "엘리베이터 이격", "엘리베이터와 멀리 떨어진 복도 안쪽 방. 소음 차단", TagCategory.LOCATION, TagStrictness.SOFT, 20);

    public static final RoomTag CORNER_ROOM = new RoomTag(
            "CORNER_ROOM", "코너룸", "건물 모퉁이 끝방. 2면 창문, 독립적 공간", TagCategory.VIEW, TagStrictness.SOFT, 15);

    public static final RoomTag QUIET_ZONE = new RoomTag(
            "QUIET_ZONE", "조용한 방", "소음 민감 고객, 아기 동반, 수면 방해 최소화", TagCategory.NOISE, TagStrictness.HARD, 25);

    public static final RoomTag VIEW_TOKYO_TOWER = new RoomTag(
            "VIEW_TOKYO_TOWER", "도쿄타워 전망", "창문 밖으로 도쿄타워가 조망되는 객실", TagCategory.VIEW, TagStrictness.SOFT, 30);

    public static final RoomTag ACCESSIBLE = new RoomTag(
            "ACCESSIBLE", "배리어프리", "휠체어 이동 및 장애인/노약자 편의 설비 완비", TagCategory.AMENITY, TagStrictness.HARD, 40);
}