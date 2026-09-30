package com.hotel.repository.rdb;

import com.hotel.domain.BookingChannelInfo;
import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.entity.ReservationEntity;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.jpa.SpringDataReservationRepository;
import com.hotel.service.dto.PageResult;
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
import java.util.Map;
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
        ReservationEntity entity = ReservationEntity.fromDomain(reservation);
        adoptExistingVersions(List.of(entity));
        ReservationEntity saved = jpaRepo.saveAndFlush(entity);
        reservation.setVersion(saved.getVersion());
    }

    // 버전이 없는 객체(새로 만든 예약)를 이미 있는 ID로 저장하면 덮어쓰기로 처리한다. 채널 재전송이 이 경우다.
    // 버전이 없으면 JPA가 신규 행으로 보고 INSERT를 시도하므로 현재 DB의 버전을 이어받는다.
    // DB에서 읽어 온 객체는 항상 버전을 들고 있어서 이 경로를 타지 않고, 동시 수정은 낙관적 락으로 걸러진다.
    // 복제 메서드(withTagPreference 등)가 버전을 빠뜨리면 이 경로로 빠져 락이 우회되므로 반드시 복사해야 한다.
    private void adoptExistingVersions(Collection<ReservationEntity> entities) {
        Map<String, ReservationEntity> unversioned = new java.util.HashMap<>();
        for (ReservationEntity e : entities) {
            if (e.getVersion() == null) unversioned.putIfAbsent(e.getReservationId(), e);
        }
        if (unversioned.isEmpty()) return;

        // 건마다 조회하지 않고 한 번의 IN 조회로 기존 행의 버전을 가져온다.
        for (ReservationEntity existing : jpaRepo.findAllById(unversioned.keySet())) {
            for (ReservationEntity e : entities) {
                if (e.getVersion() == null && e.getReservationId().equals(existing.getReservationId())) {
                    e.setVersion(existing.getVersion());
                }
            }
        }
    }

    @Override
    @Transactional
    public void saveAll(Collection<Reservation> reservations) {
        if (reservations == null || reservations.isEmpty()) return;
        List<Reservation> domains = List.copyOf(reservations);
        List<ReservationEntity> entities = domains.stream()
                .map(ReservationEntity::fromDomain)
                .toList();
        adoptExistingVersions(entities);
        List<ReservationEntity> saved = jpaRepo.saveAllAndFlush(entities);
        for (int i = 0; i < domains.size(); i++) {
            domains.get(i).setVersion(saved.get(i).getVersion());
        }
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
    public List<Reservation> findByCheckInDateBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) return List.of();
        return jpaRepo.findByOperationalCheckInDateBetween(from, to)
                .stream().map(ReservationEntity::toDomain)
                .sorted(Comparator.comparing(Reservation::getReservationId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findStayingBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) return List.of();
        return jpaRepo.findByOperationalCheckInDateLessThanEqualAndStatusNot(to, ReservationStatus.CANCELLED)
                .stream().map(ReservationEntity::toDomain)
                .filter(r -> r.getEffectiveCheckOutDate().isAfter(from))
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
        List<Predicate> predicates = buildPredicates(cb, root, condition);

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
                return !target.isBefore(r.getCheckInDate()) && target.isBefore(r.getEffectiveCheckOutDate());
            }).toList();
        }

        return results;
    }

    /**
     * DB 페이징 검색. stayingDate 조건은 실제 퇴실일 등 계산이 필요해 메모리에서 걸러야 하므로
     * 그 조건이 있으면 걸러낸 뒤에 잘라낸다.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<Reservation> search(ReservationSearchCondition condition, int page, int size) {
        if (condition == null || condition.stayingDate() != null) {
            return PageResult.slice(search(condition), page, size);
        }

        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<ReservationEntity> countRoot = countQuery.from(ReservationEntity.class);
        countQuery.select(cb.count(countRoot))
                .where(buildPredicates(cb, countRoot, condition).toArray(new Predicate[0]));
        long total = em.createQuery(countQuery).getSingleResult();

        CriteriaQuery<ReservationEntity> cq = cb.createQuery(ReservationEntity.class);
        Root<ReservationEntity> root = cq.from(ReservationEntity.class);
        cq.where(buildPredicates(cb, root, condition).toArray(new Predicate[0]));
        cq.orderBy(cb.asc(root.get("reservationId")));

        List<Reservation> items = em.createQuery(cq)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList().stream()
                .map(ReservationEntity::toDomain)
                .toList();

        return new PageResult<>(items, page, size, total);
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<ReservationEntity> root, ReservationSearchCondition condition) {
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

        // OTA 채널 검색: channelType Enum 매핑 또는 internalStaffMemo 내 [OTA] 프리픽스 매칭
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

        // 체류일자 조건은 체크인이 그날 이전이고 취소되지 않은 예약만 후보가 된다. 나머지는 메모리에서 판정한다.
        if (condition.stayingDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("operationalCheckInDate"), condition.stayingDate()));
            predicates.add(cb.notEqual(root.get("status"), ReservationStatus.CANCELLED));
        }

        return predicates;
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