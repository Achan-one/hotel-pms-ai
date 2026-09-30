package com.hotel.api;

import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;
import com.hotel.repository.StaffRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminStaffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffRepository staffRepository;

    @Test
    @WithMockUser(username = "admin_user", authorities = {"ROLE_ADMIN"})
    @DisplayName("[관리자 권한] 총지배인(ROLE_ADMIN)은 신규 직원을 정상적으로 등록할 수 있어야 한다")
    void createStaff_Admin_Success() throws Exception {
        String payload = """
                {
                    "staffId": "new_staff_01",
                    "password": "password1234",
                    "name": "신규사원",
                    "role": "ROLE_STAFF"
                }
                """;

        mockMvc.perform(post("/api/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertTrue(staffRepository.findByStaffId("new_staff_01").isPresent());
    }

    @Test
    @WithMockUser(username = "regular_staff", authorities = {"ROLE_STAFF"})
    @DisplayName("[보안 인가] 일반 사원(ROLE_STAFF)은 신규 직원을 등록할 수 없으며 403 Forbidden으로 차단되어야 한다")
    void createStaff_Staff_Forbidden() throws Exception {
        String payload = """
                {
                    "staffId": "hacker_staff",
                    "password": "password1234",
                    "name": "침입자",
                    "role": "ROLE_ADMIN"
                }
                """;

        mockMvc.perform(post("/api/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_user", authorities = {"ROLE_ADMIN"})
    @DisplayName("[유효성 검증] 이미 등록된 ID로 생성 시 400 Bad Request를 반환해야 한다")
    void createStaff_DuplicateId_BadRequest() throws Exception {
        // 픽스처로 'staff' 계정을 직접 만든다. .env나 다른 테스트가 남긴 데이터에 기대지 않는다.
        staffRepository.save(new StaffAccount("staff", "hash", "정규사원", StaffRole.ROLE_STAFF));

        String payload = """
                {
                    "staffId": "staff",
                    "password": "password1234",
                    "name": "중복계정",
                    "role": "ROLE_STAFF"
                }
                """;

        mockMvc.perform(post("/api/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}