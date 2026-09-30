package com.hotel.api;

import com.hotel.api.dto.ApiResponse;
import com.hotel.domain.Room;
import com.hotel.domain.RoomTag;
import com.hotel.domain.TagStrictness;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.service.AdminTagService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/tags")
public class TagController {

    private final AdminTagService adminTagService;
    private final TagRepository tagRepository;
    private final RoomRepository roomRepository;

    public TagController(AdminTagService adminTagService,
                         TagRepository tagRepository,
                         RoomRepository roomRepository) {
        this.adminTagService = Objects.requireNonNull(adminTagService);
        this.tagRepository = Objects.requireNonNull(tagRepository);
        this.roomRepository = Objects.requireNonNull(roomRepository);
    }

    public record TagRegisterRequest(
            @NotBlank(message = "태그 코드는 필수입니다.") String code,
            @NotBlank(message = "태그 표시 이름은 필수입니다.") String name,
            String description,
            RoomTag.TagCategory category,
            TagStrictness strictness,
            int defaultWeight,
            List<String> targetRoomNumbers
    ) {}

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomTag>>> getAllTags() {
        return ResponseEntity.ok(ApiResponse.ok(tagRepository.findAll()));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STAFF')")
    public ResponseEntity<ApiResponse<Void>> registerCustomTag(@RequestBody TagRegisterRequest request) {
        boolean isAdmin = true;

        RoomTag newTag = new RoomTag(
                request.code().trim().toUpperCase(),
                request.name().trim(),
                request.description(),
                request.category() != null ? request.category() : RoomTag.TagCategory.ETC,
                request.strictness() != null ? request.strictness() : TagStrictness.SOFT,
                request.defaultWeight() > 0 ? request.defaultWeight() : 25,
                false
        );

        adminTagService.registerTag(isAdmin, newTag);

        if (request.targetRoomNumbers() != null && !request.targetRoomNumbers().isEmpty()) {
            for (String roomNo : request.targetRoomNumbers()) {
                String normalized = normalizeRoomNumber(roomNo);
                roomRepository.findByRoomNumber(normalized).ifPresent(room -> room.addTag(newTag.code()));
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(
                String.format("[%s] 커스텀 태그가 등록되었으며 AI 사전에 즉시 반영되었습니다.", newTag.name()),
                null
        ));
    }

    /**
     * 특정 태그가 부여된 191실 객실 번호 목록 조회
     * GET /api/admin/tags/{tagCode}/rooms
     */
    @GetMapping("/{tagCode}/rooms")
    public ResponseEntity<ApiResponse<List<String>>> getRoomsByTag(@PathVariable("tagCode") String tagCode) {
        String decodedCode;
        try {
            decodedCode = java.net.URLDecoder.decode(tagCode, java.nio.charset.StandardCharsets.UTF_8).trim().toUpperCase();
        } catch (Exception e) {
            decodedCode = tagCode.trim().toUpperCase();
        }

        final String targetCode = decodedCode;
        List<String> matchedRoomNumbers = roomRepository.findAll().stream()
                .filter(room -> room.hasTag(targetCode))
                .map(Room::getRoomNumber)
                .sorted()
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(matchedRoomNumbers));
    }

    @DeleteMapping("/{tagCode}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable("tagCode") String tagCode) {
        // URL 인코딩된 문자열(한글 등) 디코딩 및 공백/대문자 정규화
        String decodedCode;
        try {
            decodedCode = java.net.URLDecoder.decode(tagCode, java.nio.charset.StandardCharsets.UTF_8).trim().toUpperCase();
        } catch (Exception e) {
            decodedCode = tagCode.trim().toUpperCase();
        }

        final String targetCode = decodedCode;
        RoomTag target = tagRepository.findByCode(targetCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 태그입니다: " + targetCode));

        if (target.isSystemDefault()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(
                    String.format("[%s] 태그는 호텔 건축 도면 기반의 필수 시스템 태그이므로 삭제할 수 없습니다.", target.name())
            ));
        }

        tagRepository.deleteByCode(targetCode);

        for (Room room : roomRepository.findAll()) {
            room.removeTag(targetCode);
        }

        return ResponseEntity.ok(ApiResponse.ok(
                String.format("[%s] 태그가 삭제되었습니다.", target.name()),
                null
        ));
    }
    public record TagRoomMappingUpdateRequest(
            List<String> targetRoomNumbers
    ) {}

    /**
     * 특정 태그(기본/커스텀 무관)의 191실 매핑 전체 갱신
     * PUT /api/admin/tags/{tagCode}/rooms
     */
    @PutMapping("/{tagCode}/rooms")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STAFF')")
    public ResponseEntity<ApiResponse<Void>> updateRoomsForTag(
            @PathVariable("tagCode") String tagCode,
            @RequestBody TagRoomMappingUpdateRequest request) {

        String decodedCode;
        try {
            decodedCode = java.net.URLDecoder.decode(tagCode, java.nio.charset.StandardCharsets.UTF_8).trim().toUpperCase();
        } catch (Exception e) {
            decodedCode = tagCode.trim().toUpperCase();
        }

        final String targetCode = decodedCode;
        RoomTag targetTag = tagRepository.findByCode(targetCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 태그입니다: " + targetCode));

        Set<String> newRoomNumbers = (request.targetRoomNumbers() != null)
                ? request.targetRoomNumbers().stream().map(this::normalizeRoomNumber).collect(java.util.stream.Collectors.toSet())
                : Set.of();

        // 191개 전체 객실을 순회하며 해당 태그 반영 상태를 일괄 동기화
        for (Room room : roomRepository.findAll()) {
            boolean shouldHaveTag = newRoomNumbers.contains(room.getRoomNumber());
            boolean currentlyHasTag = room.hasTag(targetCode);

            if (shouldHaveTag && !currentlyHasTag) {
                room.addTag(targetCode);
                roomRepository.save(room);
            } else if (!shouldHaveTag && currentlyHasTag) {
                room.removeTag(targetCode);
                roomRepository.save(room);
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(
                String.format("[%s] 태그의 객실 매핑(총 %d실)이 성공적으로 반영되었습니다.", targetTag.name(), newRoomNumbers.size()),
                null
        ));
    }

    public record TagFullUpdateRequest(
            String name,
            String description,
            RoomTag.TagCategory category,
            TagStrictness strictness,
            Integer defaultWeight,
            List<String> targetRoomNumbers
    ) {}

    /**
     * 태그 속성 및 191실 매핑 일괄 편집 (통합 수정 API)
     * PUT /api/admin/tags/{tagCode}
     */
    @PutMapping("/{tagCode}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STAFF')")
    public ResponseEntity<ApiResponse<Void>> updateTagFull(
            @PathVariable("tagCode") String tagCode,
            @RequestBody TagFullUpdateRequest request) {

        String decodedCode;
        try {
            decodedCode = java.net.URLDecoder.decode(tagCode, java.nio.charset.StandardCharsets.UTF_8).trim().toUpperCase();
        } catch (Exception e) {
            decodedCode = tagCode.trim().toUpperCase();
        }

        final String targetCode = decodedCode;
        RoomTag existingTag = tagRepository.findByCode(targetCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 태그입니다: " + targetCode));

        // 1. 커스텀 태그인 경우에만 메타데이터(이름, 설명, 점수 등) 갱신 허용
        if (!existingTag.isSystemDefault()) {
            String newName = (request.name() != null && !request.name().isBlank()) ? request.name().trim() : existingTag.name();
            String newDesc = (request.description() != null && !request.description().isBlank()) ? request.description().trim() : existingTag.description();
            RoomTag.TagCategory newCategory = (request.category() != null) ? request.category() : existingTag.category();
            TagStrictness newStrictness = (request.strictness() != null) ? request.strictness() : existingTag.strictness();
            int newWeight = (request.defaultWeight() != null && request.defaultWeight() > 0) ? request.defaultWeight() : existingTag.defaultWeight();

            RoomTag updatedTag = new RoomTag(
                    targetCode,
                    newName,
                    newDesc,
                    newCategory,
                    newStrictness,
                    newWeight,
                    false
            );
            tagRepository.save(updatedTag);
        }

        // 2. 191실 객실 매핑 반영 (기본 태그, 커스텀 태그 공통 지원)
        Set<String> newRoomNumbers = (request.targetRoomNumbers() != null)
                ? request.targetRoomNumbers().stream().map(this::normalizeRoomNumber).collect(java.util.stream.Collectors.toSet())
                : Set.of();

        for (Room room : roomRepository.findAll()) {
            boolean shouldHaveTag = newRoomNumbers.contains(room.getRoomNumber());
            boolean currentlyHasTag = room.hasTag(targetCode);

            if (shouldHaveTag && !currentlyHasTag) {
                room.addTag(targetCode);
                roomRepository.save(room);
            } else if (!shouldHaveTag && currentlyHasTag) {
                room.removeTag(targetCode);
                roomRepository.save(room);
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(
                String.format("[%s] 태그 정보 및 객실 배치(총 %d실)가 성공적으로 저장되었습니다.", existingTag.name(), newRoomNumbers.size()),
                null
        ));
    }

    private String normalizeRoomNumber(String input) {
        if (input == null || input.isBlank()) return "";
        String trimmed = input.trim();
        if (trimmed.length() == 3 && Character.isDigit(trimmed.charAt(0))) {
            return "0" + trimmed;
        }
        return trimmed;
    }
}