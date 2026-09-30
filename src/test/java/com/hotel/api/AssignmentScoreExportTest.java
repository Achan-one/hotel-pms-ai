package com.hotel.api;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.domain.TagPreference;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssignmentScoreExportTest {

    private static final LocalDate DAY = LocalDate.of(2031, 10, 5);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private RoomRepository roomRepository;

    @BeforeEach
    void setUp() {
        reservationRepository.clear();
        freeRooms();
        // 1204호에 도쿄타워 전망 태그를 붙여 요청하지 않은 손님에게 낭비 감점이 붙는 상황을 만든다.
        roomRepository.addTag("1204", "VIEW_TOKYO_TOWER_TEST");
    }

    // 다른 테스트가 같은 H2에 남긴 점유가 있어도 영향받지 않도록 이 테스트가 쓰는 객실의 일정을 비운다.
    private void freeRooms() {
        for (String number : new String[]{"1204", "1205", "0501"}) {
            roomRepository.findByRoomNumberForUpdate(number).ifPresent(room -> {
                room.getBookedPeriods().forEach(room::cancelPeriod);
                roomRepository.save(room);
            });
        }
    }

    @AfterEach
    void cleanUp() {
        reservationRepository.clear();
        freeRooms();
        roomRepository.removeTagFromAll("VIEW_TOKYO_TOWER_TEST");
    }

    private Reservation assigned(String id, String guest, String room, Set<String> preferred) {
        Reservation r = new Reservation(id, guest, RoomType.SUPERIOR_TWIN, DAY, 2, 1, "요청",
                GuestPreference.empty(), new TagPreference(preferred, Set.of()), null, null, null, null);
        reservationRepository.save(r);
        reservationService.manualAssignRoom(id, room);
        return reservationRepository.findById(id).orElseThrow();
    }

    private String csv(org.springframework.test.web.servlet.MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
    @DisplayName("[점수 엑스포트] 관리자는 배정 예약의 총점과 규칙별 점수 내역을 받는다")
    void adminGetsScoreBreakdown() throws Exception {
        assigned("RSV-SC-1", "SecretGuestName", "1204", Set.of("HIGH_FLOOR"));

        String body = csv(mockMvc.perform(get("/api/reports/assignment-scores/csv").param("checkInDate", DAY.toString()))
                .andExpect(status().isOk()).andReturn());

        assertTrue(body.startsWith("﻿PMS예약번호,예약ID,객실타입,체크인,박수,상태,배정호실,층,객실 보유 태그,희망 태그,기피 태그,총점,"), body.substring(0, 80));
        assertTrue(body.contains("선호 태그 가산,상반 조건 감점,기피 태그 감점,특수 태그 낭비 감점,개인 선호 점수,연박 가중,층 보정"));
        assertTrue(body.contains("RSV-SC-1"));
        assertTrue(body.contains("1204"));
        // 12층 HIGH_FLOOR 방에 고층 요청: +15, 요청하지 않은 특수 태그가 붙은 방이라 낭비 감점이 있고, 세부 내역 열에 그 설명이 담긴다.
        assertTrue(body.contains("선호 태그 가산(HIGH_FLOOR): +15"), body);
        assertTrue(body.contains("특수 태그 낭비 감점(VIEW_TOKYO_TOWER_TEST)"), body);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
    @DisplayName("[점수 엑스포트] 투숙객 이름은 파일에 들어가지 않는다")
    void guestNamesAreNotExported() throws Exception {
        assigned("RSV-SC-2", "SecretGuestName", "1205", Set.of());

        String body = csv(mockMvc.perform(get("/api/reports/assignment-scores/csv").param("checkInDate", DAY.toString()))
                .andExpect(status().isOk()).andReturn());

        assertFalse(body.contains("SecretGuestName"));
        assertTrue(body.contains("RSV-SC-2"));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
    @DisplayName("[점수 엑스포트] 항목별 점수 열의 합은 총점과 같다")
    void categoryColumnsSumToTotal() throws Exception {
        assigned("RSV-SC-3", "Guest", "0501", Set.of("HIGH_FLOOR", "QUIET_ZONE"));

        String body = csv(mockMvc.perform(get("/api/reports/assignment-scores/csv").param("checkInDate", DAY.toString()))
                .andExpect(status().isOk()).andReturn());

        String[] lines = body.split("\r\n");
        String[] header = lines[0].replace("﻿", "").split(",");
        String[] row = null;
        for (String line : lines) {
            if (line.contains("RSV-SC-3")) row = line.split(",", -1);
        }
        assertTrue(row != null, body);
        int totalIdx = java.util.Arrays.asList(header).indexOf("총점");
        int firstCategory = totalIdx + 1;
        int sum = 0;
        for (int i = firstCategory; i < firstCategory + 7; i++) sum += Integer.parseInt(row[i]);
        assertTrue(Integer.parseInt(row[totalIdx]) == sum, "총점 " + row[totalIdx] + " != 항목 합 " + sum);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
    @DisplayName("[점수 엑스포트] 필수(HARD) 요청을 충족하지 못한 배정은 표시된다")
    void unmetHardRequestIsFlagged() throws Exception {
        // 0501은 5층이라 LOW_FLOOR가 있다. 배리어프리(ACCESSIBLE, HARD)를 요청했지만 이 방에는 없다고 가정해 미충족으로 잡는다.
        roomRepository.removeTag("0501", "ACCESSIBLE");
        assigned("RSV-SC-4", "Guest", "0501", Set.of("ACCESSIBLE"));

        String body = csv(mockMvc.perform(get("/api/reports/assignment-scores/csv").param("checkInDate", DAY.toString()))
                .andExpect(status().isOk()).andReturn());

        assertTrue(body.contains("배리어프리"), body);
    }

    @Test
    @WithMockUser(username = "staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[점수 엑스포트] 정직원은 받을 수 없다")
    void staffIsForbidden() throws Exception {
        mockMvc.perform(get("/api/reports/assignment-scores/csv").param("checkInDate", DAY.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "part", authorities = {"ROLE_PART_TIME"})
    @DisplayName("[점수 엑스포트] 아르바이트는 받을 수 없다")
    void partTimeIsForbidden() throws Exception {
        mockMvc.perform(get("/api/reports/assignment-scores/csv").param("checkInDate", DAY.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[점수 엑스포트] 로그인하지 않으면 401이다")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/reports/assignment-scores/csv").param("checkInDate", DAY.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
    @DisplayName("[점수 엑스포트] 일자를 빼면 400이다")
    void dateIsRequired() throws Exception {
        mockMvc.perform(get("/api/reports/assignment-scores/csv")).andExpect(status().isBadRequest());
    }
}
