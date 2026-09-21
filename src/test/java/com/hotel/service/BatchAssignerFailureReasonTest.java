package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.service.dto.AssignmentAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BatchAssignerFailureReasonTest {

    private RoomRepository roomRepository;
    private TagRepository tagRepository;
    private QuotaPolicy quotaPolicy;
    private RoomAssigner roomAssigner;
    private BatchAssigner batchAssigner;

    private final LocalDate today = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        roomRepository =new com.hotel.repository.memory.InMemoryRoomRepository();
        tagRepository = new com.hotel.repository.memory.InMemoryTagRepository();
        quotaPolicy = new QuotaPolicy();
        roomAssigner = new RoomAssigner(roomRepository, tagRepository, quotaPolicy);
        batchAssigner = new BatchAssigner(roomAssigner);
    }

    @Test
    @DisplayName("[실패 사유 1:1 마킹] 킵 수량이 1실일 때 최초 1명만 '안전 쿼터 차단'이고 이후 예약은 '전량 매진'이어야 한다")
    void batchAssign_DistinguishFirstQuotaBlockFromSubsequentSoldOut() {
        // Given: 이그제큐티브 더블 총 4실 중 3실을 사전 점유하여 '물리 공실 1실'만 남김
        // QuotaPolicy 상 EXECUTIVE_DOUBLE 킵 수량은 기본 1실
        assertEquals(1, quotaPolicy.getTypeHoldQuota(RoomType.EXECUTIVE_DOUBLE));

        StayPeriod stay = new StayPeriod(today, 1);
        List<Room> execRooms = roomRepository.findAll().stream()
                .filter(r -> r.getRoomType() == RoomType.EXECUTIVE_DOUBLE)
                .toList();
        for (int i = 0; i < 3; i++) {
            execRooms.get(i).bookPeriod(stay);
        }

        // 3명의 이그제큐티브 더블 예약이 인입 (박수 동일)
        Reservation rsv1 = new Reservation("RSV-01", "손님1", RoomType.EXECUTIVE_DOUBLE, today, 1, null, GuestPreference.empty());
        Reservation rsv2 = new Reservation("RSV-02", "손님2", RoomType.EXECUTIVE_DOUBLE, today, 1, null, GuestPreference.empty());
        Reservation rsv3 = new Reservation("RSV-03", "손님3", RoomType.EXECUTIVE_DOUBLE, today, 1, null, GuestPreference.empty());

        // When: 일괄 배정 실행
        BatchAssignmentResult result = batchAssigner.assignAll(List.of(rsv1, rsv2, rsv3));

        // Then: 3건 모두 배정 실패
        assertEquals(0, result.getSuccessCount());
        assertEquals(3, result.getFailureCount());

        var failures = result.getFailedAssignments();

        // 1번째 실패자: 킵 수량(1실)에 직접 걸려 튕김 -> '안전 쿼터 차단'
        String reason1 = failures.get(0).reason();
        assertTrue(reason1.contains("안전 쿼터"), "첫 번째 탈락자는 안전 쿼터 차단 사유가 명시되어야 합니다: " + reason1);
        assertTrue(reason1.contains("우선 구제 후보"), "우선 구제 후보 문구가 포함되어야 합니다: " + reason1);

        // 2번째, 3번째 실패자: 가용 재고 자체가 없으므로 '전량 매진'
        String reason2 = failures.get(1).reason();
        String reason3 = failures.get(2).reason();
        assertTrue(reason2.contains("가용 공실 매진") || reason2.contains("전량 소진"), "두 번째 탈락자는 전량 매진이어야 합니다: " + reason2);
        assertTrue(reason3.contains("가용 공실 매진") || reason3.contains("전량 소진"), "세 번째 탈락자는 전량 매진이어야 합니다: " + reason3);
    }

    @Test
    @DisplayName("[Alert 판별: 물리적 공실 부족] 하드 태그 충족 방이 아예 없어서 차선 배정된 경우 '객실 매진' 사유를 생성해야 한다")
    void checkHardRequestAlerts_PhysicalShortage_CreatesSoldOutReason() {
        // Given: 슈페리어 트윈 객실 전체에 대해 저층(LOW_FLOOR: HARD) 방이 0실인 상황 연출
        // 3~6층의 모든 슈페리어 트윈을 미리 점유
        StayPeriod stay = new StayPeriod(today, 1);
        roomRepository.findAll().stream()
                .filter(r -> r.getRoomType() == RoomType.SUPERIOR_TWIN)
                .filter(r -> r.getFloor() <= 6)
                .forEach(r -> r.bookPeriod(stay));

        // 고객은 저층(LOW_FLOOR: HARD) 요청
        TagPreference pref = new TagPreference(Set.of(RoomTag.LOW_FLOOR.code()), Set.of());
        Reservation lowFloorGuest = new Reservation(
                "RSV-LOW", "어르신손님", RoomType.SUPERIOR_TWIN, today, 1, "낮은 층 주세요", GuestPreference.empty()
        ).withTagPreference(pref);

        // When: 배정 실행 (저층이 없으므로 상층부에 배정됨)
        Optional<Room> assignedOpt = roomAssigner.assign(lowFloorGuest);
        assertTrue(assignedOpt.isPresent());
        Room assignedRoom = assignedOpt.get();
        assertTrue(assignedRoom.getFloor() > 6);

        // Then: Alert 검증
        List<AssignmentAlert> alerts = roomAssigner.checkHardRequestAlerts(lowFloorGuest, assignedRoom);
        assertEquals(1, alerts.size());
        AssignmentAlert alert = alerts.get(0);
        assertEquals("저층", alert.unfulfilledTag());
        assertTrue(alert.reason().contains("객실 매진으로 차선 객실에 배정됨"),
                "물리적 공실이 없는 경우 객실 매진 사유가 출력되어야 합니다: " + alert.reason());
    }

    @Test
    @DisplayName("[Alert 판별: 소프트 태그 미충족 무시] 취향 선호(SOFT) 태그는 미충족되어도 경고 알림(Alert)이 발생하지 않아야 한다")
    void checkHardRequestAlerts_SoftTagUnfulfilled_NoAlert() {
        // Given: 도쿄타워 뷰(VIEW_TOKYO_TOWER: SOFT) 희망 고객
        TagPreference pref = new TagPreference(Set.of("VIEW_TOKYO_TOWER"), Set.of());
        Reservation towerGuest = new Reservation(
                "RSV-SOFT", "전망선호손님", RoomType.MODERATE_DOUBLE, today, 1, "타워 뷰 부탁", GuestPreference.empty()
        ).withTagPreference(pref);

        // 타워 뷰가 없는 모더레이트 더블 객실에 배정
        Room normalRoom = roomRepository.findByRoomNumber("0403").orElseThrow();

        // When: Alert 검증
        List<AssignmentAlert> alerts = roomAssigner.checkHardRequestAlerts(towerGuest, normalRoom);

        // Then: SOFT 태그이므로 미충족 경고가 없어야 함(empty)
        assertTrue(alerts.isEmpty(), "SOFT 태그 미충족은 프론트 데스크 경고(Alert) 대상이 아닙니다.");
    }
}