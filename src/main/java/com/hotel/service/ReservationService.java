package com.hotel.service;

import com.hotel.channel.dto.ChannelReservationRequest;
import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
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
     *
     * @param reservationRepository 예약 원장 저장소
     * @param roomRepository        191실 물리 객실 저장소
     * @param aiParser              비정형 고객 메모 분석용 AI 파서
     */
    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              AiPreferenceParser aiParser) {
        this(reservationRepository, roomRepository, aiParser, new TagRepository(), new QuotaPolicy());
    }

    /**
     * 외부 주입 쿼터 정책을 적용하는 생성자.
     *
     * @param reservationRepository 예약 원장 저장소
     * @param roomRepository        물리 객실 저장소
     * @param aiParser              AI 선호도 파서
     * @param quotaPolicy           호텔 관리자 안전 재고 킵 정책
     */
    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              AiPreferenceParser aiParser,
                              QuotaPolicy quotaPolicy) {
        this(reservationRepository, roomRepository, aiParser, new TagRepository(), quotaPolicy);
    }

    /**
     * 모든 인프라 의존성을 주입받는 마스터 생성자.
     *
     * @param reservationRepository 예약 원장 저장소 (필수)
     * @param roomRepository        객실 저장소 (필수)
     * @param aiParser              AI 파서
     * @param tagRepository         동적 태그 사전 저장소
     * @param quotaPolicy           보존 쿼터 정책
     * @throws NullPointerException 필수 저장소가 null인 경우 발생
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

    /**
     * 외부에서 인입된 원천 예약 목록을 유효성 검증 후 장부에 {@link ReservationStatus#PENDING} 상태로 적재합니다.
     *
     * @param rawReservations 원천 예약 리스트
     * @return 검증을 통과하여 원장에 적재 완료된 예약 리스트
     */
    public List<Reservation> receiveReservations(List<Reservation> rawReservations) {
        if (rawReservations == null || rawReservations.isEmpty()) return List.of();
        List<Reservation> validList = validator.filterValidReservations(rawReservations);
        reservationRepository.saveAll(validList);
        return validList;
    }

    /**
     * 채널 매니저(TL-Lincoln, ONDA)의 인바운드 요청 전문(신규 예약 및 취소)을 일괄 라우팅 처리합니다.
     *
     * <p>취소 건의 경우 데이터베이스에서 영구 삭제하지 않고, 객실 점유 스케줄 회수 및 상태를
     * {@link ReservationStatus#CANCELLED}로 안전하게 전이시킵니다.</p>
     *
     * @param requests 채널 매니저 어댑터에서 표준 변환된 인바운드 요청 목록
     */
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
     * 지정된 체크인 일자의 미배정({@code PENDING}) 예약 건들을 대상으로 일괄 자동 객실 배정을 수행합니다.
     *
     * @param checkInDate 배정을 진행할 기준 체크인 일자 (필수)
     * @return 배정 성공 건, 보존 쿼터/만실 탈락 건, 필수 하드 리퀘스트 경고 건이 포함된 결과 보고서
     * @throws NullPointerException 체크인 일자가 null인 경우 발생
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

        for (Reservation success : result.getSuccessfulAssignments()) {
            reservationRepository.save(success);
        }

        return result;
    }

    /**
     * 프론트 데스크 키 교부 및 입실 체크인을 처리합니다.
     *
     * @param reservationId 입실 처리할 예약 고유 번호
     * @throws IllegalStateException 객실 호실이 아직 배정되지 않은 상태에서 체크인을 시도한 경우
     * @throws NoSuchElementException 해당 예약이 원장에 존재하지 않을 경우
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
     * 투숙 중인 인하우스 고객의 객실을 변경(룸 체인지)합니다.
     *
     * @param request 룸 체인지 대상 호실 및 이동 일자 요청 DTO
     * @return 변경 성공 여부 및 이동 스케줄 분할 상세 결과
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
     * 당일 기준 즉시 룸 체인지를 수행하는 편의 메서드.
     *
     * @param reservationId    예약 번호
     * @param targetRoomNumber 이동할 신규 호실 번호
     * @return 룸 체인지 처리 결과
     */
    public RoomChangeResult processRoomChange(String reservationId, String targetRoomNumber) {
        Reservation reservation = findReservationOrThrow(reservationId);
        LocalDate moveDate = LocalDate.now();
        RoomChangeRequest request = new RoomChangeRequest(reservationId, targetRoomNumber, moveDate, "현장 프론트 요청");
        return processRoomChange(request);
    }

    /**
     * 고객 퇴실 체크아웃을 진행하며, 객실 하우스키핑 상태를 청소 대기({@code OUT})로 전이시킵니다.
     *
     * @param reservationId 체크아웃 처리할 예약 번호
     * @throws IllegalStateException 미정산 금액 또는 환불 대상 잔액이 원장에 남아있는 경우 차단
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
     * 프론트 데스크 복합 다조건 검색을 수행합니다.
     *
     * @param condition 고객명, 체크인일, 체류일, 객실타입, 상태 등의 필터링 조건
     * @return 조건에 부합하는 정렬된 예약 리스트
     */
    public List<Reservation> searchReservations(ReservationSearchCondition condition) {
        return reservationRepository.search(condition);
    }

    /**
     * 예약 고유 식별자로 단건 예약을 조회합니다.
     *
     * @param reservationId 예약 번호
     * @return 조회된 예약 Optional 컨테이너
     */
    public Optional<Reservation> getReservation(String reservationId) {
        return reservationRepository.findById(reservationId);
    }

    /**
     * 배정 확정된 객실의 호실 부여를 취소하고 대기({@code PENDING}) 상태로 되돌립니다.
     *
     * @param reservationId 배정 취소할 예약 번호
     * @throws IllegalStateException 이미 입실 완료한 인하우스 투숙객인 경우
     */
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

    /**
     * 예약을 정식 취소 처리합니다.
     *
     * <p>회계 및 감사 추적을 위해 원장에서 데이터를 삭제하지 않고 상태를 {@link ReservationStatus#CANCELLED}로 갱신하며,
     * 점유 중이던 객실 스케줄은 즉시 회수하여 재판매 가능한 공실({@code VACANT})로 환원합니다.</p>
     *
     * @param reservationId 취소 처리할 예약 번호
     * @throws IllegalStateException 이미 체크인하여 투숙 중인 예약인 경우
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
                if (!room.isAssigned()) {
                    room.setStatus(RoomStatus.VACANT);
                }
            });
        }
        reservation.cancelReservation();
        reservationRepository.save(reservation);
    }

    /**
     * 현재 적용 중인 통합 안전 보존 쿼터 정책을 반환합니다.
     *
     * @return 객실 타입 및 태그별 킵 수량 정책 객체
     */
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