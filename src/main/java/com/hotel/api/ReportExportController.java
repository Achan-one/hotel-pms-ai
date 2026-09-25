package com.hotel.api;

import com.hotel.domain.ReservationStatus;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.service.report.ReportExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportExportController {

    private final ReportExportService reportExportService;

    public ReportExportController(ReportExportService reportExportService) {
        this.reportExportService = reportExportService;
    }

    /**
     * 1. 숙박자(In-House) 리스트 CSV 다운로드
     */
    @GetMapping("/in-house/csv")
    public ResponseEntity<byte[]> downloadInHouseGuestsCsv(
            @RequestParam(required = false) String targetDate) {
        LocalDate date = (targetDate != null && !targetDate.isBlank())
                ? LocalDate.parse(targetDate) : LocalDate.now();

        if (date.isAfter(LocalDate.now())) {
            date = LocalDate.now();
        }

        String csvString = reportExportService.exportInHouseGuestListToCsv(date);
        byte[] csvBytes = withBom(csvString);

        String fileName = URLEncoder.encode("숙박자리스트_" + date + ".csv", StandardCharsets.UTF_8).replace("+", "%20");
        return createCsvResponse(csvBytes, fileName);
    }

    /**
     * 2. 예약자(Bookings) 리스트 CSV 다운로드
     */
    @GetMapping("/reservations/csv")
    public ResponseEntity<byte[]> downloadReservationsCsv(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String status) {
        LocalDate start = (startDate != null && !startDate.isBlank())
                ? LocalDate.parse(startDate) : LocalDate.now();

        ReservationStatus resStatus = (status != null && !status.isBlank())
                ? ReservationStatus.valueOf(status.toUpperCase()) : null;

        ReservationSearchCondition condition = new ReservationSearchCondition(
                null, null, start, null, null, null, resStatus, null, null,null
        );

        String csvString = reportExportService.exportReservationsToCsv(condition);
        byte[] csvBytes = withBom(csvString);

        String fileName = URLEncoder.encode("예약자리스트_" + start + ".csv", StandardCharsets.UTF_8).replace("+", "%20");
        return createCsvResponse(csvBytes, fileName);
    }

    /**
     * 3. 태그 & 요청사항 리스트 CSV 다운로드 (배정객실보유태그 포함)
     */
    @GetMapping("/special-requests/csv")
    public ResponseEntity<byte[]> downloadSpecialRequestsCsv(
            @RequestParam(required = false) String targetDate) {
        LocalDate date = (targetDate != null && !targetDate.isBlank())
                ? LocalDate.parse(targetDate) : LocalDate.now();

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