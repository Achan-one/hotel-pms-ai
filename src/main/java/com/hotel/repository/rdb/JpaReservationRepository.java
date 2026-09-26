package com.hotel.repository.rdb;

import com.hotel.domain.BookingChannelInfo;
import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.entity.ReservationEntity;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.jpa.SpringDataReservationRepository;
import com.hotel.service.dto.ReservationSearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JpaReservationRepository implements ReservationRepository {

    private final SpringDataReservationRepository jpaRepo;

    @PersistenceContext
    private EntityManager em;

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

    /**
     * Criteria 기반 동적 쿼리 검색
     */
    @Override
    @Transactional(readOnly = true)
    public List<Reservation> search(ReservationSearchCondition condition) {
        if (condition == null) return findAll();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ReservationEntity> cq = cb.createQuery(ReservationEntity.class);
        Root<ReservationEntity> root = cq.from(ReservationEntity.class);
        List<Predicate> predicates = new ArrayList<>();

        if (condition.reservationId() != null && !condition.reservationId().isBlank()) {
            predicates.add(cb.like(cb.lower(root.get("reservationId")), "%" + condition.reservationId().trim().toLowerCase() + "%"));
        }
        if (condition.guestName() != null && !condition.guestName().isBlank()) {
            predicates.add(cb.like(cb.lower(root.get("operationalGuestName")), "%" + condition.guestName().trim().toLowerCase() + "%"));
        }
        if (condition.checkInDate() != null) {
            predicates.add(cb.equal(root.get("operationalCheckInDate"), condition.checkInDate()));
        }
        if (condition.stayNights() != null && condition.stayNights() > 0) {
            predicates.add(cb.equal(root.get("operationalStayNights"), condition.stayNights()));
        }
        if (condition.roomType() != null) {
            predicates.add(cb.equal(root.get("bookedRoomType"), condition.roomType()));
        }
        if (condition.status() != null) {
            predicates.add(cb.equal(root.get("status"), condition.status()));
        }
        if (condition.assignedRoomNumber() != null && !condition.assignedRoomNumber().isBlank()) {
            predicates.add(cb.equal(root.get("assignedRoomNumber"), condition.assignedRoomNumber().trim()));
        }

        // 🌐 OTA 채널 검색: channelType Enum 매핑 또는 internalStaffMemo 내 [OTA] 프리픽스 매칭
        if (condition.otaChannel() != null && !condition.otaChannel().isBlank()) {
            String otaUpper = condition.otaChannel().trim().toUpperCase();

            Predicate channelTypeMatch = cb.disjunction();
            try {
                BookingChannelInfo.ChannelType enumType = BookingChannelInfo.ChannelType.valueOf(otaUpper);
                channelTypeMatch = cb.equal(root.get("channelType"), enumType);
            } catch (IllegalArgumentException ignored) {
                // BookingChannelInfo.ChannelType에 일치하는 Enum이 없는 경우 무시
            }

            Predicate memoOtaMatch = cb.like(cb.upper(root.get("internalStaffMemo")), "%[" + otaUpper + "]%");
            predicates.add(cb.or(channelTypeMatch, memoOtaMatch));
        }

        // 태그 및 고객 요청 원문 검색
        if (condition.tag() != null && !condition.tag().isBlank()) {
            String q = "%" + condition.tag().trim().toUpperCase() + "%";
            Predicate prefTagMatch = cb.like(cb.upper(root.get("preferredTagsCsv")), q);
            Predicate avoidTagMatch = cb.like(cb.upper(root.get("avoidTagsCsv")), q);
            Predicate rawTextMatch = cb.like(cb.upper(root.get("rawRequestText")), q);
            predicates.add(cb.or(prefTagMatch, avoidTagMatch, rawTextMatch));
        }

        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.asc(root.get("reservationId")));

        List<Reservation> results = em.createQuery(cq).getResultList().stream()
                .map(ReservationEntity::toDomain)
                .toList();

        // stayingDate(체류일자) 반개구간 [checkIn, checkOut) 계산 필터링
        if (condition.stayingDate() != null) {
            LocalDate target = condition.stayingDate();
            results = results.stream().filter(r -> {
                if (r.getCheckInDate() == null || r.getStatus() == ReservationStatus.CANCELLED) return false;
                LocalDate effectiveCheckOut = (r.getStatus() == ReservationStatus.CHECKED_OUT && r.getActualCheckOutDate() != null)
                        ? r.getActualCheckOutDate() : r.getCheckOutDate();
                return !target.isBefore(r.getCheckInDate()) && target.isBefore(effectiveCheckOut);
            }).toList();
        }

        return results;
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

    public static class JpaCityLedgerRepository {
    }
}