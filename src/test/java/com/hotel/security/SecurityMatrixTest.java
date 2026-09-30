package com.hotel.security;

import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;
import com.hotel.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig의 인가 규칙을 역할별로 확인한다.
 * 허용 방향은 실제로 데이터를 바꾸는 API를 실행하게 되므로, 여기서는 "막혀야 하는 역할이 막히는가"와
 * 부작용 없는 조회 API의 허용 여부만 본다. 인가는 컨트롤러보다 앞서 판단되므로 본문은 비어 있어도 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void createAccountsForEachRole() {
        staffRepository.save(new StaffAccount("matrix-admin", "hash", "관리자", StaffRole.ROLE_ADMIN));
        staffRepository.save(new StaffAccount("matrix-staff", "hash", "정직원", StaffRole.ROLE_STAFF));
        staffRepository.save(new StaffAccount("matrix-part", "hash", "아르바이트", StaffRole.ROLE_PART_TIME));
    }

    private String tokenFor(StaffRole role) {
        return switch (role) {
            case ROLE_ADMIN -> tokenProvider.generateToken("matrix-admin", role);
            case ROLE_STAFF -> tokenProvider.generateToken("matrix-staff", role);
            default -> tokenProvider.generateToken("matrix-part", role);
        };
    }

    private MockHttpServletRequestBuilder build(HttpMethod method, String path) {
        return request(method, path).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    static Stream<Arguments> deniedRules() {
        StaffRole staff = StaffRole.ROLE_STAFF;
        StaffRole part = StaffRole.ROLE_PART_TIME;
        return Stream.of(
                // 관리자 전용
                Arguments.of(HttpMethod.POST, "/api/admin/staff", staff),
                Arguments.of(HttpMethod.POST, "/api/admin/staff", part),
                Arguments.of(HttpMethod.POST, "/api/admin/tags", staff),
                Arguments.of(HttpMethod.PATCH, "/api/admin/staff/someone/enabled", staff),
                Arguments.of(HttpMethod.PATCH, "/api/admin/staff/someone/enabled", part),
                Arguments.of(HttpMethod.PUT, "/api/system/business-date", staff),
                Arguments.of(HttpMethod.PUT, "/api/system/business-date", part),
                Arguments.of(HttpMethod.POST, "/api/accounting/charge-codes", staff),
                Arguments.of(HttpMethod.DELETE, "/api/accounting/charge-codes/MINIBAR", part),
                // 관리자와 정직원만
                Arguments.of(HttpMethod.GET, "/api/system/unchecked-arrivals", part),
                Arguments.of(HttpMethod.POST, "/api/system/rollover-unchecked-arrivals", part),
                Arguments.of(HttpMethod.GET, "/api/admin/tags", part),
                Arguments.of(HttpMethod.POST, "/api/admin/tags", part),
                Arguments.of(HttpMethod.DELETE, "/api/admin/tags/ANY", part),
                Arguments.of(HttpMethod.POST, "/api/reservations/batch-assign", part),
                Arguments.of(HttpMethod.POST, "/api/reservations/R1/room-change", part),
                Arguments.of(HttpMethod.POST, "/api/reservations/R1/manual-assign", part),
                Arguments.of(HttpMethod.DELETE, "/api/reservations/R1/assign", part),
                Arguments.of(HttpMethod.PATCH, "/api/reservations/R1/operational-override", part),
                Arguments.of(HttpMethod.PATCH, "/api/reservations/R1/operational-tags", part),
                Arguments.of(HttpMethod.PUT, "/api/reservations/R1/daily-rates", part),
                Arguments.of(HttpMethod.POST, "/api/reservations/night-audit", part),
                Arguments.of(HttpMethod.GET, "/api/accounting/city-ledger", part),
                // dev 전용 개발자 콘솔 API도 아르바이트는 쓸 수 없다
                Arguments.of(HttpMethod.POST, "/api/simulation/clear", part),
                Arguments.of(HttpMethod.POST, "/api/simulation/bulk-simulate-50-and-30", part),
                Arguments.of(HttpMethod.POST, "/api/reservations/generate-test-data", part)
        );
    }

    @ParameterizedTest(name = "[{2}] {0} {1} → 403")
    @MethodSource("deniedRules")
    @DisplayName("[인가] 권한이 부족한 역할은 403으로 막힌다")
    void insufficientRoleIsForbidden(HttpMethod method, String path, StaffRole role) throws Exception {
        mockMvc.perform(build(method, path).header("Authorization", "Bearer " + tokenFor(role)))
                .andExpect(status().isForbidden());
    }

    static Stream<Arguments> protectedEndpoints() {
        return deniedRules().map(a -> Arguments.of(a.get()[0], a.get()[1]))
                .distinct();
    }

    @ParameterizedTest(name = "{0} {1} → 401")
    @MethodSource("protectedEndpoints")
    @DisplayName("[인증] 토큰이 없으면 모든 보호 API가 401이다")
    void anonymousIsUnauthorized(HttpMethod method, String path) throws Exception {
        mockMvc.perform(build(method, path)).andExpect(status().isUnauthorized());
    }

    static Stream<Arguments> readOnlyEndpointsOpenToEveryStaffRole() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (StaffRole role : new StaffRole[]{StaffRole.ROLE_ADMIN, StaffRole.ROLE_STAFF, StaffRole.ROLE_PART_TIME}) {
            b.add(Arguments.of("/api/system/business-date", role));
            b.add(Arguments.of("/api/accounting/charge-codes", role));
            b.add(Arguments.of("/api/rooms/indicator", role));
        }
        return b.build();
    }

    @ParameterizedTest(name = "[{1}] GET {0} → 200")
    @MethodSource("readOnlyEndpointsOpenToEveryStaffRole")
    @DisplayName("[인가] 조회 전용 API는 모든 직원 역할이 사용할 수 있다")
    void readOnlyEndpointsAreOpen(String path, StaffRole role) throws Exception {
        mockMvc.perform(build(HttpMethod.GET, path).header("Authorization", "Bearer " + tokenFor(role)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[인증] 형식이 깨진 Bearer 토큰은 401이다")
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(build(HttpMethod.GET, "/api/rooms/indicator").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
