package com.hotel.repository.rdb;

import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.entity.ReservationEntity;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.jpa.SpringDataReservationRepository;
import com.hotel.service.dto.ReservationSearchCondition;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

@Repository
public class JpaReservationRepository implements ReservationRepository {

    private final SpringDataReservationRepository jpaRepo;

    public JpaReservationRepository(SpringDataReservationRepository jpaRepo) {
        this.jpaRepo = Objects.requireNonNull(jpaRepo);
    }

    @Override
    @Transactional
    public void save(Reservation reservation) {
        Objects.requireNonNull(reservation, "저장할 예약 객체는 null일 수 없습니다.");
        jpaRepo.save(ReservationEntity.fromDomain(reservation));
    }

    @Override
    @Transactional
    public void saveAll(Collection<Reservation> reservations) {
        if (reservations == null || reservations.isEmpty()) return;
        List<ReservationEntity> entities = reservations.stream()
                .map(ReservationEntity::fromDomain)
                .toList();
        jpaRepo.saveAll(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findById(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) return Optional.empty();
        return jpaRepo.findById(reservationId.trim()).map(ReservationEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findAll() {
        return jpaRepo.findAll().stream().map(ReservationEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findByGuestName(String guestName) {
        if (guestName == null || guestName.isBlank()) return List.of();
        return jpaRepo.findByGuestNameContaining(guestName.trim())
                .stream().map(ReservationEntity::toDomain)
                .sorted(Comparator.comparing(Reservation::getReservationId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findByCheckInDate(LocalDate checkInDate) {
        if (checkInDate == null) return List.of();
        return jpaRepo.findByOperationalCheckInDate(checkInDate)
                .stream().map(ReservationEntity::toDomain)
                .sorted(Comparator.comparing(Reservation::getReservationId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findByStayNights(int stayNights) {
        if (stayNights <= 0) return List.of();
        return jpaRepo.findByOperationalStayNights(stayNights)
                .stream().map(ReservationEntity::toDomain)
                .sorted(Comparator.comparing(Reservation::getReservationId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findUnassignedByCheckInDate(LocalDate checkInDate) {
        if (checkInDate == null) return List.of();
        return jpaRepo.findByOperationalCheckInDateAndStatus(checkInDate, ReservationStatus.PENDING)
                .stream().map(ReservationEntity::toDomain)
                .sorted(Comparator.comparing(Reservation::getReservationId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> search(ReservationSearchCondition condition) {
        if (condition == null) return findAll();

        Stream<Reservation> stream = jpaRepo.findAll().stream().map(ReservationEntity::toDomain);

        if (condition.reservationId() != null && !condition.reservationId().isBlank()) {
            String q = condition.reservationId().trim().toLowerCase();
            stream = stream.filter(r -> r.getReservationId().toLowerCase().contains(q));
        }
        if (condition.guestName() != null && !condition.guestName().isBlank()) {
            String q = condition.guestName().trim().toLowerCase();
            stream = stream.filter(r -> r.getGuestName() != null && r.getGuestName().toLowerCase().contains(q));
        }
        if (condition.checkInDate() != null) {
            stream = stream.filter(r -> condition.checkInDate().equals(r.getCheckInDate()));
        }
        if (condition.stayingDate() != null) {
            LocalDate target = condition.stayingDate();
            stream = stream.filter(r -> {
                if (r.getCheckInDate() == null || r.getStatus() == ReservationStatus.CANCELLED) return false;
                LocalDate effectiveCheckOut = (r.getStatus() == ReservationStatus.CHECKED_OUT && r.getActualCheckOutDate() != null)
                        ? r.getActualCheckOutDate() : r.getCheckOutDate();
                return !target.isBefore(r.getCheckInDate()) && target.isBefore(effectiveCheckOut);
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
        if (condition.tag() != null && !condition.tag().isBlank()) {
            String upperQuery = condition.tag().trim().toUpperCase();
            stream = stream.filter(r -> {
                if (r.getTagPreference() != null) {
                    if (r.getTagPreference().preferredTags().stream().anyMatch(t -> t.toUpperCase().contains(upperQuery))) return true;
                    if (r.getTagPreference().avoidTags().stream().anyMatch(t -> t.toUpperCase().contains(upperQuery))) return true;
                }
                return r.getRawRequestText() != null && r.getRawRequestText().toUpperCase().contains(upperQuery);
            });
        }

        return stream.sorted(Comparator.comparing(Reservation::getReservationId)).toList();
    }

    @Override
    @Transactional
    public void deleteById(String reservationId) {
        if (reservationId != null) {
            jpaRepo.deleteById(reservationId.trim());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int count() {
        return (int) jpaRepo.count();
    }

    @Override
    @Transactional
    public void clear() {
        jpaRepo.deleteAll();
    }
}