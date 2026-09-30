package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.domain.Reservation;
import com.hotel.repository.TagRepository;
import com.hotel.service.FloorStatusService;
import com.hotel.service.HotelOperationService;
import com.hotel.service.ReservationService;
import com.hotel.service.dto.FloorMapResponseDto;
import com.hotel.service.dto.ReservationSearchCondition;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomIndicatorController {

    private final FloorStatusService floorStatusService;
    private final ReservationService reservationService;
    private final HotelOperationService hotelOperationService;
    private final TagRepository tagRepository;

    public RoomIndicatorController(FloorStatusService floorStatusService,
                                   ReservationService reservationService,
                                   HotelOperationService hotelOperationService,
                                   TagRepository tagRepository) {
        this.floorStatusService = floorStatusService;
        this.reservationService = reservationService;
        this.hotelOperationService = hotelOperationService;
        this.tagRepository = tagRepository;
    }

    public record TagCatalogItem(String code, String name, String description, String category, String strictness) {}

    /**
     * 룸 매트릭스에 나오는 태그 코드를 사람이 읽는 이름으로 바꾸기 위한 목록.
     * 태그 관리 API(/api/admin/tags)는 정직원 이상만 쓸 수 있어서 아르바이트도 볼 수 있게 따로 둔다.
     */
    @GetMapping("/tag-catalog")
    public ResponseEntity<ApiResponse<List<TagCatalogItem>>> getTagCatalog() {
        List<TagCatalogItem> items = tagRepository.findAll().stream()
                .map(t -> new TagCatalogItem(t.code(), t.name(), t.description(), t.category().name(), t.strictness().name()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    /**
     * 층별 룸 인디케이터 / 룸 랙 매트릭스 조회
     * GET /api/rooms/indicator?targetDate=2026-09-20
     */
    @GetMapping("/indicator")
    public ResponseEntity<ApiResponse<FloorMapResponseDto>> getRoomIndicator(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        LocalDate date = (targetDate != null) ? targetDate : hotelOperationService.getCurrentBusinessDate();
        // 해당 일자에 머무는 예약만 가져온다. 전체 예약을 읽어 오지 않는다.
        List<Reservation> stayingReservations = reservationService.searchReservations(
                ReservationSearchCondition.byStayingDate(date));

        FloorMapResponseDto matrix = floorStatusService.getFloorMatrix(date, stayingReservations);
        return ResponseEntity.ok(ApiResponse.ok(matrix));
    }
}