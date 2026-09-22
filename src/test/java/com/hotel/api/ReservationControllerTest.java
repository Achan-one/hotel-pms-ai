package com.hotel.api;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

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

    @Autowired
    private RoomRepository roomRepository;

    private final LocalDate targetDate = LocalDate.of(2026, 9, 20);

    @BeforeEach
    void setUp() {
        reservationRepository.clear();
    }

    @Test
    @DisplayName("[API] 당일 일괄 배정 트리거가 정상 작동하여 성공 목록을 반환해야 한다")
    void batchAssign_ApiTest() throws Exception {
        // 실제 존재하는 MODERATE_DOUBLE 타입으로 PENDING 예약 생성
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
                .andExpect(jsonPath("$.data.successfulAssignments[0].reservationId").value("RSV-API-01"))
                .andExpect(jsonPath("$.data.successfulAssignments[0].assignedRoomNumber").isNotEmpty());
    }

    @Test
    @DisplayName("[API] 단일 예약 조회 시 존재하지 않는 예약은 404를 반환해야 한다")
    void getReservation_NotFound() throws Exception {
        mockMvc.perform(get("/api/reservations/NON-EXISTENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}