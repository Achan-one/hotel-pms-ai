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
        PaymentLedger paidLedger = new PaymentLedger(PaymentLedger.PaymentType.PREPAID, 300_000L);
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
        PaymentLedger unpaidLedger = new PaymentLedger(PaymentLedger.PaymentType.PAY_ON_ARRIVAL, 150_000L);
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

        // 헤더 검증
        assertTrue(csv.startsWith("우선순위,호실,층,타입,룸랙상태,작업구분,특이사항\r\n"));

        // 개인정보 마스킹 검증: 투숙객 실명이 청소 리스트 CSV 텍스트 어디에도 포함되지 않아야 함
        assertFalse(csv.contains("Tanaka"), "청소 리스트에 재실 고객 실명이 노출되어서는 안 됩니다.");
        assertFalse(csv.contains("Suzuki"), "청소 리스트에 퇴실 고객 실명이 노출되어서는 안 됩니다.");
        assertFalse(csv.contains("Alice"), "청소 리스트에 도착 고객 실명이 노출되어서는 안 됩니다.");

        // 우선순위와 객실 번호 정상 직렬화 확인
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
        // 도메인 정식 타이틀 "투숙중" 반영
        assertTrue(csv.contains("RSV-DEP-01,Suzuki,0505,2026-09-20,2026-09-21,투숙중,150000"));
    }

    @Test
    @DisplayName("[재실자 리포트] 연박 투숙객의 투숙 일차(2일차)가 계산되고 CSV로 출력되어야 한다")
    void getInHouseGuestListAndExportCsv_Success() {
        List<InHouseGuestDto> inHouse = reportExportService.getInHouseGuestList(today);
        assertEquals(1, inHouse.size());

        InHouseGuestDto guest = inHouse.get(0);
        assertEquals("0301", guest.roomNumber());
        assertEquals(2, guest.currentStayDay(), "어제 입실한 3박 투숙객의 오늘 일차는 2일차여야 합니다.");

        String csv = reportExportService.exportInHouseGuestListToCsv(today);
        assertTrue(csv.contains("0301,3,Tanaka,RSV-STAY-01,모더레이트 더블,2026-09-20,2026-09-23,2일차,3"));
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
    @DisplayName("[예약 원장 CSV] 전체 덤프 방어 정책을 통과한 조건부 조회가 CSV로 출력되어야 한다")
    void exportReservationsToCsv_Success() {
        ReservationSearchCondition condition = new ReservationSearchCondition(
                null, null, today, null, 2, null, null, null,null,null
        );
        String csv = reportExportService.exportReservationsToCsv(condition);

        assertTrue(csv.contains("예약번호,고객명,객실타입,체크인,박수,배정호실,상태,요청사항\r\n"));
        assertTrue(csv.contains("RSV-ARR-01,Alice,슈페리얼 트윈"));
    }

}