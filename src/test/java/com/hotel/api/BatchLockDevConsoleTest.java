package com.hotel.api;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.repository.ReservationRepository;
import com.hotel.service.BatchOperationGuard;
import com.hotel.service.ReservationService;
import com.hotel.service.TestDataGeneratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 개발자 콘솔(dev 프로필)의 "예약 인입" 계열도 AI 태그 분석과 일괄 배정을 돌리는 동안에는
 * 다른 예약 변경을 막아야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BatchLockDevConsoleTest {

    private static final LocalDate DAY = LocalDate.of(2027, 8, 1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BatchOperationGuard guard;

    @Autowired
    private ReservationRepository reservationRepository;

    // 실제 생성기는 AI 태그 분석과 일괄 배정을 세 번 돌리므로 테스트에서는 잠금 상태만 기록하는 대역으로 바꾼다.
    // (Mockito 스파이는 이 프로젝트의 JDK와 Byte Buddy 조합에서 동작하지 않는다.)
    static final AtomicReference<BatchOperationGuard.Active> SEEN_DURING_RUN = new AtomicReference<>();

    @TestConfiguration
    static class RecordingGeneratorConfig {
        @Bean
        @Primary
        TestDataGeneratorService recordingGenerator(ReservationRepository repository,
                                                    ReservationService reservationService,
                                                    BatchOperationGuard guard) {
            return new TestDataGeneratorService(repository, reservationService) {
                @Override
                public List<Reservation> generate50DynamicReservations(LocalDate baseDate) {
                    SEEN_DURING_RUN.set(guard.current().orElse(null));
                    return List.of();
                }
            };
        }
    }

    @AfterEach
    void cleanUp() {
        reservationRepository.clear();
        SEEN_DURING_RUN.set(null);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
    @DisplayName("[개발자 콘솔] 테스트 데이터 생성(AI 태그 분석·배정 포함)이 도는 동안에는 일괄 잠금이 잡혀 있다")
    void generateTestDataHoldsTheBatchLock() throws Exception {
        mockMvc.perform(post("/api/reservations/generate-test-data").param("baseDate", DAY.toString()))
                .andExpect(status().isOk());

        BatchOperationGuard.Active active = SEEN_DURING_RUN.get();
        assertNotNull(active, "생성이 도는 동안에는 잠금이 잡혀 있어야 한다");
        assertEquals("TEST_DATA_GENERATION", active.operation());
        assertEquals("admin", active.staffId());
        assertTrue(guard.current().isEmpty(), "끝난 뒤에는 잠금이 풀려야 한다");
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
    @DisplayName("[개발자 콘솔] 일괄 작업 중에는 전체 초기화 같은 시뮬레이션 요청도 423으로 막힌다")
    void simulationEndpointsAreLockedWhileBatchRuns() throws Exception {
        reservationRepository.save(new Reservation("RSV-SIM-LOCK", "Guest", RoomType.SUPERIOR_TWIN, DAY, 1, null, GuestPreference.empty()));

        guard.runExclusive("BATCH_ASSIGN", "AI 일괄 자동 배정", "staff", DAY, () -> {
            try {
                mockMvc.perform(post("/api/simulation/clear")).andExpect(status().isLocked());
                mockMvc.perform(post("/api/simulation/reset-all-settings")).andExpect(status().isLocked());
                mockMvc.perform(post("/api/simulation/seed-samples")).andExpect(status().isLocked());
                mockMvc.perform(post("/api/reservations/generate-test-data")).andExpect(status().isLocked());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return null;
        });

        assertTrue(reservationRepository.findById("RSV-SIM-LOCK").isPresent(), "잠긴 요청이 데이터를 지우면 안 된다");
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"ROLE_ADMIN"})
    @DisplayName("[개발자 콘솔] 일괄 작업이 없을 때는 시뮬레이션 요청이 정상 동작한다")
    void simulationEndpointsWorkWhenIdle() throws Exception {
        mockMvc.perform(post("/api/simulation/clear")).andExpect(status().isOk());
    }
}
