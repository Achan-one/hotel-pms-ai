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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final AiPreferenceParser aiParser;
    private final BatchAssigner batchAssigner;
    private final QuotaPolicy quotaPolicy;
    private final ReservationValidator validator;

    @Autowired
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
    }

    // 단위 테스트 편의용 3개 인자 오버로딩 생성자
    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              AiPreferenceParser aiParser) {
        this(reservationRepository, roomRepository, aiParser, new InMemoryTagRepository(), new QuotaPolicy());
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
                String roomNumber = success.getAssignedRoomNumber();
                if (roomNumber != null) {
                    roomRepository.findByRoomNumberForUpdate(roomNumber).ifPresent(room -> {
                        StayPeriod period = new StayPeriod(success.getCheckInDate(), success.getStayNights());
                        room.tryBookPeriod(period);
                        roomRepository.save(room);
                    });
                }
                reservationRepository.save(success);
            } catch (Exception e) {
                String roomNumber = success.getAssignedRoomNumber();
                StayPeriod period = new StayPeriod(success.getCheckInDate(), success.getStayNights());
                if (roomNumber != null) {
                    roomRepository.findByRoomNumber(roomNumber).ifPresent(r -> {
                        r.cancelPeriod(period);
                        roomRepository.save(r);
                    });
                }
                success.cancelAssignment();
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
            roomRepository.findByRoomNumberForUpdate(roomNumber).ifPresent(room -> {
                room.setStatus(RoomStatus.OCCUPIED);
                roomRepository.save(room);
            });
        }

        reservationRepository.save(reservation);
    }

    public RoomChangeResult processRoomChange(RoomChangeRequest request) {
        Objects.requireNonNull(request, "RoomChangeRequest 요청은 필수입니다.");
        Reservation reservation = findReservationOrThrow(request.reservationId());

        if (!reservation.getStatus().isInHouse()) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    "투숙 중인 고객만 룸 체인지가 가능합니다. (현재 상태: " + reservation.getStatus() + ")");
        }

        String targetRoomNumber = request.targetRoomNumber().trim();
        String originRoomNumber = reservation.getAssignedRoomNumber();

        if (targetRoomNumber.equals(originRoomNumber)) {
            return RoomChangeResult.failure(reservation.getReservationId(), "현재 배정된 객실과 동일한 객실로 이동할 수 없습니다.");
        }

        Room targetRoom = roomRepository.findByRoomNumberForUpdate(targetRoomNumber).orElse(null);
        if (targetRoom == null) {
            return RoomChangeResult.failure(reservation.getReservationId(), "해당 객실(" + targetRoomNumber + ")이 도면에 존재하지 않습니다.");
        }

        if (!targetRoom.getStatus().isAssignable()) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    "이동 대상 객실(" + targetRoomNumber + "호)은 입실 가능한 공실(VACANT)이 아닙니다. (현재 상태: "
                            + targetRoom.getStatus().getTitle() + ")");
        }

        LocalDate moveDate = request.moveDate();
        LocalDate checkInDate = reservation.getCheckInDate();
        LocalDate checkOutDate = reservation.getCheckOutDate();

        if (moveDate.isBefore(checkInDate) || !moveDate.isBefore(checkOutDate)) {
            return RoomChangeResult.failure(reservation.getReservationId(), "유효하지 않은 이동 일자 범위입니다.");
        }

        int remainingNights = (int) ChronoUnit.DAYS.between(moveDate, checkOutDate);
        StayPeriod remainingPeriod = new StayPeriod(moveDate, remainingNights);
        StayPeriod originalPeriod = new StayPeriod(checkInDate, reservation.getStayNights());

        if (!targetRoom.tryBookPeriod(remainingPeriod)) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    String.format("대상 객실(%s호)은 해당 잔여 기간(%s)에 이미 다른 예약이 점유하여 배정할 수 없습니다.",
                            targetRoomNumber, remainingPeriod));
        }

        if (originRoomNumber != null) {
            roomRepository.findByRoomNumberForUpdate(originRoomNumber.trim()).ifPresent(originRoom -> {
                originRoom.truncatePeriodFrom(originalPeriod, moveDate);
                originRoom.setStatus(RoomStatus.OUT);
                roomRepository.save(originRoom);
            });
        }

        targetRoom.setStatus(RoomStatus.OCCUPIED);
        reservation.changeRoom(targetRoom.getRoomNumber());

        roomRepository.save(targetRoom);
        reservationRepository.save(reservation);

        return RoomChangeResult.success(
                reservation.getReservationId(),
                originRoomNumber,
                targetRoom.getRoomNumber(),
                moveDate,
                remainingNights
        );
    }

    public void manualAssignRoom(String reservationId, String targetRoomNumber) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (reservation.getStatus().isInHouse() || reservation.getStatus() == ReservationStatus.CHECKED_OUT) {
            throw new IllegalStateException("이미 입실하거나 퇴실한 고객은 일반 배정이 아닌 [룸 체인지]를 사용해야 합니다.");
        }

        Room targetRoom = roomRepository.findByRoomNumberForUpdate(targetRoomNumber)
                .orElseThrow(() -> new IllegalArgumentException("해당 호실(" + targetRoomNumber + ")이 도면에 존재하지 않습니다."));

        StayPeriod targetPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());

        if (!targetRoom.isAvailable(targetPeriod)) {
            throw new IllegalStateException("해당 객실(" + targetRoomNumber + "호)은 선택한 일정에 이미 점유되어 있습니다.");
        }

        if (reservation.isAssigned() && reservation.getAssignedRoomNumber() != null) {
            String prevRoom = reservation.getAssignedRoomNumber();
            roomRepository.findByRoomNumberForUpdate(prevRoom).ifPresent(r -> {
                r.cancelPeriod(targetPeriod);
                roomRepository.save(r);
            });
        }

        targetRoom.bookPeriod(targetPeriod);
        reservation.assignRoom(targetRoomNumber);

        roomRepository.save(targetRoom);
        reservationRepository.save(reservation);
    }

    public void updateOperationalDetails(String reservationId,
                                         String operationalName,
                                         LocalDate operationalCheckIn,
                                         Integer operationalNights,
                                         String staffMemo) {
        Reservation reservation = findReservationOrThrow(reservationId);

        if ((operationalCheckIn != null && !operationalCheckIn.equals(reservation.getCheckInDate())) ||
                (operationalNights != null && operationalNights != reservation.getStayNights())) {

            if (reservation.isAssigned()) {
                String roomNumber = reservation.getAssignedRoomNumber();
                Room room = roomRepository.findByRoomNumberForUpdate(roomNumber).orElseThrow();

                StayPeriod oldPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
                room.cancelPeriod(oldPeriod);

                LocalDate effectiveCheckIn = (operationalCheckIn != null) ? operationalCheckIn : reservation.getCheckInDate();
                int effectiveNights = (operationalNights != null) ? operationalNights : reservation.getStayNights();
                StayPeriod newPeriod = new StayPeriod(effectiveCheckIn, effectiveNights);

                if (!room.isAvailable(newPeriod)) {
                    room.bookPeriod(oldPeriod);
                    throw new IllegalStateException("해당 객실(" + roomNumber + "호)은 변경하려는 일정에 이미 예약이 존재합니다.");
                }
                room.bookPeriod(newPeriod);
                roomRepository.save(room);
            }
        }

        reservation.updateOperationalDetails(operationalName, operationalCheckIn, operationalNights, staffMemo);
        reservationRepository.save(reservation);
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
            roomRepository.findByRoomNumberForUpdate(roomNumber).ifPresent(room -> {
                room.truncatePeriodFrom(targetPeriod, effectiveDate);
                room.setStatus(RoomStatus.OUT);
                roomRepository.save(room);
            });
        }

        reservationRepository.save(reservation);
    }

    /**
     * 원장 수납/청구 거래 등록 (복식 분개 지원)
     */
    public void addFolioTransaction(String reservationId,
                                    String type,
                                    String paymentMethod,
                                    String category,
                                    String description,
                                    long amount,
                                    String instantChargeCategory,
                                    String instantChargeDescription) {
        Reservation reservation = findReservationOrThrow(reservationId);
        PaymentLedger ledger = reservation.getPaymentLedger();

        if (instantChargeCategory != null && !instantChargeCategory.isBlank() && !"NONE".equalsIgnoreCase(instantChargeCategory)) {
            // 사유가 선택된 경우: 청구(+)와 수납(-) 동시 등록 (±0 상쇄)
            ledger.recordInstantSettlement(
                    instantChargeCategory,
                    instantChargeDescription != null ? instantChargeDescription : "현장 즉시 결제 항목",
                    paymentMethod != null ? paymentMethod : "CREDIT_CARD",
                    description,
                    amount
            );
        } else if ("CHARGE".equalsIgnoreCase(type)) {
            // 단순 비용 청구 (+)
            ledger.addCharge(category != null ? category : "EXTRA_CHARGE", description, amount);
        } else {
            // 단순 수납 (-)
            ledger.recordPayment(paymentMethod != null ? paymentMethod : "CREDIT_CARD", description, amount);
        }

        reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public List<Reservation> searchReservations(ReservationSearchCondition condition) {
        return reservationRepository.search(condition);
    }

    @Transactional(readOnly = true)
    public Optional<Reservation> getReservation(String reservationId) {
        return reservationRepository.findById(reservationId);
    }

    public void cancelRoomAssignment(String reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (!reservation.isAssigned()) return;
        if (reservation.getStatus().isInHouse() || reservation.getStatus() == ReservationStatus.CHECKED_OUT) {
            throw new IllegalStateException("이미 입실하거나 퇴실한 고객의 객실 배정은 직접 취소할 수 없습니다.");
        }

        releaseRoomSchedule(reservation);
        reservation.cancelAssignment();
        reservationRepository.save(reservation);
    }

    public void cancelReservation(String reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (reservation.getStatus().isInHouse() || reservation.getStatus() == ReservationStatus.CHECKED_OUT) {
            throw new IllegalStateException("투숙 중이거나 이미 퇴실 완료된 예약은 취소할 수 없습니다.");
        }

        releaseRoomSchedule(reservation);
        reservation.cancelReservation();
        reservationRepository.save(reservation);
    }

    public void updateOperationalTags(String reservationId, Set<String> preferredTags, Set<String> avoidTags) {
        Reservation reservation = findReservationOrThrow(reservationId);
        TagPreference updatedPref = new TagPreference(preferredTags, avoidTags);
        reservation.updateOperationalTags(updatedPref);
        reservationRepository.save(reservation);
    }

    public QuotaPolicy getQuotaPolicy() {
        return quotaPolicy;
    }

    private void releaseRoomSchedule(Reservation reservation) {
        String roomNumber = reservation.getAssignedRoomNumber();
        if (roomNumber != null) {
            StayPeriod stayPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
            roomRepository.findByRoomNumberForUpdate(roomNumber).ifPresent(room -> {
                room.cancelPeriod(stayPeriod);
                if (!room.isAssigned() && room.getStatus() == RoomStatus.ASSIGNED) {
                    room.setStatus(RoomStatus.VACANT);
                }
                roomRepository.save(room);
            });
        }
    }

    private Reservation findReservationOrThrow(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("예약 ID는 필수입니다.");
        }
        return reservationRepository.findById(reservationId.trim())
                .orElseThrow(() -> new NoSuchElementException("예약 원장에서 해당 예약을 찾을 수 없습니다: " + reservationId));
    }
}