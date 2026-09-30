package com.hotel.api;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReportExportControllerTest {

    private static final LocalDate MAR4 = LocalDate.of(2027, 3, 4);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @BeforeEach
    void setUp() {
        reservationRepository.clear();
        reservationRepository.save(guest("RSV-M4", "Mar4Guest", MAR4, 1, "VIP 응대 필요"));
        reservationRepository.save(guest("RSV-M5", "Mar5Guest", MAR4.plusDays(1), 2, null));
        reservationRepository.save(guest("RSV-M9", "Mar9Guest", MAR4.plusDays(5), 1, null));
    }

    @AfterEach
    void cleanUp() {
        reservationRepository.clear();
    }

    private Reservation guest(String id, String name, LocalDate checkIn, int nights, String memo) {
        Reservation r = new Reservation(id, name, RoomType.SUPERIOR_TWIN, checkIn, nights, "요청", GuestPreference.empty());
        if (memo != null) r.updateOperationalDetails(null, null, null, memo);
        return r;
    }

    private String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[예약자 CSV] 3/4~3/4는 3/4 체크인만, 3/4~3/5는 3/4와 3/5 체크인이 나온다")
    void reservationsCsv_SingleDayAndRange() throws Exception {
        String single = body(mockMvc.perform(get("/api/reports/reservations/csv")
                        .param("startDate", "2027-03-04").param("endDate", "2027-03-04"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(single.contains("RSV-M4"));
        assertFalse(single.contains("RSV-M5"));

        String range = body(mockMvc.perform(get("/api/reports/reservations/csv")
                        .param("startDate", "2027-03-04").param("endDate", "2027-03-05"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(range.contains("RSV-M4"));
        assertTrue(range.contains("RSV-M5"));
        assertFalse(range.contains("RSV-M9"));
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[예약자 CSV] endDate를 생략하면 startDate 하루만 조회한다")
    void reservationsCsv_EndDateDefaultsToStartDate() throws Exception {
        String csv = body(mockMvc.perform(get("/api/reports/reservations/csv").param("startDate", "2027-03-04"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename=")))
                .andReturn());

        assertTrue(csv.contains("RSV-M4"));
        assertFalse(csv.contains("RSV-M5"));
    }

    @Test
    @WithMockUser(username = "part", authorities = {"ROLE_PART_TIME"})
    @DisplayName("[예약자 CSV] 아르바이트가 받은 파일에는 내부 메모가 없다")
    void reservationsCsv_PartTimeDoesNotGetStaffMemo() throws Exception {
        String csv = body(mockMvc.perform(get("/api/reports/reservations/csv").param("startDate", "2027-03-04"))
                .andExpect(status().isOk()).andReturn());

        assertTrue(csv.contains("RSV-M4"));
        assertFalse(csv.contains("VIP 응대 필요"));
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[예약자 CSV] 정직원이 받은 파일에는 내부 메모가 있다")
    void reservationsCsv_StaffGetsStaffMemo() throws Exception {
        String csv = body(mockMvc.perform(get("/api/reports/reservations/csv").param("startDate", "2027-03-04"))
                .andExpect(status().isOk()).andReturn());

        assertTrue(csv.contains("VIP 응대 필요"));
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[예약자 CSV] 시작일이 종료일보다 늦으면 400이다")
    void reservationsCsv_InvalidRangeIs400() throws Exception {
        mockMvc.perform(get("/api/reports/reservations/csv")
                        .param("startDate", "2027-03-05").param("endDate", "2027-03-04"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[숙박자 CSV] 3/5~3/5는 3/5 밤에 묵는 사람(3/4 체크인 1박 제외), 3/4~3/5는 둘 다 나온다")
    void inHouseCsv_SingleDayAndRange() throws Exception {
        String single = body(mockMvc.perform(get("/api/reports/in-house/csv")
                        .param("startDate", "2027-03-05").param("endDate", "2027-03-05"))
                .andExpect(status().isOk()).andReturn());
        assertFalse(single.contains("RSV-M4"), "3/4에 체크인한 1박 손님은 3/5 밤에 묵지 않는다");
        assertTrue(single.contains("RSV-M5"));

        String range = body(mockMvc.perform(get("/api/reports/in-house/csv")
                        .param("startDate", "2027-03-04").param("endDate", "2027-03-05"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(range.contains("RSV-M4"));
        assertTrue(range.contains("RSV-M5"));
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[숙박자 CSV] 미래 날짜도 조회할 수 있고, 기존 targetDate 파라미터도 계속 동작한다")
    void inHouseCsv_FutureDatesAndLegacyTargetDate() throws Exception {
        mockMvc.perform(get("/api/reports/in-house/csv").param("startDate", "2030-01-01"))
                .andExpect(status().isOk());

        String legacy = body(mockMvc.perform(get("/api/reports/in-house/csv").param("targetDate", "2027-03-05"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(legacy.contains("RSV-M5"));
    }
}
