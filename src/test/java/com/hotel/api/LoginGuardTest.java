package com.hotel.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;
import com.hotel.repository.StaffRepository;
import com.hotel.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoginGuardTest {

    private static final String STAFF_ID = "guard-staff";
    private static final String PASSWORD = "correct-horse-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void resetFixtures() {
        // 저장할 때마다 실패 횟수와 잠금, 활성 상태가 초기값으로 돌아간다.
        staffRepository.save(new StaffAccount(STAFF_ID, passwordEncoder.encode(PASSWORD), "가드 테스트", StaffRole.ROLE_STAFF));
        staffRepository.save(new StaffAccount("guard-admin", passwordEncoder.encode(PASSWORD), "가드 관리자", StaffRole.ROLE_ADMIN));
    }

    private ResultActions login(String staffId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"staffId\":\"%s\",\"password\":\"%s\"}", staffId, password)));
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    @Test
    @DisplayName("[로그인 잠금] 5번 연속 틀리면 맞는 비밀번호로도 로그인할 수 없다")
    void locksAfterFiveFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(STAFF_ID, "wrong-password").andExpect(status().isUnauthorized());
        }

        login(STAFF_ID, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[로그인 잠금] 중간에 성공하면 실패 횟수가 초기화된다")
    void successResetsFailureCount() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(STAFF_ID, "wrong-password").andExpect(status().isUnauthorized());
        }
        login(STAFF_ID, PASSWORD).andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            login(STAFF_ID, "wrong-password").andExpect(status().isUnauthorized());
        }
        login(STAFF_ID, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("[로그인 잠금] 관리자가 계정을 다시 활성화하면 잠금이 풀린다")
    void adminEnableClearsLock() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(STAFF_ID, "wrong-password");
        }
        String adminToken = objectMapper.readTree(
                        login("guard-admin", PASSWORD).andReturn().getResponse().getContentAsByteArray())
                .path("data").path("token").asText();

        mockMvc.perform(withToken(patch("/api/admin/staff/" + STAFF_ID + "/enabled"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        login(STAFF_ID, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("[로그인] ID 대소문자와 앞뒤 공백은 무시한다")
    void staffIdIsNormalized() throws Exception {
        login("  Guard-STAFF ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffId").value(STAFF_ID));
    }

    @Test
    @DisplayName("[로그인] 없는 ID와 틀린 비밀번호는 같은 응답이라 계정 존재 여부를 알 수 없다")
    void unknownIdAndWrongPasswordLookTheSame() throws Exception {
        String unknown = login("no-such-user", "whatever-123").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrong = login(STAFF_ID, "wrong-password").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals(unknown, wrong);
    }

    @Test
    @DisplayName("[토큰 폐기] 계정을 비활성화하면 이미 발급된 토큰도 즉시 거부된다")
    void disabledAccountTokenIsRejected() throws Exception {
        String token = tokenProvider.generateToken(STAFF_ID, StaffRole.ROLE_STAFF);
        mockMvc.perform(withToken(get("/api/reservations/NO-SUCH-RSV"), token)).andExpect(status().isNotFound());

        staffRepository.updateEnabled(STAFF_ID, false);

        mockMvc.perform(withToken(get("/api/reservations/NO-SUCH-RSV"), token)).andExpect(status().isUnauthorized());
        login(STAFF_ID, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[토큰 폐기] 토큰의 role 클레임이 아니라 DB의 역할로 인가한다")
    void roleComesFromDatabaseNotToken() throws Exception {
        String forgedAdminToken = tokenProvider.generateToken(STAFF_ID, StaffRole.ROLE_ADMIN);

        mockMvc.perform(withToken(post("/api/admin/staff"), forgedAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":\"x-new\",\"password\":\"password1234\",\"name\":\"신규\",\"role\":\"ROLE_STAFF\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[토큰] 계정이 삭제되었거나 존재하지 않는 subject의 토큰은 거부된다")
    void tokenForUnknownAccountIsRejected() throws Exception {
        String token = tokenProvider.generateToken("ghost-user", StaffRole.ROLE_ADMIN);

        mockMvc.perform(withToken(get("/api/reservations/NO-SUCH-RSV"), token)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[계정 관리] 본인 계정은 비활성화할 수 없고, 없는 계정은 404다")
    void cannotDisableSelfOrUnknown() throws Exception {
        String adminToken = tokenProvider.generateToken("guard-admin", StaffRole.ROLE_ADMIN);

        mockMvc.perform(withToken(patch("/api/admin/staff/guard-admin/enabled"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(withToken(patch("/api/admin/staff/nobody-here/enabled"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[계정 관리] 일반 직원은 계정 활성 상태를 바꿀 수 없다")
    void staffCannotToggleEnabled() throws Exception {
        String staffToken = tokenProvider.generateToken(STAFF_ID, StaffRole.ROLE_STAFF);

        mockMvc.perform(withToken(patch("/api/admin/staff/guard-admin/enabled"), staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }
}
