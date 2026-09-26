package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.domain.BookingChannelInfo;
import com.hotel.entity.CityLedgerRecordEntity;
import com.hotel.entity.FolioChargeCodeEntity;
import com.hotel.repository.CityLedgerRepository;
import com.hotel.repository.jpa.SpringDataFolioChargeCodeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounting")
public class AccountingController {

    private final SpringDataFolioChargeCodeRepository chargeCodeRepo;
    private final CityLedgerRepository cityLedgerRepo;

    public AccountingController(SpringDataFolioChargeCodeRepository chargeCodeRepo,
                                CityLedgerRepository cityLedgerRepo) {
        this.chargeCodeRepo = chargeCodeRepo;
        this.cityLedgerRepo = cityLedgerRepo;
    }

    /**
     * 1. 등록된 계정과목 목록 조회 (수납 모달 및 관리자 화면용)
     * GET /api/accounting/charge-codes
     */
    @GetMapping("/charge-codes")
    public ResponseEntity<ApiResponse<List<FolioChargeCodeEntity>>> getChargeCodes() {
        return ResponseEntity.ok(ApiResponse.ok(chargeCodeRepo.findAll()));
    }

    /**
     * 2. 관리자 신규 계정과목 등록
     * POST /api/accounting/charge-codes
     */
    @PostMapping("/charge-codes")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> registerChargeCode(@RequestBody Map<String, Object> body) {
        String code = ((String) body.get("code")).trim().toUpperCase();
        String name = ((String) body.get("name")).trim();
        long defaultAmount = Long.parseLong(body.getOrDefault("defaultAmount", 0).toString());

        chargeCodeRepo.save(new FolioChargeCodeEntity(code, name, defaultAmount, false));
        return ResponseEntity.ok(ApiResponse.ok("신규 계정과목이 등록되었습니다.", null));
    }

    /**
     * 3. 관리자 계정과목 삭제
     * DELETE /api/accounting/charge-codes/{code}
     */
    @DeleteMapping("/charge-codes/{code}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteChargeCode(@PathVariable String code) {
        FolioChargeCodeEntity target = chargeCodeRepo.findById(code.toUpperCase()).orElseThrow();
        if (target.isSystemDefault()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("시스템 기본 계정과목은 삭제할 수 없습니다."));
        }
        chargeCodeRepo.delete(target);
        return ResponseEntity.ok(ApiResponse.ok("계정과목이 삭제되었습니다.", null));
    }

    /**
     * 4. OTA City Ledger 정산 총액 및 명세서 조회
     * GET /api/accounting/city-ledger
     */
    @GetMapping("/city-ledger")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCityLedgerSummary() {
        List<CityLedgerRecordEntity> records = cityLedgerRepo.findAll();

        Map<BookingChannelInfo.ChannelType, Long> channelTotals = records.stream()
                .collect(Collectors.groupingBy(
                        CityLedgerRecordEntity::getChannelType,
                        Collectors.summingLong(CityLedgerRecordEntity::getBilledAmount)
                ));

        long grandTotal = records.stream().mapToLong(CityLedgerRecordEntity::getBilledAmount).sum();

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "records", records,
                "channelTotals", channelTotals,
                "grandTotal", grandTotal
        )));
    }
}