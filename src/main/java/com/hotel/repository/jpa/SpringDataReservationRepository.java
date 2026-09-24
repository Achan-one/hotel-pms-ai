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

    List<ReservationEntity> findByOperationalStayNights(int stayNights);

    List<ReservationEntity> findByOperationalCheckInDateAndStatus(LocalDate checkInDate, ReservationStatus status);

    @Query("SELECT r FROM ReservationEntity r WHERE LOWER(r.operationalGuestName) LIKE LOWER(CONCAT('%', :guestName, '%'))")
    List<ReservationEntity> findByGuestNameContaining(@Param("guestName") String guestName);
}