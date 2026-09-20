package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.dto.RoomChangeRequest; // <-- 이 import문이 필수입니다.
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

    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              AiPreferenceParser aiParser) {
        Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
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
     * PENDING 예약 대상 단 1회 Gemini 호출 -> BatchAssigner 실행 -> ASSIGNED 갱신
     */
    public BatchAssignmentResult runDailyBatchAssignment(LocalDate checkInDate) {
        Objects.requireNonNull(checkInDate, "체크인 일자는 필수입니다.");

        // 1. 해당 일자의 미배정(PENDING) 예약 조회
        List<Reservation> pendingList = reservationRepository.findUnassignedByCheckInDate(checkInDate);
        if (pendingList.isEmpty()) {
            return new BatchAssignmentResult(List.of(), List.of());
        }

        // 2. 단 1회의 Gemini API 호출로 메모 일괄 정제
        Map<String, GuestPreference> parsedPreferences = aiParser.parseBatch(pendingList);

        // 3. 선호도 주입
        List<Reservation> enrichedList = new ArrayList<>();
        for (Reservation rsv : pendingList) {
            GuestPreference pref = parsedPreferences.getOrDefault(rsv.getReservationId(), GuestPreference.empty());
            enrichedList.add(rsv.withPreference(pref));
        }

        // 4. 우선순위 기반 일괄 배정 엔진 실행
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
            // roomChangeService 내부에서 예약 호실 및 상태(ROOM_CHANGED)가 이미 전이되었으므로 저장소에만 반영
            reservationRepository.save(reservation);
        }

        return result;
    }

    /**
     * 기존 2개 파라미터 호출 호환 편의 메서드 (당일 기준 잔여 기간 이전)
     */
    public RoomChangeResult processRoomChange(String reservationId, String targetRoomNumber) {
        Reservation reservation = findReservationOrThrow(reservationId);
        LocalDate moveDate = (reservation.getCheckInDate() != null) ? reservation.getCheckInDate() : LocalDate.now();
        RoomChangeRequest request = new RoomChangeRequest(reservationId, targetRoomNumber, moveDate, "현장 프론트 요청");
        return processRoomChange(request);
    }

    /**
     * [5. 프론트 데스크 체크아웃]
     * STAYING / ROOM_CHANGED -> CHECKED_OUT 상태 전이
     */
    public void processCheckOut(String reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        reservation.checkOut();
        reservationRepository.save(reservation);
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
}