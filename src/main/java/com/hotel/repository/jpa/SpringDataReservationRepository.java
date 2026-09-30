package com.hotel.repository.jpa;

import com.hotel.domain.ReservationStatus;
import com.hotel.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SpringDataReservationRepository extends JpaRepository<ReservationEntity, String> {

    List<ReservationEntity> findByOperationalCheckInDate(LocalDate checkInDate);

    List<ReservationEntity> findByOperationalCheckInDateBetween(LocalDate from, LocalDate to);

    // 체크인이 조회 종료일 이전인 비취소 예약. 퇴실일 조건은 실제 퇴실일 계산이 필요해 호출하는 쪽에서 거른다.
    List<ReservationEntity> findByOperationalCheckInDateLessThanEqualAndStatusNot(LocalDate to, ReservationStatus status);

    List<ReservationEntity> findByOperationalStayNights(int stayNights);

    List<ReservationEntity> findByOperationalCheckInDateAndStatus(LocalDate checkInDate, ReservationStatus status);

    @Query("SELECT r FROM ReservationEntity r WHERE LOWER(r.operationalGuestName) LIKE LOWER(CONCAT('%', :guestName, '%'))")
    List<ReservationEntity> findByGuestNameContaining(@Param("guestName") String guestName);
}