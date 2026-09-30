package com.hotel.api;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import com.hotel.domain.StayPeriod;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.BatchOperationGuard;
import com.hotel.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BatchOperationApiTest {

    private static final LocalDate DAY = LocalDate.of(2027, 6, 10);
    private static final String DAY_JSON = "{\"checkInDate\":\"2027-06-10\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private BatchOperationGuard guard;

    @BeforeEach
    void setUp() {
        reservationRepository.clear();
    }

    @AfterEach
    void cleanUp() {
        reservationRepository.clear();
    }

    private Reservation reservation(String id, int nights) {
        Reservation r = new Reservation(id, "Guest-" + id, RoomType.SUPERIOR_TWIN, DAY, nights, null, GuestPreference.empty());
        reservationRepository.save(r);
        return r;
    }

    // ---------------- 일괄 해제 ----------------

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[일괄 해제] 선택한 날짜의 배정 완료 예약이 미배정으로 돌아가고 객실 스케줄이 풀린다")
    void batchUnassign_ReleasesAssignedReservationsAndRooms() throws Exception {
        reservation("RSV-UN-1", 2);
        reservation("RSV-UN-2", 1);
        reservationService.manualAssignRoom("RSV-UN-1", "0601");
        reservationService.manualAssignRoom("RSV-UN-2", "0602");

        mockMvc.perform(post("/api/reservations/batch-unassign").contentType(MediaType.APPLICATION_JSON).content(DAY_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releasedReservationIds.length()").value(2))
                .andExpect(jsonPath("$.data.keptInHouseCount").value(0));

        for (String id : new String[]{"RSV-UN-1", "RSV-UN-2"}) {
            Reservation after = reservationRepository.findById(id).orElseThrow();
            assertEquals(ReservationStatus.PENDING, after.getStatus());
            assertNull(after.getAssignedRoomNumber());
        }
        Room room = roomRepository.findByRoomNumber("0601").orElseThrow();
        assertTrue(room.isAvailable(new StayPeriod(DAY, 2)), "해제된 객실은 같은 기간에 다시 배정할 수 있어야 한다");
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[일괄 해제] 체크인한 예약과 다른 날짜의 예약은 건드리지 않는다")
    void batchUnassign_LeavesInHouseAndOtherDatesAlone() throws Exception {
        reservation("RSV-KEEP-IN", 2);
        reservationService.manualAssignRoom("RSV-KEEP-IN", "0603");
        reservationService.processCheckIn("RSV-KEEP-IN");

        Reservation otherDay = new Reservation("RSV-KEEP-OTHER", "Other", RoomType.SUPERIOR_TWIN, DAY.plusDays(1), 1, null, GuestPreference.empty());
        reservationRepository.save(otherDay);
        reservationService.manualAssignRoom("RSV-KEEP-OTHER", "0604");

        mockMvc.perform(post("/api/reservations/batch-unassign").contentType(MediaType.APPLICATION_JSON).content(DAY_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releasedReservationIds.length()").value(0))
                .andExpect(jsonPath("$.data.keptInHouseCount").value(1));

        assertEquals(ReservationStatus.CHECKED_IN, reservationRepository.findById("RSV-KEEP-IN").orElseThrow().getStatus());
        assertEquals("0603", reservationRepository.findById("RSV-KEEP-IN").orElseThrow().getAssignedRoomNumber());
        assertEquals(ReservationStatus.ASSIGNED, reservationRepository.findById("RSV-KEEP-OTHER").orElseThrow().getStatus());
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[일괄 해제] 0박으로 이월된 예약이 섞여 있어도 실패하지 않는다")
    void batchUnassign_HandlesZeroNightReservation() throws Exception {
        Reservation zero = reservation("RSV-ZERO", 1);
        reservationService.manualAssignRoom("RSV-ZERO", "0605");
        Reservation loaded = reservationRepository.findById("RSV-ZERO").orElseThrow();
        loaded.updateOperationalDetails(null, null, 0, null);
        reservationRepository.save(loaded);

        mockMvc.perform(post("/api/reservations/batch-unassign").contentType(MediaType.APPLICATION_JSON).content(DAY_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releasedReservationIds[0]").value("RSV-ZERO"));

        assertEquals(ReservationStatus.PENDING, reservationRepository.findById("RSV-ZERO").orElseThrow().getStatus());
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[일괄 해제] 체크인 일자가 없으면 400이다")
    void batchUnassign_RequiresDate() throws Exception {
        mockMvc.perform(post("/api/reservations/batch-unassign").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "part", authorities = {"ROLE_PART_TIME"})
    @DisplayName("[일괄 해제] 아르바이트는 실행할 수 없다")
    void batchUnassign_ForbiddenForPartTime() throws Exception {
        mockMvc.perform(post("/api/reservations/batch-unassign").contentType(MediaType.APPLICATION_JSON).content(DAY_JSON))
                .andExpect(status().isForbidden());
    }

    // ---------------- 일괄 작업 중 편집 차단 ----------------

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[편집 차단] 일괄 작업 중에는 예약을 바꾸는 모든 요청이 423으로 막히고 조회는 된다")
    void mutationsAreLockedWhileBatchRuns() throws Exception {
        reservation("RSV-LOCK-1", 1);

        guard.runExclusive("BATCH_ASSIGN", "AI 일괄 자동 배정", "admin", DAY, () -> {
            try {
                mockMvc.perform(post("/api/reservations/RSV-LOCK-1/manual-assign")
                                .contentType(MediaType.APPLICATION_JSON).content("{\"targetRoomNumber\":\"0606\"}"))
                        .andExpect(status().isLocked())
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("AI 일괄 자동 배정")));
                mockMvc.perform(patch("/api/reservations/RSV-LOCK-1/operational-override")
                                .contentType(MediaType.APPLICATION_JSON).content("{\"guestName\":\"변경\"}"))
                        .andExpect(status().isLocked());
                mockMvc.perform(post("/api/reservations/RSV-LOCK-1/check-in")).andExpect(status().isLocked());
                mockMvc.perform(delete("/api/reservations/RSV-LOCK-1/assign")).andExpect(status().isLocked());
                mockMvc.perform(post("/api/reservations/batch-assign")
                                .contentType(MediaType.APPLICATION_JSON).content(DAY_JSON))
                        .andExpect(status().isLocked());

                mockMvc.perform(get("/api/reservations/RSV-LOCK-1")).andExpect(status().isOk());
                mockMvc.perform(get("/api/reservations")).andExpect(status().isOk());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return null;
        });

        assertEquals(ReservationStatus.PENDING, reservationRepository.findById("RSV-LOCK-1").orElseThrow().getStatus(),
                "잠긴 요청은 예약을 바꾸지 못해야 한다");
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[편집 차단] 일괄 작업이 끝나면 다시 편집할 수 있다")
    void mutationsWorkAgainAfterBatchFinishes() throws Exception {
        reservation("RSV-LOCK-2", 1);
        guard.runExclusive("BATCH_ASSIGN", "AI 일괄 자동 배정", "admin", DAY, () -> null);

        mockMvc.perform(post("/api/reservations/RSV-LOCK-2/manual-assign")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetRoomNumber\":\"0607\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[편집 차단] 일괄 배정 요청이 끝나면 성공하든 실패하든 잠금이 남지 않는다")
    void batchAssignReleasesGuardWhenDone() throws Exception {
        // 미배정 예약이 없는 날짜를 쓴다. 있으면 AI 태그 분석이 실제 Gemini API를 호출해 비용이 든다.
        mockMvc.perform(post("/api/reservations/batch-assign")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"checkInDate\":\"2030-01-01\"}"))
                .andExpect(status().isOk());
        assertTrue(guard.current().isEmpty());

        mockMvc.perform(post("/api/reservations/batch-assign").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertTrue(guard.current().isEmpty());
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[편집 차단] 진행 중에는 편집 락 조회가 모든 예약을 일괄 작업에 잠긴 것으로 알려준다")
    void lockEndpointsReportBatchAsHolder() throws Exception {
        reservation("RSV-LOCK-4", 1);

        guard.runExclusive("BATCH_ASSIGN", "AI 일괄 자동 배정", "admin", DAY, () -> {
            try {
                mockMvc.perform(post("/api/reservations/RSV-LOCK-4/lock")
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.isLockedByOther").value(true))
                        .andExpect(jsonPath("$.data.lockedByBatch").value(true))
                        .andExpect(jsonPath("$.data.lockedByStaffName").value(org.hamcrest.Matchers.containsString("AI 일괄 자동 배정")));
                mockMvc.perform(get("/api/reservations/RSV-LOCK-4/lock"))
                        .andExpect(jsonPath("$.data.isLockedByOther").value(true));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return null;
        });

        mockMvc.perform(post("/api/reservations/RSV-LOCK-4/lock").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.data.isLockedByOther").value(false));
        mockMvc.perform(delete("/api/reservations/RSV-LOCK-4/lock")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "part", authorities = {"ROLE_PART_TIME"})
    @DisplayName("[일괄 상태] 진행 여부를 모든 직원이 조회할 수 있고, 진행 정보가 담긴다")
    void batchStatusIsVisibleToEveryone() throws Exception {
        mockMvc.perform(get("/api/reservations/batch-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        guard.runExclusive("BATCH_UNASSIGN", "일괄 배정 해제", "admin", DAY, () -> {
            try {
                mockMvc.perform(get("/api/reservations/batch-status"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.active").value(true))
                        .andExpect(jsonPath("$.data.operation").value("BATCH_UNASSIGN"))
                        .andExpect(jsonPath("$.data.staffId").value("admin"))
                        .andExpect(jsonPath("$.data.targetDate").value("2027-06-10"));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return null;
        });
    }

    // ---------------- 태그 카탈로그 ----------------

    @Test
    @WithMockUser(username = "part", authorities = {"ROLE_PART_TIME"})
    @DisplayName("[태그 카탈로그] 아르바이트도 룸 매트릭스용 태그 이름 목록을 볼 수 있다")
    void tagCatalogIsReadableByPartTime() throws Exception {
        mockMvc.perform(get("/api/rooms/tag-catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='HIGH_FLOOR')].name").value(org.hamcrest.Matchers.hasItem("고층")));
    }

    @Test
    @DisplayName("[태그 카탈로그] 로그인하지 않으면 401이다")
    void tagCatalogRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/rooms/tag-catalog")).andExpect(status().isUnauthorized());
    }
}
