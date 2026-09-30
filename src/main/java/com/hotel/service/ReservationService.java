package com.hotel.service;

import com.hotel.channel.dto.ChannelReservationRequest;
import com.hotel.domain.*;
import com.hotel.entity.CityLedgerRecordEntity;
import com.hotel.repository.CityLedgerRepository;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.service.dto.PageResult;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;
import com.hotel.service.validator.ReservationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.stream.Stream;

@Service
@Transactional
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final AiPreferenceParser aiParser;
    private final BatchAssigner batchAssigner;
    private final QuotaPolicy quotaPolicy;
    private final ReservationValidator validator;
    private final CityLedgerRepository cityLedgerRepository;

    @Autowired
    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              AiPreferenceParser aiParser,
                              TagRepository tagRepository,
                              QuotaPolicy quotaPolicy,
                              CityLedgerRepository cityLedgerRepository) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
        this.reservationRepository = Objects.requireNonNull(reservationRepository, "reservationRepository는 필수입니다.");
        this.quotaPolicy = Objects.requireNonNull(quotaPolicy, "quotaPolicy는 필수입니다.");
        this.aiParser = Objects.requireNonNull(aiParser, "aiParser는 필수입니다.");
        this.cityLedgerRepository = Objects.requireNonNull(cityLedgerRepository, "cityLedgerRepository는 필수입니다.");
        this.validator = new ReservationValidator();
        this.batchAssigner = new BatchAssigner(roomRepository,
                Objects.requireNonNull(tagRepository, "tagRepository는 필수입니다."), this.quotaPolicy);
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
                    log.warn("CMS 취소 처리 실패 reservationId={}", req.reservationId(), e);
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

        // 하나라도 반영에 실패하면 예외를 그대로 올려 트랜잭션 전체를 되돌린다.
        // 예외를 잡고 이어가면 롤백 전용으로 표시된 트랜잭션에서 부분 반영이 남을 수 있다.
        for (Reservation success : result.getSuccessfulAssignments()) {
            String roomNumber = success.getAssignedRoomNumber();
            if (roomNumber != null) {
                Room room = roomRepository.findByRoomNumberForUpdate(roomNumber)
                        .orElseThrow(() -> new IllegalStateException("배정된 객실을 찾을 수 없습니다: " + roomNumber));
                StayPeriod period = new StayPeriod(success.getCheckInDate(), success.getStayNights());
                // 배정기가 같은 기간을 이미 점유해 둔 경우에는 다시 잡지 않는다.
                if (!room.getBookedPeriods().contains(period) && !room.tryBookPeriod(period)) {
                    throw new IllegalStateException(String.format(
                            "[%s호] 배정 반영 중 다른 예약과 일정이 겹쳤습니다. reservationId=%s",
                            roomNumber, success.getReservationId()));
                }
                roomRepository.save(room);
            }
            reservationRepository.save(success);
        }

        return result;
    }

    /**
     * 체크인 일자가 checkInDate인 예약 중 배정 완료(ASSIGNED) 상태인 것을 모두 미배정으로 되돌린다.
     * 이미 체크인했거나 퇴실한 예약, 취소된 예약은 건드리지 않는다. 전체가 한 트랜잭션이라 중간에 실패하면 모두 되돌아간다.
     */
    public BatchUnassignResult runDailyBatchUnassign(LocalDate checkInDate) {
        Objects.requireNonNull(checkInDate, "체크인 일자는 필수입니다.");
        List<Reservation> sameDay = reservationRepository.findByCheckInDate(checkInDate);

        // 객실 잠금 순서를 방 번호 순으로 고정한다.
        List<Reservation> targets = sameDay.stream()
                .filter(r -> r.getStatus() == ReservationStatus.ASSIGNED)
                .sorted(java.util.Comparator.comparing((Reservation r) -> r.getAssignedRoomNumber() == null ? "" : r.getAssignedRoomNumber())
                        .thenComparing(Reservation::getReservationId))
                .toList();
        int keptInHouse = (int) sameDay.stream()
                .filter(r -> r.getStatus() == ReservationStatus.CHECKED_IN || r.getStatus() == ReservationStatus.CHECKED_OUT)
                .count();

        List<String> released = new ArrayList<>();
        for (Reservation reservation : targets) {
            releaseRoomSchedule(reservation);
            reservation.cancelAssignment();
            reservationRepository.save(reservation);
            released.add(reservation.getReservationId());
        }
        return new BatchUnassignResult(checkInDate, List.copyOf(released), keptInHouse);
    }

    public void processCheckIn(String reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (!reservation.isAssigned()) {
            throw new IllegalStateException("객실 배정이 완료되지 않은 예약은 체크인할 수 없습니다: " + reservationId);
        }
        if (reservation.getStatus() == ReservationStatus.CHECKED_IN) {
            throw new IllegalStateException("이미 체크인 완료된 예약입니다: " + reservationId);
        }

        String roomNumber = reservation.getAssignedRoomNumber();
        if (roomNumber != null) {
            Room room = roomRepository.findByRoomNumberForUpdate(roomNumber)
                    .orElseThrow(() -> new IllegalArgumentException("해당 호실(" + roomNumber + ")이 존재하지 않습니다."));

            // 물리적 객실이 이미 재실(OCCUPIED) 상태인 경우 체크인 차단
            if (room.getStatus() == RoomStatus.OCCUPIED) {
                throw new IllegalStateException(String.format(
                        "[%s호] 현재 다른 투숙객이 재실 중인 객실입니다. 이전 투숙객의 퇴실 및 청소가 완료되어야 체크인이 가능합니다.",
                        roomNumber
                ));
            }

            // 해당 방의 상태가 입실 가능한 상태(VACANT, ASSIGNED)인지 검증
            if (room.getStatus() == RoomStatus.OUT || room.getStatus() == RoomStatus.CLEANING) {
                throw new IllegalStateException(String.format(
                        "[%s호] 청소가 완료되지 않은 객실입니다. (현재 상태: %s)",
                        roomNumber, room.getStatus().getTitle()
                ));
            }
            if (room.getStatus().isOutOfService()) {
                throw new IllegalStateException(String.format(
                        "[%s호] 고장/점검 중인 객실입니다. (현재 상태: %s)",
                        roomNumber, room.getStatus().getTitle()
                ));
            }

            // 스케줄 점유 확정
            StayPeriod period = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
            if (!room.getBookedPeriods().contains(period)) {
                if (!room.tryBookPeriod(period)) {
                    throw new IllegalStateException(String.format(
                            "[%s호] 해당 투숙 기간(%s)에 이미 다른 예약 스케줄이 점유되어 있어 입실할 수 없습니다.",
                            roomNumber, period
                    ));
                }
            }

            room.setStatus(RoomStatus.OCCUPIED);
            roomRepository.save(room);
        }

        reservation.checkIn();
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

        lockRoomsInOrder(originRoomNumber, targetRoomNumber);
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

    // 두 방을 항상 방 번호 순으로 잠근다. 반대 방향 룸체인지가 겹쳐도 교착이 나지 않는다.
    private void lockRoomsInOrder(String roomA, String roomB) {
        Stream.of(roomA, roomB)
                .filter(Objects::nonNull)
                .map(String::trim)
                .distinct()
                .sorted()
                .forEach(roomRepository::findByRoomNumberForUpdate);
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

    public void processCheckOut(String reservationId, LocalDate checkOutDate) {
        LocalDate effectiveDate = Objects.requireNonNull(checkOutDate, "퇴실 일자는 필수입니다.");
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

        // OTA 사전결제(PREPAID) 건은 체크아웃 시 City Ledger에 정산 내역으로 기록한다
        if (reservation.getPaymentLedger() != null && reservation.getPaymentLedger().getPaymentType() == PaymentLedger.PaymentType.PREPAID) {
            long billedAmount = reservation.getPaymentLedger().getTotalCharges();
            BookingChannelInfo.ChannelType channel = (reservation.getChannelInfo() != null)
                    ? reservation.getChannelInfo().channelType()
                    : BookingChannelInfo.ChannelType.DIRECT;
            String channelRsvNo = (reservation.getChannelInfo() != null)
                    ? reservation.getChannelInfo().channelReservationNo()
                    : reservation.getReservationId();

            CityLedgerRecordEntity record = new CityLedgerRecordEntity(
                    channel,
                    reservation.getReservationId(),
                    reservation.getGuestName(),
                    channelRsvNo,
                    reservation.getCheckInDate(),
                    effectiveDate,
                    billedAmount,
                    effectiveDate
            );
            cityLedgerRepository.save(record);
        }

        reservationRepository.save(reservation);
    }

    /**
     * 원장 수납/청구 거래 등록. 거래는 수정하지 않고 건별로 누적한다.
     */
    public void addFolioTransaction(String reservationId,
                                    String type,
                                    String paymentMethod,
                                    String category,
                                    String description,
                                    long amount,
                                    String instantChargeCategory,
                                    String instantChargeDescription) {
        if (amount <= 0) {
            throw new IllegalArgumentException("거래 금액은 0보다 커야 합니다.");
        }

        Reservation reservation = findReservationOrThrow(reservationId);
        PaymentLedger ledger = reservation.getPaymentLedger();

        boolean instantSettlement = instantChargeCategory != null
                && !instantChargeCategory.isBlank()
                && !"NONE".equalsIgnoreCase(instantChargeCategory);

        if ("CHARGE".equalsIgnoreCase(type)) {
            String cat = (category != null && !category.isBlank()) ? category : "EXTRA_CHARGE";
            String desc = (description != null && !description.isBlank()) ? description : "이용 요금 청구";
            ledger.addCharge(cat, desc, amount);
        } else if ("PAYMENT".equalsIgnoreCase(type)) {
            String method = (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod : "CASH";
            String desc = (description != null && !description.isBlank()) ? description : (method.equals("CREDIT_CARD") ? "신용카드 승인" : "현금 지불 수납");
            ledger.recordPayment(method, desc, amount);
        } else if (instantSettlement) {
            // 청구와 수납을 같은 금액으로 동시에 기록해 잔액은 그대로 둔다.
            ledger.recordInstantSettlement(
                    instantChargeCategory,
                    instantChargeDescription != null ? instantChargeDescription : "현장 즉시 결제 항목",
                    paymentMethod != null ? paymentMethod : "CREDIT_CARD",
                    description,
                    amount
            );
        } else {
            throw new IllegalArgumentException("지원하지 않는 거래 유형입니다: " + type);
        }

        reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public List<Reservation> searchReservations(ReservationSearchCondition condition) {
        return reservationRepository.search(condition);
    }

    @Transactional(readOnly = true)
    public PageResult<Reservation> searchReservations(ReservationSearchCondition condition, int page, int size) {
        return reservationRepository.search(condition, page, size);
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
            // 0박으로 이월된 예약은 룸 랙에 잡힌 스케줄이 없다. 기간을 만들려 하면 예외가 나므로 건너뛴다.
            StayPeriod stayPeriod = reservation.getStayNights() >= 1
                    ? new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights())
                    : null;
            roomRepository.findByRoomNumberForUpdate(roomNumber).ifPresent(room -> {
                if (stayPeriod != null) {
                    room.cancelPeriod(stayPeriod);
                }
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