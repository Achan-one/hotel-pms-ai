package com.hotel.repository;

import com.hotel.domain.Reservation;
import com.hotel.service.dto.ReservationSearchCondition;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 예약 원장 데이터 영속성 처리를 위한 표준 저장소 인터페이스.
 */
public interface ReservationRepository {

    void save(Reservation reservation);

    void saveAll(Collection<Reservation> reservations);

    Optional<Reservation> findById(String reservationId);

    List<Reservation> findAll();

    List<Reservation> findByGuestName(String guestName);

    List<Reservation> findByCheckInDate(LocalDate checkInDate);

    List<Reservation> findByStayNights(int stayNights);

    List<Reservation> findUnassignedByCheckInDate(LocalDate checkInDate);

    List<Reservation> search(ReservationSearchCondition condition);

    void deleteById(String reservationId);

    int count();

    void clear();
}