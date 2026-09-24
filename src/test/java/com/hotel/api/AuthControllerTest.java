package com.hotel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;
import com.hotel.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // staff 계정 픽스처 보장
        staffRepository.save(new StaffAccount(
                "staff",
                passwordEncoder.encode("hotel1234"),
                "정규사원",
                StaffRole.ROLE_STAFF
        ));
    }

    @Test
    @DisplayName("[인증] 유효한 계정으로 로그인 시 200 OK와 함께 JWT 토큰이 발급되어야 한다")
    void login_Success() throws Exception {
        String payload = """
                {
                    "staffId": "staff",
                    "password": "hotel1234"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.staffId").value("staff"))
                .andExpect(jsonPath("$.data.role").value("ROLE_STAFF"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    @DisplayName("[인증 실패] 잘못된 비밀번호 입력 시 401 Unauthorized를 반환해야 한다")
    void login_WrongPassword_Returns401() throws Exception {
        String payload = """
                {
                    "staffId": "staff",
                    "password": "wrong_password"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("[엔드투엔드] 로그인하여 발급받은 JWT 토큰으로 보호된 예약 API를 성공적으로 호출할 수 있어야 한다")
    void loginAndAccessProtectedApi_Success() throws Exception {
        String loginPayload = """
                {
                    "staffId": "staff",
                    "password": "hotel1234"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = root.path("data").path("token").asText();
        assertNotNull(token);

        mockMvc.perform(get("/api/reservations/RSV-TARGET")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}