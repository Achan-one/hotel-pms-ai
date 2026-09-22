package com.hotel.api;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoomIndicatorControllerTest {

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
    @DisplayName("[룸 인디케이터] 정직원은 특정 일자 기준 191실 전체 룸 랙 매트릭스를 정상 조회할 수 있어야 한다")
    void getRoomIndicator_Staff_Success() throws Exception {
        // Given: 배정된 예약 1건 저장
        Reservation res = new Reservation(
                "RSV-IND-01", "Tanaka", RoomType.SUPERIOR_TWIN, targetDate, 2, "고층", GuestPreference.empty()
        );
        res.assignRoom("0301");
        reservationRepository.save(res);

        // When & Then: 숫자 키는 ['3'] 형태로 접근
        mockMvc.perform(get("/api/rooms/indicator")
                        .param("targetDate", "2026-09-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRooms").value(191))
                .andExpect(jsonPath("$.data.floorRooms['3']").isArray())
                .andExpect(jsonPath("$.data.floorRooms['3'][0].roomNumber").value("0301"))
                .andExpect(jsonPath("$.data.floorRooms['3'][0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.floorRooms['3'][0].guestName").value("Tanaka"));
    }

    @Test
    @WithMockUser(username = "part_time_staff", authorities = {"ROLE_PART_TIME"})
    @DisplayName("[보안 인가] 아르바이트 직원도 현장 업무를 위해 룸 인디케이터 조회가 허용되어야 한다")
    void getRoomIndicator_PartTime_Allowed() throws Exception {
        mockMvc.perform(get("/api/rooms/indicator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRooms").value(191));
    }

    @Test
    @DisplayName("[보안 인증] 미인증 익명 사용자의 룸 인디케이터 접근은 401 Unauthorized로 차단되어야 한다")
    void getRoomIndicator_Anonymous_Returns401() throws Exception {
        mockMvc.perform(get("/api/rooms/indicator"))
                .andExpect(status().isUnauthorized());
    }
}