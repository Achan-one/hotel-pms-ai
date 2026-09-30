package com.hotel.security;

import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;
import com.hotel.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MockMvc는 서블릿 컨테이너의 에러 디스패치를 거치지 않아 403이 401로 바뀌는 문제를 잡지 못한다.
 * 실제 서버를 띄워 상태 코드가 그대로 나가는지 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealServerAuthStatusTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void createStaff() {
        staffRepository.save(new StaffAccount("realsrv-staff", "hash", "정직원", StaffRole.ROLE_STAFF));
    }

    private ResponseEntity<String> post(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
    }

    @Test
    @DisplayName("[실서버] 로그인했지만 권한이 부족하면 401이 아니라 403이다")
    void authenticatedButForbiddenIs403() {
        String token = tokenProvider.generateToken("realsrv-staff", StaffRole.ROLE_STAFF);

        assertEquals(HttpStatus.FORBIDDEN, post("/api/admin/staff", token).getStatusCode());
    }

    @Test
    @DisplayName("[실서버] 토큰이 없으면 401이다")
    void anonymousIs401() {
        assertEquals(HttpStatus.UNAUTHORIZED, post("/api/admin/staff", null).getStatusCode());
    }
}
