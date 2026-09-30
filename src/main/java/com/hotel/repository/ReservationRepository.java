package com.hotel.repository;

import com.hotel.domain.Reservation;
import com.hotel.service.dto.PageResult;
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

    /**
     * 조건에 맞는 예약을 예약 ID 순으로 한 페이지만 조회한다. page는 0부터 시작한다.
     */
    default PageResult<Reservation> search(ReservationSearchCondition condition, int page, int size) {
        return PageResult.slice(search(condition), page, size);
    }

    void deleteById(String reservationId);

    int count();

    void clear();
}