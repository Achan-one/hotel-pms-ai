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

public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationValidator validator;
    private final AiPreferenceParser aiParser;
    private final BatchAssigner batchAssigner;
    private final RoomChangeService roomChangeService;
    private final RoomRepository roomRepository;
    private final QuotaPolicy quotaPolicy;

    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              AiPreferenceParser aiParser) {
        this(reservationRepository, roomRepository, aiParser, new InMemoryTagRepository(), new QuotaPolicy());
    }

    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              AiPreferenceParser aiParser,
                              QuotaPolicy quotaPolicy) {
        this(reservationRepository, roomRepository, aiParser, new InMemoryTagRepository(), quotaPolicy);
    }

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
            try {
                reservationRepository.save(success);
            } catch (Exception e) {
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

        String roomNumber = reservation.getAssignedRoomNumber();
        if (roomNumber != null) {
            roomRepository.findByRoomNumber(roomNumber).ifPresent(room -> {
                room.setStatus(RoomStatus.OCCUPIED);
            });
        }

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
        // 예약의 체크인 날짜 기준으로 안전하게 룸체인지 요청 생성
        LocalDate moveDate = reservation.getCheckInDate() != null ? reservation.getCheckInDate() : LocalDate.now();
        RoomChangeRequest request = new RoomChangeRequest(reservationId, targetRoomNumber, moveDate, "현장 프론트 요청");
        return processRoomChange(request);
    }

    public void processCheckOut(String reservationId) {
        processCheckOut(reservationId, LocalDate.now());
    }

    public void processCheckOut(String reservationId, LocalDate checkOutDate) {
        LocalDate effectiveDate = (checkOutDate != null) ? checkOutDate : LocalDate.now();
        Reservation reservation = findReservationOrThrow(reservationId);

        reservation.checkOut(effectiveDate);

        String roomNumber = reservation.getAssignedRoomNumber();
        if (roomNumber != null) {
            StayPeriod targetPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
            roomRepository.findByRoomNumber(roomNumber).ifPresent(room -> {
                room.truncatePeriodFrom(targetPeriod, effectiveDate);
                room.setStatus(RoomStatus.OUT);
            });
        }

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
        if (reservation.getStatus().isInHouse() || reservation.getStatus() == ReservationStatus.CHECKED_OUT) {
            throw new IllegalStateException("이미 입실하거나 퇴실한 고객의 객실 배정은 직접 취소할 수 없습니다.");
        }
        String roomNumber = reservation.getAssignedRoomNumber();
        StayPeriod stayPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
        if (roomNumber != null) {
            roomRepository.findByRoomNumber(roomNumber).ifPresent(room -> {
                room.cancelPeriod(stayPeriod);
                if (!room.isAssigned() && room.getStatus() == RoomStatus.ASSIGNED) {
                    room.setStatus(RoomStatus.VACANT);
                }
            });
        }
        reservation.cancelAssignment();
        reservationRepository.save(reservation);
    }

    public void cancelReservation(String reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (reservation.getStatus().isInHouse() || reservation.getStatus() == ReservationStatus.CHECKED_OUT) {
            throw new IllegalStateException("투숙 중이거나 이미 퇴실 완료된 예약은 취소할 수 없습니다.");
        }
        String roomNumber = reservation.getAssignedRoomNumber();
        if (roomNumber != null) {
            StayPeriod stayPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
            roomRepository.findByRoomNumber(roomNumber).ifPresent(room -> {
                room.cancelPeriod(stayPeriod);
                if (!room.isAssigned() && room.getStatus() == RoomStatus.ASSIGNED) {
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