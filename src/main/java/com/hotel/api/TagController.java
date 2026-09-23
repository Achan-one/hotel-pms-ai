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

    private String normalizeRoomNumber(String input) {
        if (input == null || input.isBlank()) return "";
        String trimmed = input.trim();
        if (trimmed.length() == 3 && Character.isDigit(trimmed.charAt(0))) {
            return "0" + trimmed;
        }
        return trimmed;
    }
}