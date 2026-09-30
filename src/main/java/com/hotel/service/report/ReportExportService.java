package com.hotel.service.report;

import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.RoomAssigner;
import com.hotel.service.dto.AssignmentAlert;
import com.hotel.service.dto.ScoreBreakdown;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.report.dto.*;
import com.hotel.service.report.dto.HousekeepingWorkItemDto.CleanPriority;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReportExportService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final RoomAssigner roomAssigner;

    public ReportExportService(ReservationRepository reservationRepository,
                               RoomRepository roomRepository,
                               RoomAssigner roomAssigner) {
        this.reservationRepository = Objects.requireNonNull(reservationRepository, "reservationRepository는 필수입니다.");
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
        this.roomAssigner = Objects.requireNonNull(roomAssigner, "roomAssigner는 필수입니다.");
    }

    // 1. 예약 원장: 체크인 일자가 [from, to]에 드는 예약의 전체 정보
    public String exportReservationsToCsv(LocalDate from, LocalDate to, ReservationStatus status, boolean includeStaffMemo) {
        ReportPolicy.validateDateRange(from, to);

        List<Reservation> list = reservationRepository.findByCheckInDateBetween(from, to).stream()
                .filter(r -> status == null || r.getStatus() == status)
                .toList();
        if (list.size() > ReportPolicy.MAX_ROW_LIMIT) {
            throw new IllegalStateException(String.format(
                    "조회 데이터 한도 초과 (%d건). 최대 출력 한도는 %d건입니다.",
                    list.size(), ReportPolicy.MAX_ROW_LIMIT));
        }

        List<String> headers = List.of(
                "예약번호", "고객명", "OTA원본고객명", "객실타입", "체크인", "체크아웃", "박수",
                "배정호실", "이전호실", "상태", "실제퇴실일",
                "예약채널", "채널예약번호", "플랜명", "조식", "조식인원",
                "결제유형", "총청구", "총수납", "잔액",
                "도착예정시각", "레이트체크아웃", "희망태그", "기피태그", "고객요청", "내부메모"
        );
        List<Function<Reservation, Object>> mappers = List.of(
                Reservation::getReservationId,
                Reservation::getGuestName,
                Reservation::getOriginalGuestName,
                r -> r.getBookedRoomType().getDescription(),
                Reservation::getCheckInDate,
                Reservation::getCheckOutDate,
                Reservation::getStayNights,
                r -> r.isAssigned() ? r.getAssignedRoomNumber() : "미배정",
                r -> r.getPreviousRoomNumber() != null ? r.getPreviousRoomNumber() : "",
                r -> r.getStatus().getTitle(),
                r -> r.getActualCheckOutDate() != null ? r.getActualCheckOutDate() : "",
                r -> r.getChannelInfo().channelType().name(),
                r -> r.getChannelInfo().channelReservationNo() != null ? r.getChannelInfo().channelReservationNo() : "",
                r -> r.getChannelInfo().planName() != null ? r.getChannelInfo().planName() : "",
                r -> r.getBreakfastOption().isIncluded() ? "포함" : "미포함",
                r -> r.getBreakfastOption().isIncluded() ? r.getBreakfastOption().getDailyBreakfastCount() : 0,
                r -> r.getPaymentLedger().getPaymentType().getDesc(),
                r -> r.getPaymentLedger().getTotalCharges(),
                r -> r.getPaymentLedger().getTotalPayments(),
                r -> r.getPaymentLedger().getBalance(),
                r -> r.getEstimatedArrivalTime() != null ? r.getEstimatedArrivalTime() : "",
                r -> r.getLateCheckOutTime() != null ? r.getLateCheckOutTime() : "",
                r -> String.join(" ", r.getTagPreference().preferredTags()),
                r -> String.join(" ", r.getTagPreference().avoidTags()),
                r -> r.getRawRequestText() != null ? r.getRawRequestText() : "",
                // 내부 인계 메모는 정직원 이상에게만 내려준다. OTA 전문 원문(rawXmlPayload)은 CSV에 넣지 않는다.
                r -> includeStaffMemo ? r.getInternalStaffMemo() : ""
        );

        return CsvSerializer.serialize(headers, mappers, list);
    }

    /**
     * 배정 점수 내역 (관리자 전용). 체크인 일자가 checkInDate이고 객실이 배정된 예약마다,
     * 배정된 방이 왜 그 점수를 받았는지 규칙별로 나눠서 보여 준다.
     * 배정 당시의 다른 후보 방과의 비교는 포함하지 않는다(그 시점의 공실 상황을 재현할 수 없다).
     * 투숙객 이름은 넣지 않는다. 점수 검증에는 필요 없다.
     */
    public String exportAssignmentScoresToCsv(LocalDate checkInDate) {
        Objects.requireNonNull(checkInDate, "체크인 일자는 필수입니다.");

        List<Reservation> assigned = reservationRepository.findByCheckInDate(checkInDate).stream()
                .filter(r -> r.getStatus() != ReservationStatus.CANCELLED && r.isAssigned())
                .toList();
        if (assigned.size() > ReportPolicy.MAX_ROW_LIMIT) {
            throw new IllegalStateException(String.format(
                    "조회 데이터 한도 초과 (%d건). 최대 출력 한도는 %d건입니다.", assigned.size(), ReportPolicy.MAX_ROW_LIMIT));
        }

        record ScoredRow(Reservation reservation, Room room, ScoreBreakdown breakdown, List<String> unmetHard) {}

        List<ScoredRow> rows = new ArrayList<>();
        for (Reservation r : assigned) {
            Optional<Room> room = roomRepository.findByRoomNumber(r.getAssignedRoomNumber());
            if (room.isEmpty()) continue;
            rows.add(new ScoredRow(r, room.get(),
                    roomAssigner.explainScore(room.get(), r.getPreference(), r.getTagPreference(), r.getStayNights()),
                    roomAssigner.unmetHardRequestTagNames(r, room.get())));
        }
        rows.sort(Comparator.comparing((ScoredRow row) -> row.reservation().getAssignedRoomNumber())
                .thenComparing(row -> row.reservation().getReservationId()));

        List<ScoreBreakdown.Category> categories = List.of(ScoreBreakdown.Category.values());
        List<String> headers = new ArrayList<>(List.of(
                "PMS예약번호", "예약ID", "객실타입", "체크인", "박수", "상태", "배정호실", "층", "객실 보유 태그", "희망 태그", "기피 태그", "총점"));
        categories.forEach(c -> headers.add(c.label()));
        headers.addAll(List.of("필수(HARD) 미충족", "점수 내역"));

        List<Function<ScoredRow, Object>> mappers = new ArrayList<>(List.of(
                row -> row.reservation().getPmsReservationNo() != null ? row.reservation().getPmsReservationNo() : "",
                row -> row.reservation().getReservationId(),
                row -> row.reservation().getBookedRoomType().getDescription(),
                row -> row.reservation().getCheckInDate(),
                row -> row.reservation().getStayNights(),
                row -> row.reservation().getStatus().getTitle(),
                row -> row.reservation().getAssignedRoomNumber(),
                row -> row.room().getFloor(),
                row -> String.join(" ", new TreeSet<>(row.room().getTags())),
                row -> String.join(" ", new TreeSet<>(row.reservation().getTagPreference().preferredTags())),
                row -> String.join(" ", new TreeSet<>(row.reservation().getTagPreference().avoidTags())),
                row -> row.breakdown().total()));
        for (ScoreBreakdown.Category category : categories) {
            mappers.add(row -> row.breakdown().totalsByCategory().getOrDefault(category, 0));
        }
        mappers.add(row -> String.join(", ", row.unmetHard()));
        mappers.add(row -> row.breakdown().describe());

        return CsvSerializer.serialize(headers, mappers, rows);
    }

    // 2. 당일 도착 예정자 명단 (Arrivals List)
    public List<ArrivalReportItemDto> getArrivalList(LocalDate targetDate) {
        LocalDate date = Objects.requireNonNull(targetDate, "기준 일자는 필수입니다.");
        List<Reservation> arrivals = reservationRepository.findByCheckInDate(date);

        return arrivals.stream()
                .filter(r -> r.getStatus() != ReservationStatus.CANCELLED)
                .sorted(Comparator.comparing(Reservation::isAssigned).reversed()
                        .thenComparing(Reservation::getReservationId))
                .map(r -> new ArrivalReportItemDto(
                        r.getReservationId(),
                        r.getGuestName(),
                        r.getBookedRoomType(),
                        r.getBookedRoomType().getDescription(),
                        r.isAssigned() ? r.getAssignedRoomNumber() : "UNASSIGNED",
                        r.getCheckInDate(),
                        r.getStayNights(),
                        r.getStatus(),
                        r.isAssigned(),
                        r.getRawRequestText(),
                        r.getPaymentLedger() != null && r.getPaymentLedger().isSettled()
                ))
                .toList();
    }

    public String exportArrivalListToCsv(LocalDate targetDate) {
        List<ArrivalReportItemDto> list = getArrivalList(targetDate);
        List<String> headers = List.of("예약ID", "고객명", "객실타입", "배정호실", "체크인", "박수", "상태", "정산여부", "고객요청");
        List<Function<ArrivalReportItemDto, Object>> mappers = List.of(
                ArrivalReportItemDto::reservationId,
                ArrivalReportItemDto::guestName,
                ArrivalReportItemDto::roomTypeName,
                ArrivalReportItemDto::assignedRoomNumber,
                ArrivalReportItemDto::checkInDate,
                ArrivalReportItemDto::stayNights,
                item -> item.status().getTitle(),
                item -> item.isSettled() ? "완료" : "미정산",
                item -> item.specialRequestText() != null ? item.specialRequestText() : ""
        );
        return CsvSerializer.serialize(headers, mappers, list);
    }

    // 3. 당일 출발 예정자 명단 (Departures List)
    public List<DepartureReportItemDto> getDepartureList(LocalDate targetDate) {
        LocalDate date = Objects.requireNonNull(targetDate, "기준 일자는 필수입니다.");
        List<Reservation> allReservations = reservationRepository.search(ReservationSearchCondition.empty());

        return allReservations.stream()
                .filter(r -> date.equals(r.getCheckOutDate()) || date.equals(r.getActualCheckOutDate()))
                .filter(r -> r.getStatus() != ReservationStatus.CANCELLED)
                .map(r -> {
                    long balanceDue = 0L;
                    if (r.getStatus() != ReservationStatus.CHECKED_OUT && r.getPaymentLedger() != null) {
                        balanceDue = r.getPaymentLedger().getTotalDue();
                    }

                    LocalDate effectiveCheckOut = (r.getActualCheckOutDate() != null)
                            ? r.getActualCheckOutDate()
                            : r.getCheckOutDate();

                    return new DepartureReportItemDto(
                            r.getReservationId(),
                            r.getGuestName(),
                            r.isAssigned() ? r.getAssignedRoomNumber() : "미배정",
                            r.getCheckInDate(),
                            effectiveCheckOut,
                            r.getStatus(),
                            balanceDue,
                            false
                    );
                })
                .sorted(Comparator.comparing(DepartureReportItemDto::roomNumber))
                .toList();
    }

    public String exportDepartureListToCsv(LocalDate targetDate) {
        List<DepartureReportItemDto> list = getDepartureList(targetDate);
        List<String> headers = List.of("예약ID", "고객명", "호실", "체크인", "체크아웃", "상태", "미수금(BalanceDue)");
        List<Function<DepartureReportItemDto, Object>> mappers = List.of(
                DepartureReportItemDto::reservationId,
                DepartureReportItemDto::guestName,
                DepartureReportItemDto::roomNumber,
                DepartureReportItemDto::checkInDate,
                DepartureReportItemDto::checkOutDate,
                item -> item.status().getTitle(),
                DepartureReportItemDto::balanceDue
        );
        return CsvSerializer.serialize(headers, mappers, list);
    }

    // 4. 재실 숙박자 명단 (In-House Guest List)
    /**
     * [from, to] 중 하룻밤이라도 숙박하는 예약 명단. 취소 건은 제외하고, 객실이 아직 없으면 미배정으로 나온다.
     */
    public List<InHouseGuestDto> getInHouseGuestList(LocalDate from, LocalDate to) {
        ReportPolicy.validateDateRange(from, to);
        List<Reservation> stayingList = reservationRepository.findStayingBetween(from, to);

        return stayingList.stream()
                .map(r -> {
                    String room = r.isAssigned() ? r.getAssignedRoomNumber().trim() : null;
                    Integer floor = (room != null && room.length() >= 2) ? Integer.valueOf(room.substring(0, 2)) : null;
                    int calculatedDay = (int) ChronoUnit.DAYS.between(r.getCheckInDate(), from) + 1;
                    int currentStayDay = Math.min(Math.max(1, r.getStayNights()), Math.max(1, calculatedDay));

                    LocalDate firstNight = r.getCheckInDate().isAfter(from) ? r.getCheckInDate() : from;
                    LocalDate lastNightExclusive = r.getEffectiveCheckOutDate().isBefore(to.plusDays(1))
                            ? r.getEffectiveCheckOutDate() : to.plusDays(1);
                    int nightsInRange = (int) Math.max(0, ChronoUnit.DAYS.between(firstNight, lastNightExclusive));

                    return new InHouseGuestDto(
                            room,
                            floor,
                            r.getGuestName(),
                            r.getReservationId(),
                            r.getBookedRoomType(),
                            r.getStatus(),
                            r.getCheckInDate(),
                            r.getCheckOutDate(),
                            currentStayDay,
                            r.getStayNights(),
                            nightsInRange
                    );
                })
                .sorted(Comparator.comparing((InHouseGuestDto g) -> g.roomNumber() == null)
                        .thenComparing(g -> g.roomNumber() == null ? "" : g.roomNumber())
                        .thenComparing(InHouseGuestDto::reservationId))
                .toList();
    }

    public String exportInHouseGuestListToCsv(LocalDate from, LocalDate to) {
        List<InHouseGuestDto> list = getInHouseGuestList(from, to);
        if (list.size() > ReportPolicy.MAX_ROW_LIMIT) {
            throw new IllegalStateException(String.format(
                    "조회 데이터 한도 초과 (%d건). 최대 출력 한도는 %d건입니다.",
                    list.size(), ReportPolicy.MAX_ROW_LIMIT));
        }
        List<String> headers = List.of("호실", "층", "투숙객명", "예약ID", "객실타입", "상태", "체크인", "체크아웃",
                "투숙일차(조회시작일 기준)", "총박수", "조회기간내숙박수");
        List<Function<InHouseGuestDto, Object>> mappers = List.of(
                item -> item.roomNumber() != null ? item.roomNumber() : "미배정",
                item -> item.floor() != null ? item.floor() : "",
                InHouseGuestDto::guestName,
                InHouseGuestDto::reservationId,
                item -> item.roomType().getDescription(),
                item -> item.status().getTitle(),
                InHouseGuestDto::checkInDate,
                InHouseGuestDto::checkOutDate,
                item -> item.currentStayDay() + "일차",
                InHouseGuestDto::totalNights,
                InHouseGuestDto::nightsInRange
        );
        return CsvSerializer.serialize(headers, mappers, list);
    }

    // 5. 룸 밸런스 리포트 (Room Balance Reconciliation)
    public List<RoomBalanceReportDto> getRoomBalanceReport(LocalDate targetDate) {
        LocalDate date = Objects.requireNonNull(targetDate, "기준 일자는 필수입니다.");
        List<Room> allRooms = roomRepository.findAll();
        List<Reservation> inHouseGuests = reservationRepository.search(ReservationSearchCondition.byStayingDate(date));
        List<Reservation> arrivals = reservationRepository.findByCheckInDate(date);
        StayPeriod singleDay = new StayPeriod(date, 1);

        List<RoomBalanceReportDto> report = new ArrayList<>();

        for (RoomType type : RoomType.values()) {
            List<Room> typeRooms = allRooms.stream().filter(r -> r.getRoomType() == type).toList();
            int total = typeRooms.size();
            if (total == 0) continue;

            int oos = (int) typeRooms.stream().filter(r -> r.getStatus().isOutOfService()).count();
            int stayover = (int) inHouseGuests.stream()
                    .filter(r -> r.getBookedRoomType() == type)
                    .filter(r -> r.getCheckInDate().isBefore(date))
                    .count();

            int arrivalCount = (int) arrivals.stream()
                    .filter(r -> r.getBookedRoomType() == type)
                    .filter(r -> r.getStatus() != ReservationStatus.CANCELLED)
                    .count();

            int hold = roomAssigner.getQuotaPolicy().getTypeHoldQuota(type);
            long minDailyVacant = roomAssigner.calculateMinDailyVacant(type, date, 1);
            int sellable = (int) Math.max(0, minDailyVacant - hold);

            long occupiedScheduleRooms = typeRooms.stream()
                    .filter(r -> !r.getStatus().isOutOfService())
                    .filter(r -> !r.isAvailable(singleDay))
                    .count();

            boolean balanced = (total == (oos + (int) occupiedScheduleRooms + (int) minDailyVacant));

            report.add(new RoomBalanceReportDto(
                    date, type, type.getDescription(), total, oos, stayover, arrivalCount, hold, sellable, balanced
            ));
        }

        return report;
    }

    public String exportRoomBalanceToCsv(LocalDate targetDate) {
        List<RoomBalanceReportDto> list = getRoomBalanceReport(targetDate);
        List<String> headers = List.of("기준일자", "객실타입", "총객실", "점검(OOS)", "연박재실", "신규도착", "안전보존(Hold)", "판매가능(Sellable)", "정합성");
        List<Function<RoomBalanceReportDto, Object>> mappers = List.of(
                RoomBalanceReportDto::targetDate,
                RoomBalanceReportDto::roomTypeName,
                RoomBalanceReportDto::totalInventory,
                RoomBalanceReportDto::outOfServiceRooms,
                RoomBalanceReportDto::stayoverRooms,
                RoomBalanceReportDto::arrivalRooms,
                RoomBalanceReportDto::holdQuota,
                RoomBalanceReportDto::sellableInventory,
                item -> item.isBalanced() ? "정상" : "불일치주의"
        );
        return CsvSerializer.serialize(headers, mappers, list);
    }

    // 6. 스페셜 리퀘스트 요약 명단 (배정객실보유태그 컬럼 추가)
    public List<SpecialRequestReportDto> getSpecialRequestSummary(LocalDate targetDate) {
        LocalDate date = Objects.requireNonNull(targetDate, "기준 일자는 필수입니다.");
        List<Reservation> arrivals = reservationRepository.findByCheckInDate(date);

        List<SpecialRequestReportDto> list = new ArrayList<>();
        for (Reservation rsv : arrivals) {
            if (rsv.getStatus() == ReservationStatus.CANCELLED) continue;

            TagPreference tagPref = rsv.getTagPreference();
            String roomNo = rsv.isAssigned() ? rsv.getAssignedRoomNumber() : "미배정";
            String assignedTags = "";
            boolean hasHardFail = false;
            String alertReason = "정상";

            if (rsv.isAssigned()) {
                Room room = roomRepository.findByRoomNumber(roomNo).orElse(null);
                if (room != null) {
                    assignedTags = String.join("|", room.getTags());
                    List<AssignmentAlert> alerts = roomAssigner.checkHardRequestAlerts(rsv, room);
                    if (!alerts.isEmpty()) {
                        hasHardFail = true;
                        alertReason = alerts.getFirst().reason();
                    }
                }
            } else {
                alertReason = "미배정 (만실 또는 안전 쿼터 보존)";
            }

            Set<String> prefTags = (tagPref != null) ? tagPref.preferredTags() : Set.of();
            Set<String> avoidTags = (tagPref != null) ? tagPref.avoidTags() : Set.of();

            list.add(new SpecialRequestReportDto(
                    rsv.getReservationId(),
                    rsv.getGuestName(),
                    rsv.getBookedRoomType(),
                    roomNo,
                    assignedTags,
                    rsv.getRawRequestText() != null ? rsv.getRawRequestText() : "",
                    prefTags,
                    avoidTags,
                    hasHardFail,
                    alertReason
            ));
        }
        return list;
    }

    public String exportSpecialRequestSummaryToCsv(LocalDate targetDate) {
        List<SpecialRequestReportDto> list = getSpecialRequestSummary(targetDate);
        List<String> headers = List.of(
                "예약ID", "고객명", "신청객실타입", "배정호실", "배정객실보유태그", "원문요청", "희망태그", "기피태그", "필수조건미충족", "조치사유"
        );
        List<Function<SpecialRequestReportDto, Object>> mappers = List.of(
                SpecialRequestReportDto::reservationId,
                SpecialRequestReportDto::guestName,
                item -> item.roomType().getDescription(),
                SpecialRequestReportDto::assignedRoomNumber,
                SpecialRequestReportDto::assignedRoomTags,
                SpecialRequestReportDto::rawRequestText,
                item -> String.join("|", item.preferredTags()),
                item -> String.join("|", item.avoidTags()),
                item -> item.hasHardConstraintFail() ? "경고(Fail)" : "정상",
                SpecialRequestReportDto::alertMessage
        );
        return CsvSerializer.serialize(headers, mappers, list);
    }

    // 7. 하우스키핑 작업 지시서
    public List<HousekeepingWorkItemDto> getHousekeepingWorkSheet(LocalDate targetDate) {
        LocalDate date = Objects.requireNonNull(targetDate, "기준 일자는 필수입니다.");
        List<Room> allRooms = roomRepository.findAll();

        Set<String> todayDepartureRoomNumbers = reservationRepository.search(ReservationSearchCondition.empty()).stream()
                .filter(r -> r.isAssigned() && (date.equals(r.getCheckOutDate()) || date.equals(r.getActualCheckOutDate())))
                .filter(r -> r.getStatus() != ReservationStatus.CANCELLED)
                .map(Reservation::getAssignedRoomNumber)
                .collect(Collectors.toSet());

        List<HousekeepingWorkItemDto> tasks = new ArrayList<>();
        for (Room room : allRooms) {
            CleanPriority priority;
            String taskType;
            String memo;

            boolean isScheduledDeparture = todayDepartureRoomNumbers.contains(room.getRoomNumber());

            if (room.getStatus() == RoomStatus.OUT || isScheduledDeparture) {
                priority = CleanPriority.P1_URGENT;
                taskType = "퇴실청소 (D/C)";
                memo = (room.getStatus() == RoomStatus.OUT) ? "체크아웃 완료 (즉시 청소 가능)" : "출발 예정 (퇴실 대기 중)";
            } else if (room.getStatus() == RoomStatus.OCCUPIED) {
                priority = CleanPriority.P3_STAYOVER;
                taskType = "연박청소 (S/C)";
                memo = "투숙 연박 고객 재실";
            } else if (room.getStatus().isOutOfService()) {
                priority = CleanPriority.P4_INSPECTION;
                taskType = "점검객실 (OOO/OOS)";
                memo = "시설팀 점검 중";
            } else {
                priority = CleanPriority.P4_INSPECTION;
                taskType = "공실점검 (V/I)";
                memo = "체크인 대기 완료";
            }

            tasks.add(new HousekeepingWorkItemDto(
                    room.getRoomNumber(),
                    room.getFloor(),
                    room.getRoomType(),
                    room.getStatus(),
                    priority,
                    taskType,
                    memo
            ));
        }

        tasks.sort(Comparator.comparing(HousekeepingWorkItemDto::cleanPriority)
                .thenComparing(HousekeepingWorkItemDto::floor)
                .thenComparing(HousekeepingWorkItemDto::roomNumber));

        return tasks;
    }

    public String exportHousekeepingWorkSheetToCsv(LocalDate targetDate) {
        List<HousekeepingWorkItemDto> list = getHousekeepingWorkSheet(targetDate);
        List<String> headers = List.of("우선순위", "호실", "층", "타입", "룸랙상태", "작업구분", "특이사항");
        List<Function<HousekeepingWorkItemDto, Object>> mappers = List.of(
                item -> item.cleanPriority().name(),
                HousekeepingWorkItemDto::roomNumber,
                HousekeepingWorkItemDto::floor,
                item -> item.roomType().getDescription(),
                item -> item.roomStatus().getTitle(),
                HousekeepingWorkItemDto::taskType,
                HousekeepingWorkItemDto::memo
        );
        return CsvSerializer.serialize(headers, mappers, list);
    }

    // 8. 취소 및 노쇼 감사 장부
    public String exportCancellationAuditLedgerToCsv(LocalDate checkInFrom, LocalDate checkInTo) {
        ReportPolicy.validateDateRange(checkInFrom, checkInTo);

        List<Reservation> cancelledList = reservationRepository.search(ReservationSearchCondition.empty()).stream()
                .filter(r -> r.getStatus() == ReservationStatus.CANCELLED)
                .filter(r -> !r.getCheckInDate().isBefore(checkInFrom) && !r.getCheckInDate().isAfter(checkInTo))
                .sorted(Comparator.comparing(Reservation::getCheckInDate))
                .toList();

        List<String> headers = List.of("예약ID", "고객명", "원신청타입", "체크인예정일", "박수", "상태", "기록메모");
        List<Function<Reservation, Object>> mappers = List.of(
                Reservation::getReservationId,
                Reservation::getGuestName,
                r -> r.getBookedRoomType().getDescription(),
                Reservation::getCheckInDate,
                Reservation::getStayNights,
                r -> r.getStatus().getTitle(),
                r -> r.getRawRequestText() != null ? r.getRawRequestText() : ""
        );

        return CsvSerializer.serialize(headers, mappers, cancelledList);
    }

    // 9. 룸 태그 인디케이터 (1) - 191실 전수 방 기준 보유 태그 리포트
    public String exportRoomTagsToCsv() {
        List<Room> allRooms = roomRepository.findAll().stream()
                .sorted(Comparator.comparing(Room::getFloor)
                        .thenComparing(Room::getRoomNumber))
                .toList();

        List<String> headers = List.of("호실", "층", "객실타입", "엘리베이터", "코너룸", "룸랙상태", "보유태그수", "보유태그목록");
        List<Function<Room, Object>> mappers = List.of(
                Room::getRoomNumber,
                Room::getFloor,
                r -> r.getRoomType().getDescription(),
                r -> r.isNearElevator() ? "인접(3~8호)" : "이격",
                r -> r.isCorner() ? "코너(모퉁이)" : "일반",
                r -> r.getStatus().getTitle(),
                r -> r.getTags().size(),
                r -> String.join(" | ", r.getTags())
        );

        return CsvSerializer.serialize(headers, mappers, allRooms);
    }

    // 10. 룸 태그 인디케이터 (2) - 태그 기준 해당 객실 목록 매핑 리포트
    public record TagRoomMappingRow(String tagCode, int matchedRoomCount, String matchedRooms) {}

    public String exportTagToRoomsMatrixToCsv() {
        List<Room> allRooms = roomRepository.findAll();

        Set<String> allTags = new TreeSet<>();
        for (Room r : allRooms) {
            allTags.addAll(r.getTags());
        }

        List<TagRoomMappingRow> rows = new ArrayList<>();
        for (String tagCode : allTags) {
            List<String> matched = allRooms.stream()
                    .filter(r -> r.hasTag(tagCode))
                    .map(Room::getRoomNumber)
                    .sorted()
                    .toList();

            rows.add(new TagRoomMappingRow(
                    tagCode,
                    matched.size(),
                    String.join(", ", matched)
            ));
        }

        List<String> headers = List.of("태그코드", "해당객실총수", "배정가능객실목록");
        List<Function<TagRoomMappingRow, Object>> mappers = List.of(
                TagRoomMappingRow::tagCode,
                TagRoomMappingRow::matchedRoomCount,
                TagRoomMappingRow::matchedRooms
        );

        return CsvSerializer.serialize(headers, mappers, rows);
    }
}