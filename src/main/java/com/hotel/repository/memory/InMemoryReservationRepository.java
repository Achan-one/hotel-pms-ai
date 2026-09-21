package com.hotel.repository.memory;

import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.repository.ReservationRepository;
import com.hotel.service.dto.ReservationSearchCondition;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * ConcurrentHashMap 기반 인메모리 예약 원장 저장소 구현체.
 */
public class InMemoryReservationRepository implements ReservationRepository {

    private final Map<String, Reservation> store = new ConcurrentHashMap<>();

    @Override
    public void save(Reservation reservation) {
        Objects.requireNonNull(reservation, "저장할 예약 객체는 null일 수 없습니다.");
        store.put(reservation.getReservationId(), reservation);
    }

    @Override
    public void saveAll(Collection<Reservation> reservations) {
        if (reservations != null) {
            reservations.forEach(this::save);
        }
    }

    @Override
    public Optional<Reservation> findById(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(reservationId.trim()));
    }

    @Override
    public List<Reservation> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
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

    @Override
    public List<Reservation> findByCheckInDate(LocalDate checkInDate) {
        if (checkInDate == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(r -> checkInDate.equals(r.getCheckInDate()))
                .sorted(Comparator.comparing(Reservation::getReservationId))
                .toList();
    }

    @Override
    public List<Reservation> findByStayNights(int stayNights) {
        if (stayNights <= 0) {
            return List.of();
        }
        return store.values().stream()
                .filter(r -> r.getStayNights() == stayNights)
                .sorted(Comparator.comparing(Reservation::getReservationId))
                .toList();
    }

    @Override
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

    @Override
    public List<Reservation> search(ReservationSearchCondition condition) {
        if (condition == null) {
            return findAll();
        }

        Stream<Reservation> stream = store.values().stream();

        if (condition.reservationId() != null && !condition.reservationId().isBlank()) {
            String idQuery = condition.reservationId().trim().toLowerCase();
            stream = stream.filter(r -> r.getReservationId().toLowerCase().contains(idQuery));
        }

        if (condition.guestName() != null && !condition.guestName().isBlank()) {
            String nameQuery = condition.guestName().trim().toLowerCase();
            stream = stream.filter(r -> r.getGuestName() != null && r.getGuestName().toLowerCase().contains(nameQuery));
        }

        if (condition.checkInDate() != null) {
            stream = stream.filter(r -> condition.checkInDate().equals(r.getCheckInDate()));
        }

        if (condition.stayingDate() != null) {
            LocalDate target = condition.stayingDate();
            stream = stream.filter(r -> {
                if (r.getCheckInDate() == null) return false;
                LocalDate checkOut = r.getCheckOutDate();
                return !target.isBefore(r.getCheckInDate()) && target.isBefore(checkOut);
            });
        }

        if (condition.stayNights() != null && condition.stayNights() > 0) {
            stream = stream.filter(r -> r.getStayNights() == condition.stayNights());
        }

        if (condition.roomType() != null) {
            stream = stream.filter(r -> r.getBookedRoomType() == condition.roomType());
        }

        if (condition.status() != null) {
            stream = stream.filter(r -> r.getStatus() == condition.status());
        }

        if (condition.assignedRoomNumber() != null && !condition.assignedRoomNumber().isBlank()) {
            String roomQuery = condition.assignedRoomNumber().trim();
            stream = stream.filter(r -> roomQuery.equals(r.getAssignedRoomNumber()));
        }

        return stream.sorted(Comparator.comparing(Reservation::getReservationId)).toList();
    }

    @Override
    public void deleteById(String reservationId) {
        if (reservationId != null) {
            store.remove(reservationId.trim());
        }
    }

    @Override
    public int count() {
        return store.size();
    }

    @Override
    public void clear() {
        store.clear();
    }
}