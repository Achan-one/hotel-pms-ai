package com.hotel.domain;

public enum RoomType {
    MODERATE_DOUBLE("모더레이트 더블"),
    SUPERIOR_TWIN("슈페리얼 트윈"),
    SUPERIOR_DOUBLE("슈페리얼 더블"),
    RESIDENTIAL_DOUBLE("레지덴셜 더블"),
    EXECUTIVE_DOUBLE("이그제큐티브 더블");

    private final String description;

    RoomType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}