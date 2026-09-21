package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoomAssignerQuotaTest {

    private RoomRepository roomRepository;
    private TagQuotaPolicy quotaPolicy;
    private RoomAssigner assigner;

    private final LocalDate today = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        roomRepository = new RoomRepository();
        quotaPolicy = new TagQuotaPolicy();
        assigner = new RoomAssigner(roomRepository, quotaPolicy);
    }

    @Test
    @DisplayName("[태그 쿼터 차단] 킵 수량 이하로 남은 특수 태그 객실은 해당 태그 요청이 없는 일반 예약에 배정되지 않아야 한다")
    void assign_GeneralGuest_BlockedFromQuotaRooms() {
        // Given: 슈페리어 트윈 객실 전체에 대해 특정 2개 객실(0301호, 0401호)에만 특수 태그 "ACCESSIBLE" 부여
        String accessibleTag = "ACCESSIBLE";
        Room r0301 = roomRepository.findByRoomNumber("0301").orElseThrow();
        Room r0401 = roomRepository.findByRoomNumber("0401").orElseThrow();
        r0301.addTag(accessibleTag);
        r0401.addTag(accessibleTag);

        // 호텔 정책: 배리어프리(ACCESSIBLE) 객실은 현장 긴급 인입을 위해 최소 2실 킵 설정
        quotaPolicy.setHoldQuota(accessibleTag, 2);

        // 슈페리어 트윈 공실 중 ACCESSIBLE 태그가 없는 일반 객실을 1건 조회
        Room normalRoom = roomRepository.findAll().stream()
                .filter(r -> r.getRoomType() == RoomType.SUPERIOR_TWIN)
                .filter(r -> !r.hasTag(accessibleTag))
                .findFirst()
                .orElseThrow();

        // When: 태그 요청이 없는 일반 고객(shortStay)이 예약 신청
        Reservation generalGuest = new Reservation(
                "RSV-GEN-01", "일반손님", RoomType.SUPERIOR_TWIN,
                today, 1, null, GuestPreference.empty()
        );

        Optional<Room> assigned = assigner.assign(generalGuest);

        // Then: 배정은 성공하되, 킵 대상인 ACCESSIBLE 태그 객실(0301, 0401)은 피하고 일반 방에 배정되어야 함
        assertTrue(assigned.isPresent());
        assertFalse(assigned.get().hasTag(accessibleTag),
                "일반 예약 고객에게 킵 설정된 ACCESSIBLE 객실이 배정되어서는 안 됩니다.");
    }

    @Test
    @DisplayName("[태그 희망자 허용] 킵 수량 임계치에 도달했더라도 해당 태그를 명시적으로 선호한 고객에게는 정상 배정되어야 한다")
    void assign_PreferredTagGuest_AllocatedFromQuotaRooms() {
        // Given: 슈페리어 트윈 객실 중 0301호에 "VIEW_TOKYO_TOWER" 태그 부여 및 1실 킵 설정
        String towerTag = "VIEW_TOKYO_TOWER";
        Room r0301 = roomRepository.findByRoomNumber("0301").orElseThrow();
        r0301.addTag(towerTag);

        quotaPolicy.setHoldQuota(towerTag, 1);

        // 도쿄타워 전망 태그 스위치가 켜진 예약 생성
        TagPreference pref = new TagPreference(Set.of(towerTag), Set.of());
        Reservation towerLover = new Reservation(
                "RSV-TOWER-01", "타워희망손님", RoomType.SUPERIOR_TWIN,
                today, 2, "도쿄타워 전망 부탁합니다", GuestPreference.empty()
        ).withTagPreference(pref);

        // When: 배정 실행
        Optional<Room> assigned = assigner.assign(towerLover);

        // Then: 킵 수량(1실)에 걸려 있어도 태그를 희망했으므로 타워뷰 객실(0301)에 정상 배정되어야 함
        assertTrue(assigned.isPresent());
        assertEquals("0301", assigned.get().getRoomNumber());
        assertTrue(assigned.get().hasTag(towerTag));
    }

    @Test
    @DisplayName("[인벤토리 소진 시 킵 객실 보호] 일반 객실이 전부 차고 킵 객실만 남았을 때 일반 예약은 만실(empty) 격리되어야 한다")
    void assign_WhenOnlyQuotaRoomsLeft_GeneralGuestRejected() {
        String towerTag = "VIEW_TOKYO_TOWER";

        // 슈페리어 트윈 객실 중 0301호만 남겨두고 나머지는 모두 사전 점유 처리
        StayPeriod stay = new StayPeriod(today, 1);
        List<Room> twinRooms = roomRepository.findAll().stream()
                .filter(r -> r.getRoomType() == RoomType.SUPERIOR_TWIN)
                .toList();

        for (Room r : twinRooms) {
            if (!r.getRoomNumber().equals("0301")) {
                r.bookPeriod(stay);
            }
        }

        // 유일하게 남은 0301호에 태그 부여 및 1실 킵 정책 설정
        Room lastRoom = roomRepository.findByRoomNumber("0301").orElseThrow();
        lastRoom.addTag(towerTag);
        quotaPolicy.setHoldQuota(towerTag, 1);

        // When: 태그 요청이 없는 일반 고객 배정 시도
        Reservation generalGuest = new Reservation(
                "RSV-GEN-FAIL", "일반손님", RoomType.SUPERIOR_TWIN,
                today, 1, null, GuestPreference.empty()
        );

        Optional<Room> assignedGeneral = assigner.assign(generalGuest);

        // Then: 0301호가 비어있음에도 킵 수량 방어로 인해 일반 손님에게는 공실이 없는 것으로 격리(empty)
        assertTrue(assignedGeneral.isEmpty(),
                "일반 고객은 킵 수량 방어에 의해 빈 방을 가로채지 못하고 배정 실패(만실)되어야 합니다.");

        // And When: 같은 날 동일 룸타입으로 타워뷰를 희망하는 손님 인입
        TagPreference pref = new TagPreference(Set.of(towerTag), Set.of());
        Reservation vipGuest = new Reservation(
                "RSV-VIP-PASS", "VIP손님", RoomType.SUPERIOR_TWIN,
                today, 1, "타워 뷰 원함", GuestPreference.empty()
        ).withTagPreference(pref);

        Optional<Room> assignedVip = assigner.assign(vipGuest);

        // Then: 킵해두었던 0301호가 VIP 손님에게 정상 배정 확정
        assertTrue(assignedVip.isPresent());
        assertEquals("0301", assignedVip.get().getRoomNumber());
    }
}