package com.hotel.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.hotel.repository.StaffRepository;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;

    public SecurityConfig(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, StaffRepository staffRepository) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 세션과 쿠키를 쓰지 않는 JWT API라 CSRF는 끈다.
                .csrf(AbstractHttpConfigurer::disable)

                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // 기본 핸들러는 sendError(403)를 써서 /error로 재디스패치되고, 그 요청은 인증 정보가 없어 401로 바뀐다.
                        // 상태 코드만 직접 지정해 권한 부족이 403으로 나가게 한다.
                        .accessDeniedHandler((request, response, denied) -> response.setStatus(HttpStatus.FORBIDDEN.value()))
                )
                .authorizeHttpRequests(auth -> auth
                        // 0. CORS Preflight (OPTIONS) 사전 검사는 인증 없이 무조건 허용
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 1. 인증 공개 엔드포인트
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/staff").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/admin/staff/*/enabled").hasAuthority("ROLE_ADMIN")

                        // 2. 시스템 API. 영업일 조회는 로그인한 직원 모두, 보정은 관리자, 그 외는 관리자와 직원.
                        .requestMatchers(HttpMethod.GET, "/api/system/business-date").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/system/business-date").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/system/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")

                        // 3. 동적 태그 관리
                        // 태그 등록은 관리자만, 조회/수정/삭제/객실 매핑은 관리자와 정직원
                        .requestMatchers(HttpMethod.POST, "/api/admin/tags").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/admin/tags", "/api/admin/tags/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")

                        // 4. 룸 인디케이터
                        .requestMatchers(HttpMethod.GET, "/api/rooms/indicator", "/api/rooms/tag-catalog").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")

                        // 5. 배정, 룸 체인지, 오버라이드 및 요금 스케줄 갱신
                        .requestMatchers(HttpMethod.POST, "/api/reservations/batch-assign", "/api/reservations/batch-unassign").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/room-change").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/manual-assign").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.DELETE, "/api/reservations/*/assign").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.PATCH, "/api/reservations/*/operational-override").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.PATCH, "/api/reservations/*/operational-tags").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.PUT, "/api/reservations/*/daily-rates").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers("/api/reservations/*/lock").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")

                        // 6. 실무 리포트 CSV 다운로드
                        // 배정 점수 내역은 배정 규칙의 계산 방식이 드러나므로 관리자만 받을 수 있다. 아래 일반 규칙보다 먼저 선언한다.
                        .requestMatchers(HttpMethod.GET, "/api/reports/assignment-scores/csv").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/reports/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")

                        // 7. 시뮬레이터 및 나이트 오딧 실행
                        // 시뮬레이션과 테스트 데이터 API는 dev 프로필에서만 등록된다. 운영에는 빈 자체가 없어 404다.
                        .requestMatchers("/api/simulation/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/generate-test-data").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/night-audit").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")

                        // 8. 체크인/체크아웃 및 원장(Folio) 수납/청구 거래 분개
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/check-in").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/check-out").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/folio/transactions").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")

                        // 9. 예약 조회
                        .requestMatchers(HttpMethod.GET, "/api/reservations", "/api/reservations/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")

                        // 회계 및 정산 원장 API 인가
                        .requestMatchers(HttpMethod.GET, "/api/accounting/charge-codes").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")
                        .requestMatchers("/api/accounting/charge-codes/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/accounting/city-ledger").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")

                        // 10. 그 외 모든 요청은 항상 마지막에 선언
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, staffRepository), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}