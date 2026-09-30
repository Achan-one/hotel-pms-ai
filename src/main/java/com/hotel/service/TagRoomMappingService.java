package com.hotel.service;

import com.hotel.domain.Room;
import com.hotel.domain.RoomTag;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 태그 정의와 객실-태그 매핑을 한 트랜잭션으로 바꾸는 서비스.
 * 객실 단위 태그 추가/삭제만 쓰므로 동시에 진행 중인 배정이나 체크인이 저장한 상태와 스케줄을 덮어쓰지 않는다.
 */
@Service
@Transactional
public class TagRoomMappingService {

    private final AdminTagService adminTagService;
    private final TagRepository tagRepository;
    private final RoomRepository roomRepository;

    public TagRoomMappingService(AdminTagService adminTagService,
                                 TagRepository tagRepository,
                                 RoomRepository roomRepository) {
        this.adminTagService = Objects.requireNonNull(adminTagService);
        this.tagRepository = Objects.requireNonNull(tagRepository);
        this.roomRepository = Objects.requireNonNull(roomRepository);
    }

    public void registerCustomTag(RoomTag tag, Collection<String> roomNumbers) {
        if (tagRepository.findByCode(tag.code()).isPresent()) {
            throw new DuplicateResourceException("이미 존재하는 태그 코드입니다: " + tag.code());
        }
        Set<String> targets = requireExistingRooms(roomNumbers);

        adminTagService.registerTag(true, tag);
        targets.forEach(roomNumber -> roomRepository.addTag(roomNumber, tag.code()));
    }

    public RoomTag deleteTag(String tagCode) {
        RoomTag target = requireTag(tagCode);
        if (target.isSystemDefault()) {
            throw new IllegalArgumentException(String.format(
                    "[%s] 태그는 호텔 건축 도면 기반의 필수 시스템 태그이므로 삭제할 수 없습니다.", target.name()));
        }
        tagRepository.deleteByCode(target.code());
        roomRepository.removeTagFromAll(target.code());
        return target;
    }

    public RoomTag replaceRoomMapping(String tagCode, Collection<String> roomNumbers) {
        RoomTag tag = requireTag(tagCode);
        applyMapping(tag.code(), requireExistingRooms(roomNumbers));
        return tag;
    }

    /**
     * 커스텀 태그는 속성도 함께 바꾸고, 시스템 태그는 객실 매핑만 바꾼다.
     */
    public RoomTag updateTag(String tagCode, RoomTag updatedCustomTag, Collection<String> roomNumbers) {
        RoomTag existing = requireTag(tagCode);
        Set<String> targets = requireExistingRooms(roomNumbers);

        if (!existing.isSystemDefault() && updatedCustomTag != null) {
            tagRepository.save(updatedCustomTag);
        }
        applyMapping(existing.code(), targets);
        return existing;
    }

    private void applyMapping(String tagCode, Set<String> desiredRooms) {
        Set<String> currentHolders = roomRepository.findAll().stream()
                .filter(room -> room.hasTag(tagCode))
                .map(Room::getRoomNumber)
                .collect(Collectors.toSet());

        for (String roomNumber : desiredRooms) {
            if (!currentHolders.contains(roomNumber)) {
                roomRepository.addTag(roomNumber, tagCode);
            }
        }
        for (String roomNumber : currentHolders) {
            if (!desiredRooms.contains(roomNumber)) {
                roomRepository.removeTag(roomNumber, tagCode);
            }
        }
    }

    private RoomTag requireTag(String tagCode) {
        return tagRepository.findByCode(tagCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 태그입니다: " + tagCode));
    }

    private Set<String> requireExistingRooms(Collection<String> roomNumbers) {
        if (roomNumbers == null) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();
        for (String raw : roomNumbers) {
            String roomNumber = normalizeRoomNumber(raw);
            if (roomNumber.isEmpty() || roomRepository.findByRoomNumber(roomNumber).isEmpty()) {
                missing.add(raw);
            } else {
                normalized.add(roomNumber);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("도면에 없는 객실 번호가 있습니다: " + missing);
        }
        return normalized;
    }

    private static String normalizeRoomNumber(String input) {
        if (input == null || input.isBlank()) return "";
        String trimmed = input.trim();
        if (trimmed.length() == 3 && Character.isDigit(trimmed.charAt(0))) {
            return "0" + trimmed;
        }
        return trimmed;
    }
}
