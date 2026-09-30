package com.hotel.api;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomType;
import com.hotel.repository.ReservationRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    private final LocalDate targetDate = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        reservationRepository.clear();
    }

    @Test
    @WithMockUser(username = "staff_member", authorities = {"ROLE_STAFF"})
    @DisplayName("[API] 예약 목록 검색 조건에 맞게 필터링된 결과가 반환되어야 한다")
    void searchReservations_Success() throws Exception {
        // Given: 2건의 예약 저장 (1건은 9/20 PENDING, 1건은 9/21 ASSIGNED)
        Reservation r1 = new Reservation("RSV-01", "Tanaka", RoomType.MODERATE_DOUBLE, targetDate, 2, null, GuestPreference.empty());
        Reservation r2 = new Reservation("RSV-02", "Suzuki", RoomType.SUPERIOR_TWIN, targetDate.plusDays(1), 1, null, GuestPreference.empty());
        r2.assignRoom("0501");

        reservationRepository.save(r1);
        reservationRepository.save(r2);

        // When & Then 1: 9/20 체크인 일자 필터링
        mockMvc.perform(get("/api/reservations")
                        .param("checkInDate", "2026-09-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reservationId").value("RSV-01"));

        // When & Then 2: 고객명 검색 필터링 ("suzuki")
        mockMvc.perform(get("/api/reservations")
                        .param("guestName", "suzuki"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reservationId").value("RSV-02"));
    }

    @Test
    @WithMockUser(username = "staff_member", authorities = {"ROLE_STAFF"})
    @DisplayName("[API] 정직원(STAFF)은 당일 일괄 배정을 성공적으로 실행할 수 있다")
    void batchAssign_Staff_Success() throws Exception {
        Reservation rsv = new Reservation(
                "RSV-API-01",
                "Hong GilDong",
                RoomType.MODERATE_DOUBLE,
                targetDate,
                2,
                "고층 부탁드립니다",
                GuestPreference.empty()
        );
        reservationRepository.save(rsv);

        String jsonPayload = """
                {
                    "checkInDate": "2026-09-20"
                }
                """;

        mockMvc.perform(post("/api/reservations/batch-assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.successfulAssignments[0].reservationId").value("RSV-API-01"));
    }

    @Test
    @WithMockUser(username = "part_time_staff", authorities = {"ROLE_PART_TIME"})
    @DisplayName("[보안 인가] 아르바이트(PART_TIME)는 일괄 배정 권한이 없어 403 Forbidden을 반환해야 한다")
    void batchAssign_PartTime_Forbidden() throws Exception {
        String jsonPayload = """
                {
                    "checkInDate": "2026-09-20"
                }
                """;

        mockMvc.perform(post("/api/reservations/batch-assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "part_time_staff", authorities = {"ROLE_PART_TIME"})
    @DisplayName("[API] 아르바이트(PART_TIME)는 단일 예약 조회가 정상 동작해야 한다")
    void getReservation_PartTime_Success() throws Exception {
        mockMvc.perform(get("/api/reservations/NON-EXISTENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("[보안 인증] 인증 토큰 없는 익명 요청은 401 Unauthorized를 반환해야 한다")
    void apiWithoutAuth_Returns401() throws Exception {
        mockMvc.perform(get("/api/reservations/RSV-001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "staff_member", authorities = {"ROLE_STAFF"})
    @DisplayName("[API] page, size를 주면 해당 페이지만 반환하고 전체 건수를 함께 알려준다")
    void searchReservations_Paging() throws Exception {
        for (int i = 1; i <= 5; i++) {
            reservationRepository.save(new Reservation("RSV-PG-0" + i, "Guest" + i, RoomType.MODERATE_DOUBLE,
                    targetDate, 1, null, GuestPreference.empty()));
        }

        mockMvc.perform(get("/api/reservations").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].reservationId").value("RSV-PG-03"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.total").value(5));

        mockMvc.perform(get("/api/reservations").param("page", "9").param("size", "2"))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(5));
    }

    @Test
    @WithMockUser(username = "staff_member", authorities = {"ROLE_STAFF"})
    @DisplayName("[API] size 상한(500)을 넘겨 요청해도 상한까지만 적용된다")
    void searchReservations_SizeIsCapped() throws Exception {
        mockMvc.perform(get("/api/reservations").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(500));
    }

    private void saveReservationWithSecrets() {
        Reservation r = new Reservation("RSV-SECRET", "Tanaka", RoomType.MODERATE_DOUBLE, targetDate, 1, 1,
                "요청", "<xml>OTA 원문</xml>", GuestPreference.empty(), null, null, null, null, null);
        r.updateOperationalDetails(null, null, null, "내부 인계 메모");
        reservationRepository.save(r);
    }

    @Test
    @WithMockUser(username = "part_timer", authorities = {"ROLE_PART_TIME"})
    @DisplayName("[API] 아르바이트에게는 OTA 전문 원문과 내부 메모가 내려가지 않는다")
    void reservationResponse_HidesSensitiveFieldsFromPartTime() throws Exception {
        saveReservationWithSecrets();

        mockMvc.perform(get("/api/reservations/RSV-SECRET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rawXmlPayload").doesNotExist())
                .andExpect(jsonPath("$.data.internalStaffMemo").doesNotExist())
                .andExpect(jsonPath("$.data.guestName").value("Tanaka"));
    }

    @Test
    @WithMockUser(username = "staff_member", authorities = {"ROLE_STAFF"})
    @DisplayName("[API] 정직원에게는 내부 메모가 보이지만 OTA 전문 원문은 여전히 내려가지 않는다")
    void reservationResponse_ShowsMemoToStaffButNeverRawXml() throws Exception {
        saveReservationWithSecrets();

        mockMvc.perform(get("/api/reservations/RSV-SECRET"))
                .andExpect(jsonPath("$.data.internalStaffMemo").value("내부 인계 메모"))
                .andExpect(jsonPath("$.data.rawXmlPayload").doesNotExist());

        mockMvc.perform(get("/api/reservations").param("reservationId", "RSV-SECRET"))
                .andExpect(jsonPath("$.data.items[0].rawXmlPayload").doesNotExist());
    }
}
