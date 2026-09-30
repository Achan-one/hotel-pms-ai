package com.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 방 하나가 어느 날 밤에 점유됐는지를 한 행으로 기록한다.
 * 기본키가 (방, 날짜)라서 같은 방의 같은 날이 두 번 들어가면 DB가 거부한다.
 * 기간은 [체크인, 체크아웃) 반개구간이라 체크아웃일은 행으로 남지 않는다.
 */
@Entity
@Table(name = "room_night_occupancy")
public class RoomNightOccupancyEntity implements Persistable<RoomNightOccupancyEntity.Id> {

    @EmbeddedId
    private Id id;

    // 기본키를 직접 채우는 엔티티라 신규 여부를 따로 들고 있어야 save가 SELECT 없이 INSERT만 한다.
    @Transient
    private boolean isNew = true;

    protected RoomNightOccupancyEntity() {}

    public RoomNightOccupancyEntity(String roomNumber, LocalDate stayDate) {
        this.id = new Id(roomNumber, stayDate);
    }

    @Override
    public Id getId() { return id; }

    public String getRoomNumber() { return id.roomNumber; }

    public LocalDate getStayDate() { return id.stayDate; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "room_number", nullable = false, length = 10)
        private String roomNumber;

        @Column(name = "stay_date", nullable = false)
        private LocalDate stayDate;

        protected Id() {}

        public Id(String roomNumber, LocalDate stayDate) {
            this.roomNumber = roomNumber;
            this.stayDate = stayDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id that)) return false;
            return Objects.equals(roomNumber, that.roomNumber) && Objects.equals(stayDate, that.stayDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomNumber, stayDate);
        }
    }
}
