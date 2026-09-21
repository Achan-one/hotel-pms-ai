package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;
import com.hotel.service.validator.ReservationValidator;

import java.time.LocalDate;
import java.util.*;

public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationValidator validator;
    private final AiPreferenceParser aiParser;
    private final BatchAssigner batchAssigner;
    private final RoomChangeService roomChangeService;
    private final RoomRepository roomRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              AiPreferenceParser aiParser) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
        this.reservationRepository = Objects.requireNonNull(reservationRepository, "reservationRepository는 필수입니다.");
        this.validator = new ReservationValidator();
        this.aiParser = (aiParser != null) ? aiParser : new AiPreferenceParser();
        this.batchAssigner = new BatchAssigner(new RoomAssigner(roomRepository));
        this.roomChangeService = new RoomChangeService(roomRepository);
    }

    /**
     * [1. 외부 예약 접수]
     * 비즈니스 유효성 검증 통과 건만 PENDING 상태로 장부에 적재
     */
    public List<Reservation> receiveReservations(List<Reservation> rawReservations) {
        if (rawReservations == null || rawReservations.isEmpty()) {
            return List.of();
        }

        List<Reservation> validList = validator.filterValidReservations(rawReservations);
        reservationRepository.saveAll(validList);
        return validList;
    }

    /**
     * [2. 당일 기준 일괄 AI 분석 및 우선순위 자동 배정]
     * PENDING 예약 대상 단 1회 Gemini 호출 -> 동적 태그 선호도(TagPreference) 주입 -> BatchAssigner 실행 -> ASSIGNED 갱신
     */
    public BatchAssignmentResult runDailyBatchAssignment(LocalDate checkInDate) {
        Objects.requireNonNull(checkInDate, "체크인 일자는 필수입니다.");

        // 1. 해당 일자의 미배정(PENDING) 예약 조회
        List<Reservation> pendingList = reservationRepository.findUnassignedByCheckInDate(checkInDate);
        if (pendingList.isEmpty()) {
            return new BatchAssignmentResult(List.of(), List.of());
        }

        // 2. 단 1회의 Gemini API 호출로 메모로부터 태그 선호도(Map<String, TagPreference>) 일괄 추출
        Map<String, TagPreference> parsedTagPreferences = aiParser.parseBatch(pendingList);

        // 3. 파싱된 태그 선호도를 각 예약에 주입
        List<Reservation> enrichedList = new ArrayList<>();
        for (Reservation rsv : pendingList) {
            TagPreference tagPref = parsedTagPreferences.getOrDefault(rsv.getReservationId(), TagPreference.empty());
            enrichedList.add(rsv.withTagPreference(tagPref));
        }

        // 4. 태그 및 조건 우선순위 기반 일괄 배정 엔진 실행
        BatchAssignmentResult result = batchAssigner.assignAll(enrichedList);

        // 5. 배정 성공한 예약들을 장부(ReservationRepository)에 저장 (상태: ASSIGNED)
        for (Reservation success : result.getSuccessfulAssignments()) {
            reservationRepository.save(success);
        }

        return result;
    }

    /**
     * [3. 프론트 데스크 키 발급 및 체크인]
     * ASSIGNED / DUE_IN -> CHECKED_IN 상태 전이
     */
    public void processCheckIn(String reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);

        if (!reservation.isAssigned()) {
            throw new IllegalStateException("객실 배정이 완료되지 않은 예약은 체크인할 수 없습니다: " + reservationId);
        }

        reservation.checkIn();
        reservationRepository.save(reservation);
    }

    /**
     * [4. 수동 룸 체인지 (Room Move)]
     * 잔여 기간 스케줄 분할 이전 및 룸 랙 상태 전이 후 장부 동기화
     */
    public RoomChangeResult processRoomChange(RoomChangeRequest request) {
        Objects.requireNonNull(request, "RoomChangeRequest 요청은 필수입니다.");
        Reservation reservation = findReservationOrThrow(request.reservationId());

        RoomChangeResult result = roomChangeService.changeRoom(reservation, request);

        if (result.success()) {
            reservationRepository.save(reservation);
        }

        return result;
    }

    /**
     * 기존 2개 파라미터 호출 호환 편의 메서드
     */
    public RoomChangeResult processRoomChange(String reservationId, String targetRoomNumber) {
        Reservation reservation = findReservationOrThrow(reservationId);
        LocalDate moveDate = LocalDate.now();
        RoomChangeRequest request = new RoomChangeRequest(reservationId, targetRoomNumber, moveDate, "현장 프론트 요청");
        return processRoomChange(request);
    }

    /**
     * [5. 프론트 데스크 체크아웃]
     */
    public void processCheckOut(String reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        reservation.checkOut();
        reservationRepository.save(reservation);

        String roomNumber = reservation.getAssignedRoomNumber();
        if (roomNumber != null) {
            roomRepository.findByRoomNumber(roomNumber).ifPresent(room -> {
                room.truncatePeriodFrom(LocalDate.now());
                room.setStatus(RoomStatus.OUT);
            });
        }
    }

    /**
     * [6. 다조건 통합 검색]
     */
    public List<Reservation> searchReservations(ReservationSearchCondition condition) {
        return reservationRepository.search(condition);
    }

    /**
     * 단건 예약 조회
     */
    public Optional<Reservation> getReservation(String reservationId) {
        return reservationRepository.findById(reservationId);
    }

    private Reservation findReservationOrThrow(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("예약 ID는 필수입니다.");
        }
        return reservationRepository.findById(reservationId.trim())
                .orElseThrow(() -> new NoSuchElementException("예약 원장에서 해당 예약을 찾을 수 없습니다: " + reservationId));
    }

    /**
     * [배정 취소 (Unassign)]
     */
    public void cancelRoomAssignment(String reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);

        if (!reservation.isAssigned()) {
            return;
        }

        if (reservation.getStatus().isInHouse()) {
            throw new IllegalStateException("이미 입실(체크인)한 고객의 객실 배정은 직접 취소할 수 없습니다. (룸 체인지를 이용하세요)");
        }

        String roomNumber = reservation.getAssignedRoomNumber();
        StayPeriod stayPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());

        if (roomNumber != null) {
            roomRepository.findByRoomNumber(roomNumber).ifPresent(room -> {
                room.cancelPeriod(stayPeriod);
            });
        }

        reservation.cancelAssignment();
        reservationRepository.save(reservation);
    }

    /**
     * [예약 취소 (Cancel)]
     */
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
            });
        }

        reservation.cancelReservation();
        reservationRepository.save(reservation);
    }
}