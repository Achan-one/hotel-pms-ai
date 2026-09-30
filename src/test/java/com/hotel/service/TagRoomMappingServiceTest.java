package com.hotel.service;

import com.hotel.domain.RoomTag;
import com.hotel.domain.TagStrictness;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class TagRoomMappingServiceTest {

    @Autowired
    private TagRoomMappingService service;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private TagRepository tagRepository;

    private RoomTag customTag(String code) {
        return new RoomTag(code, "테스트 태그", "설명", RoomTag.TagCategory.ETC, TagStrictness.SOFT, 25, false);
    }

    @Test
    @DisplayName("[태그 등록] 대상 객실에 태그가 실제로 저장되어야 한다")
    void registerPersistsRoomMapping() {
        service.registerCustomTag(true, customTag("SEA_VIEW"), List.of("0501", "0502"));

        assertTrue(tagRepository.findByCode("SEA_VIEW").isPresent());
        assertTrue(roomRepository.findByRoomNumber("0501").orElseThrow().hasTag("SEA_VIEW"));
        assertTrue(roomRepository.findByRoomNumber("0502").orElseThrow().hasTag("SEA_VIEW"));
        assertFalse(roomRepository.findByRoomNumber("0503").orElseThrow().hasTag("SEA_VIEW"));
    }

    @Test
    @DisplayName("[태그 등록] 이미 있는 코드(시스템 태그 포함)로는 덮어쓸 수 없다")
    void registerExistingCodeIsRejected() {
        assertThrows(DuplicateResourceException.class,
                () -> service.registerCustomTag(true, customTag("HIGH_FLOOR"), List.of()));

        assertTrue(tagRepository.findByCode("HIGH_FLOOR").orElseThrow().isSystemDefault());
    }

    @Test
    @DisplayName("[태그 등록] 도면에 없는 객실 번호가 있으면 태그도 만들지 않는다")
    void registerWithUnknownRoomIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerCustomTag(true, customTag("GHOST"), List.of("0501", "9999")));

        assertTrue(tagRepository.findByCode("GHOST").isEmpty());
    }

    @Test
    @DisplayName("[태그 등록] 관리자가 아니면 서비스 계층에서도 거부하고 아무것도 저장하지 않는다")
    void registerByNonAdminIsRejected() {
        assertThrows(SecurityException.class,
                () -> service.registerCustomTag(false, customTag("NOT_ALLOWED"), List.of("0501")));

        assertTrue(tagRepository.findByCode("NOT_ALLOWED").isEmpty());
        assertFalse(roomRepository.findByRoomNumber("0501").orElseThrow().hasTag("NOT_ALLOWED"));
    }

    @Test
    @DisplayName("[태그 삭제] 삭제하면 모든 객실에서 매핑도 사라지고, 시스템 태그는 삭제할 수 없다")
    void deleteRemovesMappingsAndProtectsSystemTags() {
        service.registerCustomTag(true, customTag("TEMP_TAG"), List.of("0501", "0502"));

        service.deleteTag("TEMP_TAG");

        assertTrue(tagRepository.findByCode("TEMP_TAG").isEmpty());
        assertTrue(roomRepository.findAll().stream().noneMatch(r -> r.hasTag("TEMP_TAG")));
        assertThrows(IllegalArgumentException.class, () -> service.deleteTag("HIGH_FLOOR"));
    }

    @Test
    @DisplayName("[태그 매핑] 목록에서 빠진 객실은 태그가 제거되고 새 객실은 추가되어야 한다")
    void replaceMappingAddsAndRemoves() {
        service.registerCustomTag(true, customTag("MOVE_TAG"), List.of("0501", "0502"));

        service.replaceRoomMapping("MOVE_TAG", List.of("0502", "0503"));

        assertFalse(roomRepository.findByRoomNumber("0501").orElseThrow().hasTag("MOVE_TAG"));
        assertTrue(roomRepository.findByRoomNumber("0502").orElseThrow().hasTag("MOVE_TAG"));
        assertTrue(roomRepository.findByRoomNumber("0503").orElseThrow().hasTag("MOVE_TAG"));
        assertEquals(2, roomRepository.findAll().stream().filter(r -> r.hasTag("MOVE_TAG")).count());
    }
}
