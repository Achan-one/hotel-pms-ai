package com.hotel.service.report;

import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.memory.InMemoryReservationRepository;
import com.hotel.repository.memory.InMemoryRoomRepository;
import com.hotel.service.RoomAssigner;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.report.dto.ArrivalReportItemDto;
import com.hotel.service.report.dto.DepartureReportItemDto;
import com.hotel.service.report.dto.HousekeepingWorkItemDto;
import com.hotel.service.report.dto.HousekeepingWorkItemDto.CleanPriority;
import com.hotel.service.report.dto.InHouseGuestDto;
import com.hotel.service.report.dto.RoomBalanceReportDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReportExportServiceTest {

    private ReservationRepository reservationRepository;
    private RoomRepository roomRepository;
    private ReportExportService reportExportService;

    private final LocalDate today = LocalDate.of(2026, 9, 21);
    private final LocalDate yesterday = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        roomRepository = new InMemoryRoomRepository();
        RoomAssigner assigner = new RoomAssigner(roomRepository);
        reportExportService = new ReportExportService(reservationRepository, roomRepository, assigner);

        // 1. 어제 체크인한 3박 연박 재실 고객 (9/20 ~ 9/23) -> 오늘(9/21) 기준 2일 차 In-House
        PaymentLedger paidLedger = new PaymentLedger(PaymentLedger.PaymentType.PREPAID, 0);
        paidLedger.addCharge("ROOM_RATE", "연박 객실료 총액", 300_000L); // 명시적 청구 추가
        Reservation stayOver = new Reservation(
                "RSV-STAY-01", "Tanaka", RoomType.MODERATE_DOUBLE,
                yesterday, 3, 1, "조용히", GuestPreference.empty(),
                null, null, null, paidLedger, null
        );
        stayOver.assignRoom("0301");
        stayOver.checkIn();
        roomRepository.findByRoomNumber("0301").ifPresent(r -> {
            r.bookPeriod(new StayPeriod(yesterday, 3));
            r.setStatus(RoomStatus.OCCUPIED);
        });

        // 2. 어제 체크인하고 오늘(9/21) 출발 예정인 1박 현장결제 미수금 고객 (9/20 ~ 9/21)
        PaymentLedger unpaidLedger = new PaymentLedger(PaymentLedger.PaymentType.PAY_ON_ARRIVAL, 0);
        unpaidLedger.addCharge("ROOM_RATE", "현장 결제 1박 룸차지", 150_000L); // 명시적 청구 추가
        Reservation departingToday = new Reservation(
                "RSV-DEP-01", "Suzuki", RoomType.SUPERIOR_DOUBLE,
                yesterday, 1, 1, "엘리베이터 근처", GuestPreference.empty(),
                null, null, null, unpaidLedger, null
        );
        departingToday.assignRoom("0505");
        departingToday.checkIn();
        roomRepository.findByRoomNumber("0505").ifPresent(r -> {
            r.bookPeriod(new StayPeriod(yesterday, 1));
            r.setStatus(RoomStatus.OCCUPIED);
        });

        // 3. 오늘 체크인 예정 도착 고객 (Arrival - 미배정 PENDING 상태)
        TagPreference tagPref = new TagPreference(Set.of("HIGH_FLOOR"), Set.of());
        Reservation arrival = new Reservation(
                "RSV-ARR-01", "Alice", RoomType.SUPERIOR_TWIN,
                today, 2, 1, "고층 희망",
                GuestPreference.empty(), tagPref, null, null, null, null
        );

        // 4. 취소된 예약 (감사 추적용 CANCELLED 보존)
        Reservation cancelled = new Reservation(
                "RSV-CAN-01", "Bob", RoomType.EXECUTIVE_DOUBLE,
                today, 1, "취소건", GuestPreference.empty()
        );
        cancelled.cancelReservation();

        // 저장소에 저장
        reservationRepository.saveAll(List.of(stayOver, departingToday, arrival, cancelled));
    }

    @Test
    @DisplayName("[하우스키핑 보안] 청소 리스트 CSV에는 고객 이름이 절대 노출되지 않고 호실과 작업 구분만 출력되어야 한다")
    void exportHousekeepingWorkSheetToCsv_StrictlyHidesGuestNames() {
        String csv = reportExportService.exportHousekeepingWorkSheetToCsv(today);

        assertTrue(csv.startsWith("우선순위,호실,층,타입,룸랙상태,작업구분,특이사항\r\n"));

        assertFalse(csv.contains("Tanaka"), "청소 리스트에 재실 고객 실명이 노출되어서는 안 됩니다.");
        assertFalse(csv.contains("Suzuki"), "청소 리스트에 퇴실 고객 실명이 노출되어서는 안 됩니다.");
        assertFalse(csv.contains("Alice"), "청소 리스트에 도착 고객 실명이 노출되어서는 안 됩니다.");

        assertTrue(csv.contains("P1_URGENT,0505,5"));
    }

    @Test
    @DisplayName("[하우스키핑 지시서] 오늘 퇴실 예정 방(0505)은 P1_URGENT(D/C), 연박 방(0301)은 P3_STAYOVER(S/C)로 분류되어야 한다")
    void getHousekeepingWorkSheet_ClassifiesPrioritiesCorrectly() {
        List<HousekeepingWorkItemDto> sheet = reportExportService.getHousekeepingWorkSheet(today);

        HousekeepingWorkItemDto departureTask = sheet.stream()
                .filter(t -> t.roomNumber().equals("0505"))
                .findFirst()
                .orElseThrow();
        assertEquals(CleanPriority.P1_URGENT, departureTask.cleanPriority());
        assertTrue(departureTask.taskType().contains("D/C"));

        HousekeepingWorkItemDto stayTask = sheet.stream()
                .filter(t -> t.roomNumber().equals("0301"))
                .findFirst()
                .orElseThrow();
        assertEquals(CleanPriority.P3_STAYOVER, stayTask.cleanPriority());
        assertTrue(stayTask.taskType().contains("S/C"));
    }

    @Test
    @DisplayName("[도착자 리포트] 오늘 체크인 예정인 예약만 추출되고 CSV로 정상 출력되어야 한다")
    void getArrivalListAndExportCsv_Success() {
        List<ArrivalReportItemDto> arrivals = reportExportService.getArrivalList(today);
        assertEquals(1, arrivals.size());
        assertEquals("RSV-ARR-01", arrivals.get(0).reservationId());
        assertEquals("Alice", arrivals.get(0).guestName());
        assertFalse(arrivals.get(0).isAssigned());

        String csv = reportExportService.exportArrivalListToCsv(today);
        assertTrue(csv.contains("예약ID,고객명,객실타입,배정호실,체크인,박수,상태,정산여부,고객요청\r\n"));
        assertTrue(csv.contains("RSV-ARR-01,Alice,슈페리얼 트윈,UNASSIGNED"));
    }

    @Test
    @DisplayName("[출발 예정자 리포트] 미수금(Balance Due 150,000원)이 정확히 산출되어 CSV에 기록되어야 한다")
    void getDepartureListAndExportCsv_Success() {
        List<DepartureReportItemDto> departures = reportExportService.getDepartureList(today);
        assertEquals(1, departures.size());

        DepartureReportItemDto suzuki = departures.get(0);
        assertEquals("RSV-DEP-01", suzuki.reservationId());
        assertEquals(150_000L, suzuki.balanceDue(), "현장 결제 미수금 150,000원이 정확히 계산되어야 합니다.");

        String csv = reportExportService.exportDepartureListToCsv(today);
        assertTrue(csv.contains("예약ID,고객명,호실,체크인,체크아웃,상태,미수금(BalanceDue)\r\n"));
        assertTrue(csv.contains("RSV-DEP-01,Suzuki,0505,2026-09-20,2026-09-21,투숙중,150000"));
    }

    @Test
    @DisplayName("[숙박자 리포트] 하루만 조회하면 그날 밤 묵는 사람이 나온다. 그날 퇴실하는 사람과 취소 건은 나오지 않는다")
    void getInHouseGuestList_SingleDay() {
        List<InHouseGuestDto> list = reportExportService.getInHouseGuestList(today, today);

        // 9/21 밤: 연박 중인 Tanaka(0301)와 오늘 도착하는 미배정 Alice. 오늘 퇴실하는 Suzuki와 취소된 Bob은 제외.
        assertEquals(List.of("RSV-STAY-01", "RSV-ARR-01"),
                list.stream().map(InHouseGuestDto::reservationId).toList());

        InHouseGuestDto stay = list.get(0);
        assertEquals("0301", stay.roomNumber());
        assertEquals(2, stay.currentStayDay(), "어제 입실한 3박 투숙객의 오늘 일차는 2일차여야 합니다.");
        assertEquals(1, stay.nightsInRange());

        InHouseGuestDto arrival = list.get(1);
        assertNull(arrival.roomNumber(), "객실이 없으면 미배정으로 나와야 합니다.");
        assertEquals(1, arrival.currentStayDay());
    }

    @Test
    @DisplayName("[숙박자 리포트] 기간으로 조회하면 그 기간에 하룻밤이라도 묵는 사람이 모두 나오고, 기간 내 숙박 수가 계산된다")
    void getInHouseGuestList_Range() {
        List<InHouseGuestDto> list = reportExportService.getInHouseGuestList(yesterday, today.plusDays(1));

        assertEquals(List.of("RSV-STAY-01", "RSV-DEP-01", "RSV-ARR-01"),
                list.stream().map(InHouseGuestDto::reservationId).toList());
        // Tanaka: 9/20~9/23 중 조회 기간(9/20~9/22)에 묵는 밤은 9/20, 9/21, 9/22
        assertEquals(3, list.get(0).nightsInRange());
        // Suzuki: 9/20 하룻밤
        assertEquals(1, list.get(1).nightsInRange());
        // Alice: 9/21~9/23 중 9/21, 9/22
        assertEquals(2, list.get(2).nightsInRange());
    }

    @Test
    @DisplayName("[숙박자 리포트] 조회 시작일이 퇴실일과 같으면 그 사람은 그날 밤 숙박이 아니라서 나오지 않는다")
    void getInHouseGuestList_CheckOutDayIsNotAStayNight() {
        List<InHouseGuestDto> list = reportExportService.getInHouseGuestList(today, today);

        assertTrue(list.stream().noneMatch(g -> g.reservationId().equals("RSV-DEP-01")));
    }

    @Test
    @DisplayName("[숙박자 리포트] 과거 날짜를 조회해도 이미 퇴실한 사람이 그날 묵었다면 나온다")
    void getInHouseGuestList_IncludesGuestsWhoAlreadyCheckedOut() {
        Reservation past = new Reservation("RSV-PAST-01", "Kato", RoomType.MODERATE_DOUBLE,
                yesterday.minusDays(5), 2, 1, null, GuestPreference.empty(), null, null, null, null, null);
        past.assignRoom("0401");
        past.checkIn();
        past.checkOut(yesterday.minusDays(3));
        reservationRepository.save(past);

        List<InHouseGuestDto> list = reportExportService.getInHouseGuestList(yesterday.minusDays(5), yesterday.minusDays(5));

        assertEquals(List.of("RSV-PAST-01"), list.stream().map(InHouseGuestDto::reservationId).toList());
        assertEquals(com.hotel.domain.ReservationStatus.CHECKED_OUT, list.get(0).status());
    }

    @Test
    @DisplayName("[숙박자 리포트] 미래 날짜도 조회할 수 있다")
    void getInHouseGuestList_FutureDatesAllowed() {
        List<InHouseGuestDto> list = reportExportService.getInHouseGuestList(today.plusDays(1), today.plusDays(1));

        assertEquals(List.of("RSV-STAY-01", "RSV-ARR-01"),
                list.stream().map(InHouseGuestDto::reservationId).toList());
    }

    @Test
    @DisplayName("[숙박자 리포트] CSV에는 상태와 미배정 표기, 조회기간 내 숙박수가 들어간다")
    void exportInHouseGuestListToCsv_Columns() {
        String csv = reportExportService.exportInHouseGuestListToCsv(today, today);

        assertTrue(csv.startsWith("호실,층,투숙객명,예약ID,객실타입,상태,체크인,체크아웃,투숙일차(조회시작일 기준),총박수,조회기간내숙박수\r\n"));
        assertTrue(csv.contains("0301,3,Tanaka,RSV-STAY-01,모더레이트 더블,투숙중,2026-09-20,2026-09-23,2일차,3,1"));
        assertTrue(csv.contains("미배정,,Alice,RSV-ARR-01"));
    }

    @Test
    @DisplayName("[숙박자 리포트] 시작일이 종료일보다 늦거나 31일을 넘기면 거부한다")
    void getInHouseGuestList_RejectsInvalidRange() {
        assertThrows(IllegalArgumentException.class, () -> reportExportService.getInHouseGuestList(today.plusDays(1), today));
        assertThrows(IllegalArgumentException.class, () -> reportExportService.getInHouseGuestList(today, today.plusDays(31)));
        assertDoesNotThrow(() -> reportExportService.getInHouseGuestList(today, today.plusDays(30)));
    }

    @Test
    @DisplayName("[룸 밸런스] 총 191실 기준 타입별 인벤토리 정합성이 유지되고 CSV로 직렬화되어야 한다")
    void getRoomBalanceReportAndExportCsv_Success() {
        List<RoomBalanceReportDto> balanceList = reportExportService.getRoomBalanceReport(today);

        assertFalse(balanceList.isEmpty());
        assertTrue(balanceList.stream().allMatch(RoomBalanceReportDto::isBalanced));

        String csv = reportExportService.exportRoomBalanceToCsv(today);
        assertTrue(csv.contains("기준일자,객실타입,총객실,점검(OOS),연박재실,신규도착,안전보존(Hold),판매가능(Sellable),정합성\r\n"));
        assertTrue(csv.contains("모더레이트 더블"));
    }

    @Test
    @DisplayName("[스페셜 리퀘스트 CSV] 특이 요청사항 및 희망 태그가 CSV로 직렬화되어야 한다")
    void exportSpecialRequestSummaryToCsv_Success() {
        String csv = reportExportService.exportSpecialRequestSummaryToCsv(today);

        assertTrue(csv.contains("예약ID,고객명,신청객실타입,배정호실,배정객실보유태그,원문요청,희망태그,기피태그,필수조건미충족,조치사유\r\n"));
        assertTrue(csv.contains("RSV-ARR-01,Alice,슈페리얼 트윈,미배정"));
    }

    @Test
    @DisplayName("[취소 감사 장부 CSV] 기간 내 취소 건(RSV-CAN-01)만 정확히 추출되어야 한다")
    void exportCancellationAuditLedgerToCsv_ExtractsCancelledOnly() {
        String csv = reportExportService.exportCancellationAuditLedgerToCsv(today.minusDays(1), today.plusDays(1));

        assertTrue(csv.contains("RSV-CAN-01,Bob,이그제큐티브 더블"));
        assertFalse(csv.contains("RSV-ARR-01"));
    }

    @Test
    @DisplayName("[예약자 리포트] 같은 날짜를 시작과 끝으로 넣으면 그날 체크인한 예약만 나온다")
    void exportReservationsToCsv_SingleDay() {
        String csv = reportExportService.exportReservationsToCsv(today, today, null, true);

        assertTrue(csv.contains("RSV-ARR-01,Alice"));
        assertTrue(csv.contains("RSV-CAN-01,Bob"), "취소 건도 상태로 구분되어 나온다");
        assertFalse(csv.contains("RSV-STAY-01"), "어제 체크인한 예약은 나오지 않는다");
        assertFalse(csv.contains("RSV-DEP-01"));
    }

    @Test
    @DisplayName("[예약자 리포트] 기간으로 넣으면 그 기간에 체크인하는 예약이 모두 나온다")
    void exportReservationsToCsv_Range() {
        String csv = reportExportService.exportReservationsToCsv(yesterday, today, null, true);

        assertTrue(csv.contains("RSV-STAY-01"));
        assertTrue(csv.contains("RSV-DEP-01"));
        assertTrue(csv.contains("RSV-ARR-01"));
        assertTrue(csv.contains("RSV-CAN-01"));
    }

    @Test
    @DisplayName("[예약자 리포트] 상태 필터를 주면 그 상태만 나온다")
    void exportReservationsToCsv_StatusFilter() {
        String csv = reportExportService.exportReservationsToCsv(yesterday, today, com.hotel.domain.ReservationStatus.CANCELLED, true);

        assertTrue(csv.contains("RSV-CAN-01"));
        assertFalse(csv.contains("RSV-ARR-01"));
    }

    @Test
    @DisplayName("[예약자 리포트] 예약의 전체 정보(채널, 결제, 태그, 잔액 등)가 컬럼으로 나온다")
    void exportReservationsToCsv_FullInformation() {
        String csv = reportExportService.exportReservationsToCsv(yesterday, yesterday, null, true);

        assertTrue(csv.startsWith("예약번호,고객명,OTA원본고객명,객실타입,체크인,체크아웃,박수,배정호실,이전호실,상태,실제퇴실일,"
                + "예약채널,채널예약번호,플랜명,조식,조식인원,결제유형,총청구,총수납,잔액,도착예정시각,레이트체크아웃,희망태그,기피태그,고객요청,내부메모\r\n"));
        // Tanaka: 사전결제 300,000 청구, 수납 0 → 잔액 300,000
        assertTrue(csv.contains("RSV-STAY-01,Tanaka,Tanaka,모더레이트 더블,2026-09-20,2026-09-23,3,0301,,투숙중,,DIRECT,"));
        assertTrue(csv.contains(",사전 카드 결제,300000,0,300000,"));
    }

    @Test
    @DisplayName("[예약자 리포트] 내부 메모는 권한이 없으면 비워서 내려간다")
    void exportReservationsToCsv_HidesStaffMemoWhenNotAllowed() {
        Reservation withMemo = reservationRepository.findById("RSV-ARR-01").orElseThrow();
        withMemo.updateOperationalDetails(null, null, null, "VIP 응대 필요");
        reservationRepository.save(withMemo);

        assertTrue(reportExportService.exportReservationsToCsv(today, today, null, true).contains("VIP 응대 필요"));
        assertFalse(reportExportService.exportReservationsToCsv(today, today, null, false).contains("VIP 응대 필요"));
    }

    @Test
    @DisplayName("[예약자 리포트] 기간이 잘못되면 거부한다")
    void exportReservationsToCsv_RejectsInvalidRange() {
        assertThrows(IllegalArgumentException.class, () -> reportExportService.exportReservationsToCsv(today, yesterday, null, true));
        assertThrows(IllegalArgumentException.class, () -> reportExportService.exportReservationsToCsv(today, today.plusDays(31), null, true));
    }
}
