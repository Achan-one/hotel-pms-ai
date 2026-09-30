package com.hotel.api;

import com.hotel.domain.ReservationStatus;
import com.hotel.service.HotelOperationService;
import com.hotel.service.report.ReportExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportExportController {

    private final ReportExportService reportExportService;

    private final HotelOperationService hotelOperationService;

    public ReportExportController(ReportExportService reportExportService,
                                  HotelOperationService hotelOperationService) {
        this.reportExportService = reportExportService;
        this.hotelOperationService = hotelOperationService;
    }

    /**
     * 1. 숙박자(In-House) 리스트 CSV 다운로드
     * startDate~endDate 중 하룻밤이라도 묵는 사람. endDate를 생략하면 startDate 하루만 조회한다.
     * targetDate는 단일 날짜를 넘기던 기존 호출을 위한 별칭이다.
     */
    @GetMapping("/in-house/csv")
    public ResponseEntity<byte[]> downloadInHouseGuestsCsv(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String targetDate) {
        LocalDate from = parseDateOrDefault(startDate != null && !startDate.isBlank() ? startDate : targetDate);
        LocalDate to = (endDate != null && !endDate.isBlank()) ? LocalDate.parse(endDate.trim()) : from;

        String csvString = reportExportService.exportInHouseGuestListToCsv(from, to);
        byte[] csvBytes = withBom(csvString);

        String fileName = URLEncoder.encode("숙박자리스트_" + periodLabel(from, to) + ".csv", StandardCharsets.UTF_8).replace("+", "%20");
        return createCsvResponse(csvBytes, fileName);
    }

    /**
     * 2. 예약자(Bookings) 리스트 CSV 다운로드
     * 체크인 일자가 startDate~endDate에 드는 예약의 전체 정보. endDate를 생략하면 startDate 하루만 조회한다.
     */
    @GetMapping("/reservations/csv")
    public ResponseEntity<byte[]> downloadReservationsCsv(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        LocalDate from = parseDateOrDefault(startDate);
        LocalDate to = (endDate != null && !endDate.isBlank()) ? LocalDate.parse(endDate.trim()) : from;

        ReservationStatus resStatus = (status != null && !status.isBlank())
                ? ReservationStatus.valueOf(status.trim().toUpperCase()) : null;

        String csvString = reportExportService.exportReservationsToCsv(from, to, resStatus, canSeeStaffMemo(authentication));
        byte[] csvBytes = withBom(csvString);

        String fileName = URLEncoder.encode("예약자리스트_" + periodLabel(from, to) + ".csv", StandardCharsets.UTF_8).replace("+", "%20");
        return createCsvResponse(csvBytes, fileName);
    }

    /**
     * 배정 점수 내역 CSV 다운로드 (관리자 전용).
     * 배정 규칙의 계산 내역이 드러나므로 일반 직원에게는 노출하지 않는다.
     */
    @GetMapping("/assignment-scores/csv")
    public ResponseEntity<byte[]> downloadAssignmentScoresCsv(@RequestParam String checkInDate) {
        LocalDate date = LocalDate.parse(checkInDate.trim());

        String csvString = reportExportService.exportAssignmentScoresToCsv(date);
        byte[] csvBytes = withBom(csvString);

        String fileName = URLEncoder.encode("배정점수내역_" + date + ".csv", StandardCharsets.UTF_8).replace("+", "%20");
        return createCsvResponse(csvBytes, fileName);
    }

    /**
     * 3. 태그 & 요청사항 리스트 CSV 다운로드 (배정객실보유태그 포함)
     */
    @GetMapping("/special-requests/csv")
    public ResponseEntity<byte[]> downloadSpecialRequestsCsv(
            @RequestParam(required = false) String targetDate) {
        LocalDate date = (targetDate != null && !targetDate.isBlank())
                ? LocalDate.parse(targetDate) : hotelOperationService.getCurrentBusinessDate();

        String csvString = reportExportService.exportSpecialRequestSummaryToCsv(date);
        byte[] csvBytes = withBom(csvString);

        String fileName = URLEncoder.encode("태그_요청사항리스트_" + date + ".csv", StandardCharsets.UTF_8).replace("+", "%20");
        return createCsvResponse(csvBytes, fileName);
    }

    /**
     * 4. 룸 태그 인디케이터 (1): 191실 전수 객실별 보유 태그 CSV
     */
    @GetMapping("/room-tags/csv")
    public ResponseEntity<byte[]> downloadRoomTagsCsv() {
        String csvString = reportExportService.exportRoomTagsToCsv();
        byte[] csvBytes = withBom(csvString);

        String fileName = URLEncoder.encode("191실_객실별_보유태그인벤토리.csv", StandardCharsets.UTF_8).replace("+", "%20");
        return createCsvResponse(csvBytes, fileName);
    }

    /**
     * 5. 룸 태그 인디케이터 (2): 태그 기준 객실 매핑 매트릭스 CSV
     */
    @GetMapping("/tag-matrix/csv")
    public ResponseEntity<byte[]> downloadTagMatrixCsv() {
        String csvString = reportExportService.exportTagToRoomsMatrixToCsv();
        byte[] csvBytes = withBom(csvString);

        String fileName = URLEncoder.encode("태그별_보유객실매핑_매트릭스.csv", StandardCharsets.UTF_8).replace("+", "%20");
        return createCsvResponse(csvBytes, fileName);
    }

    private LocalDate parseDateOrDefault(String value) {
        return (value != null && !value.isBlank())
                ? LocalDate.parse(value.trim()) : hotelOperationService.getCurrentBusinessDate();
    }

    private static String periodLabel(LocalDate from, LocalDate to) {
        return from.equals(to) ? from.toString() : from + "_" + to;
    }

    // 내부 인계 메모는 아르바이트(PART_TIME)에게는 내려주지 않는다.
    private static boolean canSeeStaffMemo(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));
    }

    private byte[] withBom(String content) {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] textBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + textBytes.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(textBytes, 0, result, bom.length, textBytes.length);
        return result;
    }

    private ResponseEntity<byte[]> createCsvResponse(byte[] data, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(data);
    }
}