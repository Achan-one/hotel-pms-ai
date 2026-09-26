package com.hotel.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 1. REST API는 CSRF 비활성화하되, H2 콘솔 자체 폼 요청 또한 차단되지 않도록 방어
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**").disable())

                // 2. H2 웹 콘솔의 iframe 프레임 렌더링 허용 (SAMEORIGIN)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))

                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        // 0. CORS Preflight (OPTIONS) 사전 검사는 인증 없이 무조건 허용
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 1. H2 콘솔 및 인증 공개 엔드포인트
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/staff").hasAuthority("ROLE_ADMIN")

                        // 2. 시스템 인프라 및 나이트오딧 사전 점검/롤오버 (프론트 상시 연동을 위해 전체 허용)
                        .requestMatchers("/api/system/**").permitAll()

                        // 3. 동적 태그 관리
                        .requestMatchers("/api/admin/tags/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers("/api/admin/tags").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")

                        // 4. 룸 인디케이터
                        .requestMatchers(HttpMethod.GET, "/api/rooms/indicator").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")

                        // 5. 배정, 룸 체인지, 오버라이드 및 요금 스케줄 갱신
                        .requestMatchers(HttpMethod.POST, "/api/reservations/batch-assign").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/room-change").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/manual-assign").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.DELETE, "/api/reservations/*/assign").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.PATCH, "/api/reservations/*/operational-override").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.PATCH, "/api/reservations/*/operational-tags").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.PUT, "/api/reservations/*/daily-rates").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers("/api/reservations/*/lock").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")

                        // 6. 실무 리포트 CSV 다운로드
                        .requestMatchers("/api/reports/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")

                        // 7. 시뮬레이터 및 나이트 오딧 실행
                        .requestMatchers("/api/simulation/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
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
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}