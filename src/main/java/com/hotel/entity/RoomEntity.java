package com.hotel.entity;

import com.hotel.domain.RoomStatus;
import com.hotel.domain.RoomType;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rooms")
public class RoomEntity {

    @Id
    @Column(name = "room_number", length = 10)
    private String roomNumber; // 예: "0501", "1404"

    @Column(nullable = false)
    private int floor;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 30)
    private RoomType roomType;

    @Column(name = "near_elevator", nullable = false)
    private boolean nearElevator;

    @Column(name = "corner_room", nullable = false)
    private boolean cornerRoom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomStatus status;

    // 객실에 부여된 태그 목록 (별도 매핑 테이블 생성)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_tag_mappings", joinColumns = @JoinColumn(name = "room_number"))
    @Column(name = "tag_code", length = 50)
    private Set<String> tags = new HashSet<>();

    // JPA 스펙상 반드시 필요한 기본 생성자
    protected RoomEntity() {}

    public RoomEntity(String roomNumber, int floor, RoomType roomType, boolean nearElevator, boolean cornerRoom, RoomStatus status) {
        this.roomNumber = roomNumber;
        this.floor = floor;
        this.roomType = roomType;
        this.nearElevator = nearElevator;
        this.cornerRoom = cornerRoom;
        this.status = status != null ? status : RoomStatus.VACANT;
    }

    // Getter & Setter
    public String getRoomNumber() { return roomNumber; }
    public int getFloor() { return floor; }
    public RoomType getRoomType() { return roomType; }
    public boolean isNearElevator() { return nearElevator; }
    public boolean isCornerRoom() { return cornerRoom; }
    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }
    public Set<String> getTags() { return tags; }

    public void addTag(String tagCode) {
        if (tagCode != null && !tagCode.isBlank()) {
            this.tags.add(tagCode.trim().toUpperCase());
        }
    }

    public void removeTag(String tagCode) {
        if (tagCode != null) {
            this.tags.remove(tagCode.trim().toUpperCase());
        }
    }
}