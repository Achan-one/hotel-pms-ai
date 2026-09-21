package com.hotel.service;

import com.hotel.channel.dto.ChannelReservationRequest;
import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.repository.memory.InMemoryTagRepository;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;
import com.hotel.service.validator.ReservationValidator;

import java.time.LocalDate;
import java.util.*;

/**
 * 호텔 예약 전산 라이프사이클(인입, 자동 배정, 체크인, 룸 체인지, 정산 체크아웃, 취소)을 관장하는 핵심 도메인 서비스.
 *
 * <p>외부 채널 매니저(TL-Lincoln, ONDA)의 인바운드 전문 수신부터 하우스키핑 룸 랙과의 상태 동기화를 보장합니다.</p>
 *
 * @author PMS Core Engine Team
 * @see Reservation
 * @see RoomAssigner
 * @see BatchAssigner
 */
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationValidator validator;
    private final AiPreferenceParser aiParser;
    private final BatchAssigner batchAssigner;
    private final RoomChangeService roomChangeService;
    private final RoomRepository roomRepository;
    private final QuotaPolicy quotaPolicy;

    /**
     * 기본 태그 저장소 및 기본 쿼터 정책을 사용하는 편의 생성자.
     */
    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              AiPreferenceParser aiParser) {
        this(reservationRepository, roomRepository, aiParser, new InMemoryTagRepository(), new QuotaPolicy());
    }

    /**
     * 외부 주입 쿼터 정책을 적용하는 생성자.
     */
    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              AiPreferenceParser aiParser,
                              QuotaPolicy quotaPolicy) {
        this(reservationRepository, roomRepository, aiParser, new InMemoryTagRepository(), quotaPolicy);
    }

    /**
     * 모든 인프라 의존성을 주입받는 마스터 생성자.
     */
    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              AiPreferenceParser aiParser,
                              TagRepository tagRepository,
                              QuotaPolicy quotaPolicy) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
        this.reservationRepository = Objects.requireNonNull(reservationRepository, "reservationRepository는 필수입니다.");
        this.quotaPolicy = (quotaPolicy != null) ? quotaPolicy : new QuotaPolicy();
        this.validator = new ReservationValidator();
        this.aiParser = (aiParser != null) ? aiParser : new AiPreferenceParser();
        this.batchAssigner = new BatchAssigner(roomRepository, tagRepository, this.quotaPolicy);
        this.roomChangeService = new RoomChangeService(roomRepository);
    }

    public List<Reservation> receiveReservations(List<Reservation> rawReservations) {
        if (rawReservations == null || rawReservations.isEmpty()) return List.of();
        List<Reservation> validList = validator.filterValidReservations(rawReservations);
        reservationRepository.saveAll(validList);
        return validList;
    }

    public void processChannelRequests(List<ChannelReservationRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        List<Reservation> newBookings = new ArrayList<>();
        for (ChannelReservationRequest req : requests) {
            if (req.actionType() == ChannelReservationRequest.ActionType.BOOKING) {
                newBookings.add(req.reservation());
            } else if (req.actionType() == ChannelReservationRequest.ActionType.CANCEL) {
                try {
                    cancelReservation(req.reservationId());
                } catch (Exception e) {
                    System.err.printf("[CMS 취소 수신] 취소 처리 실패 (예약ID: %s): %s%n",
                            req.reservationId(), e.getMessage());
                }
            }
        }

        if (!newBookings.isEmpty()) {
            receiveReservations(newBookings);
        }
    }

    /**
     * 지정된 체크인 일자의 미배정(PENDING) 예약 건 일괄 자동 배정
     * - 저장 실패 시 스케줄 즉시 회수 (보상 롤백 보장)
     */
    public BatchAssignmentResult runDailyBatchAssignment(LocalDate checkInDate) {
        Objects.requireNonNull(checkInDate, "체크인 일자는 필수입니다.");
        List<Reservation> pendingList = reservationRepository.findUnassignedByCheckInDate(checkInDate);
        if (pendingList.isEmpty()) {
            return new BatchAssignmentResult(List.of(), List.of(), List.of());
        }

        Map<String, TagPreference> parsedTagPreferences = aiParser.parseBatch(pendingList);

        List<Reservation> enrichedList = new ArrayList<>();
        for (Reservation rsv : pendingList) {
            TagPreference tagPref = parsedTagPreferences.getOrDefault(rsv.getReservationId(), TagPreference.empty());
            enrichedList.add(rsv.withTagPreference(tagPref));
        }

        BatchAssignmentResult result = batchAssigner.assignAll(enrichedList);

        // [원자성 보상 트랜잭션] 저장 실패 시 객실 스케줄 원상 복구
        List<Reservation> successfullySaved = new ArrayList<>();
        for (Reservation success : result.getSuccessfulAssignments()) {
            try {
                reservationRepository.save(success);
                successfullySaved.add(success);
            } catch (Exception e) {
                // 보상 트랜잭션: 저장 튕긴 예약의 객실 점유 즉시 반납
                String roomNumber = success.getAssignedRoomNumber();
                StayPeriod period = new StayPeriod(success.getCheckInDate(), success.getStayNights());
                if (roomNumber != null) {
                    roomRepository.findByRoomNumber(roomNumber).ifPresent(r -> r.cancelPeriod(period));
                }
                success.cancelAssignment();
                System.err.printf("[보상 롤백 트리거] 예약 저장 실패로 객실 스케줄 회수 (%s, %s호): %s%n",
                        success.getReservationId(), roomNumber, e.getMessage());
            }
        }

        return result;
    }

    public void processCheckIn(String reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (!reservation.isAssigned()) {
            throw new IllegalStateException("객실 배정이 완료되지 않은 예약은 체크인할 수 없습니다: " + reservationId);
        }
        reservation.checkIn();
        reservationRepository.save(reservation);
    }

    public RoomChangeResult processRoomChange(RoomChangeRequest request) {
        Objects.requireNonNull(request, "RoomChangeRequest 요청은 필수입니다.");
        Reservation reservation = findReservationOrThrow(request.reservationId());
        RoomChangeResult result = roomChangeService.changeRoom(reservation, request);
        if (result.success()) {
            reservationRepository.save(reservation);
        }
        return result;
    }

    public RoomChangeResult processRoomChange(String reservationId, String targetRoomNumber) {
        Reservation reservation = findReservationOrThrow(reservationId);
        LocalDate moveDate = LocalDate.now();
        RoomChangeRequest request = new RoomChangeRequest(reservationId, targetRoomNumber, moveDate, "현장 프론트 요청");
        return processRoomChange(request);
    }

    /**
     * 당일 시스템 일자 기준 체크아웃 (기본 편의 메서드)
     */
    public void processCheckOut(String reservationId) {
        processCheckOut(reservationId, LocalDate.now());
    }

    /**
     * 특정 영업일자(Business Date) 기준 체크아웃 처리
     * - 미정산 상태 사전 검증
     * - 객실 미래 스케줄 회수 및 하우스키핑 OUT 처리 후 예약 원장 갱신
     */
    public void processCheckOut(String reservationId, LocalDate checkOutDate) {
        LocalDate effectiveDate = (checkOutDate != null) ? checkOutDate : LocalDate.now();
        Reservation reservation = findReservationOrThrow(reservationId);

        // 1. 투숙 및 미정산 상태 검증 (정산 미완료 시 즉시 차단)
        reservation.checkOut();

        // 2. 객실 스케줄 회수 및 청소 대기(OUT) 전이
        String roomNumber = reservation.getAssignedRoomNumber();
        if (roomNumber != null) {
            roomRepository.findByRoomNumber(roomNumber).ifPresent(room -> {
                room.truncatePeriodFrom(effectiveDate);
                room.setStatus(RoomStatus.OUT);
            });
        }

        // 3. 검증 통과 및 객실 회수 후 최종 영속화
        reservationRepository.save(reservation);
    }

    public List<Reservation> searchReservations(ReservationSearchCondition condition) {
        return reservationRepository.search(condition);
    }

    public Optional<Reservation> getReservation(String reservationId) {
        return reservationRepository.findById(reservationId);
    }

    public void cancelRoomAssignment(String reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (!reservation.isAssigned()) return;
        if (reservation.getStatus().isInHouse()) {
            throw new IllegalStateException("이미 입실(체크인)한 고객의 객실 배정은 직접 취소할 수 없습니다. (룸 체인지를 이용하세요)");
        }
        String roomNumber = reservation.getAssignedRoomNumber();
        StayPeriod stayPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
        if (roomNumber != null) {
            roomRepository.findByRoomNumber(roomNumber).ifPresent(room -> {
                room.cancelPeriod(stayPeriod);
                if (!room.isAssigned()) {
                    room.setStatus(RoomStatus.VACANT);
                }
            });
        }
        reservation.cancelAssignment();
        reservationRepository.save(reservation);
    }

    public void cancelReservation(String reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (reservation.getStatus().isInHouse()) {
            throw new IllegalStateException("현재 투숙 중인 예약은 취소할 수 없습니다. (체크아웃을 진행하세요)");
        }
        String roomNumber = reservation.getAssignedRoomNumber();
        if (roomNumber != null) {
            StayPeriod stayPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
            roomRepository.findByRoomNumber(roomNumber).ifPresent(room -> {
                room.cancelPeriod(stayPeriod);
                if (!room.isAssigned()) {
                    room.setStatus(RoomStatus.VACANT);
                }
            });
        }
        reservation.cancelReservation();
        reservationRepository.save(reservation);
    }

    public QuotaPolicy getQuotaPolicy() {
        return quotaPolicy;
    }

    private Reservation findReservationOrThrow(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("예약 ID는 필수입니다.");
        }
        return reservationRepository.findById(reservationId.trim())
                .orElseThrow(() -> new NoSuchElementException("예약 원장에서 해당 예약을 찾을 수 없습니다: " + reservationId));
    }
}