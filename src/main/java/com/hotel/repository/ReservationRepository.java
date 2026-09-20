package com.hotel.repository;

import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.service.dto.ReservationSearchCondition;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ReservationRepository {

    private final Map<String, Reservation> store = new ConcurrentHashMap<>();

    public void save(Reservation reservation) {
        Objects.requireNonNull(reservation, "저장할 예약 객체는 null일 수 없습니다.");
        store.put(reservation.getReservationId(), reservation);
    }

    public void saveAll(Collection<Reservation> reservations) {
        if (reservations != null) {
            reservations.forEach(this::save);
        }
    }

    public Optional<Reservation> findById(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(reservationId.trim()));
    }

    public List<Reservation> findAll() {
        return new ArrayList<>(store.values());
    }

    // ==========================================
    // 1. 단일 필드 편의 조회 메서드들
    // ==========================================

    /**
     * 투숙객 이름으로 검색 (부분 일치 및 대소문자 무시 지원)
     */
    public List<Reservation> findByGuestName(String guestName) {
        if (guestName == null || guestName.isBlank()) {
            return List.of();
        }
        String query = guestName.trim().toLowerCase();
        return store.values().stream()
                .filter(r -> r.getGuestName() != null && r.getGuestName().toLowerCase().contains(query))
                .sorted(Comparator.comparing(Reservation::getReservationId))
                .toList();
    }

    /**
     * 체크인 일자 기준 전체 조회
     */
    public List<Reservation> findByCheckInDate(LocalDate checkInDate) {
        if (checkInDate == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(r -> checkInDate.equals(r.getCheckInDate()))
                .sorted(Comparator.comparing(Reservation::getReservationId))
                .toList();
    }

    /**
     * 투숙 박수(Stay Nights) 기준 조회
     */
    public List<Reservation> findByStayNights(int stayNights) {
        if (stayNights <= 0) {
            return List.of();
        }
        return store.values().stream()
                .filter(r -> r.getStayNights() == stayNights)
                .sorted(Comparator.comparing(Reservation::getReservationId))
                .toList();
    }

    /**
     * 특정 체크인 일자의 '미배정(PENDING)' 예약만 조회 (BatchAssigner 배정 대상)
     */
    public List<Reservation> findUnassignedByCheckInDate(LocalDate checkInDate) {
        if (checkInDate == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(r -> checkInDate.equals(r.getCheckInDate()))
                .filter(r -> r.getStatus() == ReservationStatus.PENDING)
                .sorted(Comparator.comparing(Reservation::getReservationId))
                .toList();
    }

    // ==========================================
    // 2. PMS 통합 동적 복합 검색 (search)
    // ==========================================

    /**
     * 이름, 날짜, 박수, 상태 등 복합 조건이 결합된 통합 검색
     */
    public List<Reservation> search(ReservationSearchCondition condition) {
        if (condition == null) {
            return findAll();
        }

        Stream<Reservation> stream = store.values().stream();

        // 1. 예약 번호 필터
        if (condition.reservationId() != null && !condition.reservationId().isBlank()) {
            String idQuery = condition.reservationId().trim().toLowerCase();
            stream = stream.filter(r -> r.getReservationId().toLowerCase().contains(idQuery));
        }

        // 2. 고객 이름 필터 (부분 일치)
        if (condition.guestName() != null && !condition.guestName().isBlank()) {
            String nameQuery = condition.guestName().trim().toLowerCase();
            stream = stream.filter(r -> r.getGuestName() != null && r.getGuestName().toLowerCase().contains(nameQuery));
        }

        // 3. 체크인 날짜 일치 필터
        if (condition.checkInDate() != null) {
            stream = stream.filter(r -> condition.checkInDate().equals(r.getCheckInDate()));
        }

        // 4. 투숙 박수 필터
        if (condition.stayNights() != null && condition.stayNights() > 0) {
            stream = stream.filter(r -> r.getStayNights() == condition.stayNights());
        }

        // 5. 객실 타입 필터
        if (condition.roomType() != null) {
            stream = stream.filter(r -> r.getBookedRoomType() == condition.roomType());
        }

        // 6. 예약 상태 필터
        if (condition.status() != null) {
            stream = stream.filter(r -> r.getStatus() == condition.status());
        }

        // 7. 배정 객실 번호 필터
        if (condition.assignedRoomNumber() != null && !condition.assignedRoomNumber().isBlank()) {
            String roomQuery = condition.assignedRoomNumber().trim();
            stream = stream.filter(r -> roomQuery.equals(r.getAssignedRoomNumber()));
        }

        return stream.sorted(Comparator.comparing(Reservation::getReservationId)).toList();
    }

    public void deleteById(String reservationId) {
        if (reservationId != null) {
            store.remove(reservationId.trim());
        }
    }

    public int count() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }
}