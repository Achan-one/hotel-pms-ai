package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.domain.Room;
import com.hotel.domain.RoomTag;
import com.hotel.domain.TagStrictness;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.service.TagRoomMappingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin/tags")
public class TagController {

    private static final int DEFAULT_WEIGHT = 25;

    private final TagRoomMappingService tagRoomMappingService;
    private final TagRepository tagRepository;
    private final RoomRepository roomRepository;

    public TagController(TagRoomMappingService tagRoomMappingService,
                         TagRepository tagRepository,
                         RoomRepository roomRepository) {
        this.tagRoomMappingService = Objects.requireNonNull(tagRoomMappingService);
        this.tagRepository = Objects.requireNonNull(tagRepository);
        this.roomRepository = Objects.requireNonNull(roomRepository);
    }

    public record TagRegisterRequest(
            @NotBlank(message = "태그 코드는 필수입니다.")
            @Size(max = 50, message = "태그 코드는 50자 이하여야 합니다.")
            String code,

            @NotBlank(message = "태그 표시 이름은 필수입니다.")
            @Size(max = 100, message = "태그 이름은 100자 이하여야 합니다.")
            String name,

            @Size(max = 500, message = "설명은 500자 이하여야 합니다.")
            String description,

            RoomTag.TagCategory category,
            TagStrictness strictness,

            @PositiveOrZero(message = "기본 점수는 0 이상이어야 합니다.")
            int defaultWeight,

            List<String> targetRoomNumbers
    ) {}

    public record TagRoomMappingUpdateRequest(
            List<String> targetRoomNumbers
    ) {}

    public record TagFullUpdateRequest(
            @Size(max = 100, message = "태그 이름은 100자 이하여야 합니다.")
            String name,

            @Size(max = 500, message = "설명은 500자 이하여야 합니다.")
            String description,

            RoomTag.TagCategory category,
            TagStrictness strictness,

            @PositiveOrZero(message = "기본 점수는 0 이상이어야 합니다.")
            Integer defaultWeight,

            List<String> targetRoomNumbers
    ) {}

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomTag>>> getAllTags() {
        return ResponseEntity.ok(ApiResponse.ok(tagRepository.findAll()));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STAFF')")
    public ResponseEntity<ApiResponse<Void>> registerCustomTag(@Valid @RequestBody TagRegisterRequest request) {
        RoomTag newTag = new RoomTag(
                normalizeCode(request.code()),
                request.name().trim(),
                request.description(),
                request.category() != null ? request.category() : RoomTag.TagCategory.ETC,
                request.strictness() != null ? request.strictness() : TagStrictness.SOFT,
                request.defaultWeight() > 0 ? request.defaultWeight() : DEFAULT_WEIGHT,
                false
        );

        tagRoomMappingService.registerCustomTag(newTag, request.targetRoomNumbers());

        return ResponseEntity.ok(ApiResponse.ok(
                String.format("[%s] 커스텀 태그가 등록되었으며 AI 사전에 즉시 반영되었습니다.", newTag.name()),
                null
        ));
    }

    /**
     * 특정 태그가 부여된 객실 번호 목록 조회
     * GET /api/admin/tags/{tagCode}/rooms
     */
    @GetMapping("/{tagCode}/rooms")
    public ResponseEntity<ApiResponse<List<String>>> getRoomsByTag(@PathVariable("tagCode") String tagCode) {
        String targetCode = normalizeCode(tagCode);
        List<String> matchedRoomNumbers = roomRepository.findAll().stream()
                .filter(room -> room.hasTag(targetCode))
                .map(Room::getRoomNumber)
                .sorted()
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(matchedRoomNumbers));
    }

    @DeleteMapping("/{tagCode}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STAFF')")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable("tagCode") String tagCode) {
        RoomTag deleted = tagRoomMappingService.deleteTag(normalizeCode(tagCode));
        return ResponseEntity.ok(ApiResponse.ok(
                String.format("[%s] 태그가 삭제되었습니다.", deleted.name()),
                null
        ));
    }

    /**
     * 특정 태그(기본/커스텀 무관)의 객실 매핑 전체 갱신
     * PUT /api/admin/tags/{tagCode}/rooms
     */
    @PutMapping("/{tagCode}/rooms")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STAFF')")
    public ResponseEntity<ApiResponse<Void>> updateRoomsForTag(
            @PathVariable("tagCode") String tagCode,
            @Valid @RequestBody TagRoomMappingUpdateRequest request) {

        RoomTag tag = tagRoomMappingService.replaceRoomMapping(normalizeCode(tagCode), request.targetRoomNumbers());
        int roomCount = request.targetRoomNumbers() != null ? request.targetRoomNumbers().size() : 0;

        return ResponseEntity.ok(ApiResponse.ok(
                String.format("[%s] 태그의 객실 매핑(총 %d실)이 반영되었습니다.", tag.name(), roomCount),
                null
        ));
    }

    /**
     * 태그 속성 및 객실 매핑 일괄 편집
     * PUT /api/admin/tags/{tagCode}
     */
    @PutMapping("/{tagCode}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STAFF')")
    public ResponseEntity<ApiResponse<Void>> updateTagFull(
            @PathVariable("tagCode") String tagCode,
            @Valid @RequestBody TagFullUpdateRequest request) {

        String targetCode = normalizeCode(tagCode);
        RoomTag existing = tagRepository.findByCode(targetCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 태그입니다: " + targetCode));

        RoomTag updated = null;
        if (!existing.isSystemDefault()) {
            updated = new RoomTag(
                    targetCode,
                    hasText(request.name()) ? request.name().trim() : existing.name(),
                    hasText(request.description()) ? request.description().trim() : existing.description(),
                    request.category() != null ? request.category() : existing.category(),
                    request.strictness() != null ? request.strictness() : existing.strictness(),
                    (request.defaultWeight() != null && request.defaultWeight() > 0) ? request.defaultWeight() : existing.defaultWeight(),
                    false
            );
        }

        tagRoomMappingService.updateTag(targetCode, updated, request.targetRoomNumbers());
        int roomCount = request.targetRoomNumbers() != null ? request.targetRoomNumbers().size() : 0;

        return ResponseEntity.ok(ApiResponse.ok(
                String.format("[%s] 태그 정보 및 객실 배치(총 %d실)가 저장되었습니다.", existing.name(), roomCount),
                null
        ));
    }

    // 스프링이 경로 변수를 이미 디코딩하므로 여기서 다시 디코딩하지 않는다.
    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
