package com.hotel.repository.memory;

import com.hotel.domain.PmsReservationNumber;
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
        // DB 저장소와 같은 규칙: 새 예약이면 번호를 발급하고, 같은 ID를 다시 저장하면 기존 번호를 이어받는다.
        if (reservation.getPmsReservationNo() == null) {
            Reservation existing = store.get(reservation.getReservationId());
            reservation.setPmsReservationNo(existing != null && existing.getPmsReservationNo() != null
                    ? existing.getPmsReservationNo() : PmsReservationNumber.generate());
        }
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
    public List<Reservation> findByCheckInDateBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(r -> r.getCheckInDate() != null
                        && !r.getCheckInDate().isBefore(from) && !r.getCheckInDate().isAfter(to))
                .sorted(Comparator.comparing(Reservation::getReservationId))
                .toList();
    }

    @Override
    public List<Reservation> findStayingBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return List.of();
        }
        return store.values().stream()
                .filter(r -> r.getStatus() != ReservationStatus.CANCELLED)
                .filter(r -> r.getCheckInDate() != null && !r.getCheckInDate().isAfter(to))
                .filter(r -> r.getEffectiveCheckOutDate().isAfter(from))
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
            // PMS 예약 번호, 기존 예약 ID, OTA(채널) 예약번호 중 하나라도 맞으면 찾는다. DB 저장소와 같은 규칙이다.
            String idQuery = condition.reservationId().trim().toLowerCase();
            stream = stream.filter(r -> r.getReservationId().toLowerCase().contains(idQuery)
                    || (r.getPmsReservationNo() != null && r.getPmsReservationNo().toLowerCase().contains(idQuery))
                    || (r.getChannelInfo() != null && r.getChannelInfo().channelReservationNo() != null
                        && r.getChannelInfo().channelReservationNo().toLowerCase().contains(idQuery)));
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

                // 1. 취소된 예약은 재실 대상에서 제외
                if (r.getStatus() == ReservationStatus.CANCELLED) return false;

                // 2. 조기 퇴실: 이미 체크아웃한 고객은 실제 퇴실일(actualCheckOutDate) 기준으로 유효 종료일 재조정
                LocalDate effectiveCheckOut = (r.getStatus() == ReservationStatus.CHECKED_OUT && r.getActualCheckOutDate() != null)
                        ? r.getActualCheckOutDate()
                        : r.getCheckOutDate();

                // 3. 체류 구간 판정: checkInDate <= target < effectiveCheckOut
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

        // 태그 검색: 선호/기피 태그 코드와 원문 요청 메모(rawRequestText)를 함께 본다
        if (condition.tag() != null && !condition.tag().isBlank()) {
            String tagQuery = condition.tag().trim();
            String upperQuery = tagQuery.toUpperCase();

            stream = stream.filter(r -> {
                // 1. AI가 파싱한 선호 태그(preferredTags) 코드 매칭 (대소문자 무시)
                if (r.getTagPreference() != null) {
                    boolean matchPref = r.getTagPreference().preferredTags().stream()
                            .anyMatch(t -> t.toUpperCase().contains(upperQuery));
                    if (matchPref) return true;

                    // 2. 기피 태그(avoidTags) 코드 매칭 (예: 'GHOST', 'LOW_FLOOR' 등)
                    boolean matchAvoid = r.getTagPreference().avoidTags().stream()
                            .anyMatch(t -> t.toUpperCase().contains(upperQuery));
                    if (matchAvoid) return true;
                }

                // 3. 고객 원문 요청사항(rawRequestText) 매칭 (한글 키워드 지원: '귀신', '도쿄타워' 등)
                if (r.getRawRequestText() != null) {
                    String rawText = r.getRawRequestText().trim();
                    if (rawText.toUpperCase().contains(upperQuery) || rawText.contains(tagQuery)) {
                        return true;
                    }
                }

                return false;
            });
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