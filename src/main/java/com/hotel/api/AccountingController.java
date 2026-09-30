package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.domain.BookingChannelInfo;
import com.hotel.entity.CityLedgerRecordEntity;
import com.hotel.entity.FolioChargeCodeEntity;
import com.hotel.repository.CityLedgerRepository;
import com.hotel.repository.jpa.SpringDataFolioChargeCodeRepository;
import com.hotel.service.DuplicateResourceException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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

    public record ChargeCodeRequest(
            @NotBlank(message = "계정과목 코드는 필수입니다.")
            @Size(max = 30, message = "계정과목 코드는 30자 이하여야 합니다.")
            String code,

            @NotBlank(message = "계정과목 이름은 필수입니다.")
            @Size(max = 50, message = "계정과목 이름은 50자 이하여야 합니다.")
            String name,

            @PositiveOrZero(message = "기본 금액은 0 이상이어야 합니다.")
            long defaultAmount
    ) {}

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
    public ResponseEntity<ApiResponse<Void>> registerChargeCode(@Valid @RequestBody ChargeCodeRequest request) {
        String code = request.code().trim().toUpperCase();
        if (chargeCodeRepo.existsById(code)) {
            throw new DuplicateResourceException("이미 존재하는 계정과목 코드입니다: " + code);
        }

        chargeCodeRepo.save(new FolioChargeCodeEntity(code, request.name().trim(), request.defaultAmount(), false));
        return ResponseEntity.ok(ApiResponse.ok("신규 계정과목이 등록되었습니다.", null));
    }

    /**
     * 3. 관리자 계정과목 삭제
     * DELETE /api/accounting/charge-codes/{code}
     */
    @DeleteMapping("/charge-codes/{code}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteChargeCode(@PathVariable String code) {
        FolioChargeCodeEntity target = chargeCodeRepo.findById(code.trim().toUpperCase())
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 계정과목입니다: " + code));
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