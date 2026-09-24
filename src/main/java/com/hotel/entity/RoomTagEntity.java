package com.hotel.entity;

import com.hotel.domain.RoomTag;
import com.hotel.domain.TagStrictness;
import jakarta.persistence.*;

@Entity
@Table(name = "room_tags")
public class RoomTagEntity {

    @Id
    @Column(name = "code", length = 50)
    private String code; // 예: "HIGH_FLOOR", "VIEW_TOKYO_TOWER"

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomTag.TagCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagStrictness strictness;

    @Column(name = "default_weight", nullable = false)
    private int defaultWeight;

    @Column(name = "is_system_default", nullable = false)
    private boolean systemDefault;

    protected RoomTagEntity() {}

    public RoomTagEntity(String code, String name, String description,
                         RoomTag.TagCategory category, TagStrictness strictness,
                         int defaultWeight, boolean systemDefault) {
        this.code = code != null ? code.trim().toUpperCase() : null;
        this.name = name;
        this.description = description;
        this.category = category != null ? category : RoomTag.TagCategory.ETC;
        this.strictness = strictness != null ? strictness : TagStrictness.SOFT;
        this.defaultWeight = defaultWeight;
        this.systemDefault = systemDefault;
    }

    public static RoomTagEntity fromDomain(RoomTag domain) {
        return new RoomTagEntity(
                domain.code(),
                domain.name(),
                domain.description(),
                domain.category(),
                domain.strictness(),
                domain.defaultWeight(),
                domain.isSystemDefault()
        );
    }

    public RoomTag toDomain() {
        return new RoomTag(
                this.code,
                this.name,
                this.description,
                this.category,
                this.strictness,
                this.defaultWeight,
                this.systemDefault
        );
    }

    // Getters
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public RoomTag.TagCategory getCategory() { return category; }
    public TagStrictness getStrictness() { return strictness; }
    public int getDefaultWeight() { return defaultWeight; }
    public boolean isSystemDefault() { return systemDefault; }
}