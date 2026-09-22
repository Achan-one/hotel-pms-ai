package com.hotel.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        // 프론트엔드 로컬 개발 서버 Origin 허용
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
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
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        // 1. 공용 엔드포인트 (로그인 등)
                        .requestMatchers("/api/auth/**").permitAll()

                        // 2. 룸 인디케이터 / 룸 랙: 현장 근무 직원(ADMIN, STAFF, PART_TIME) 조회 허용
                        .requestMatchers(HttpMethod.GET, "/api/rooms/indicator").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")

                        // 3. 관리자/사원 전용 (배치 일괄 배정, 룸 체인지)
                        .requestMatchers(HttpMethod.POST, "/api/reservations/batch-assign").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/room-change").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")

                        // 4. OTA/린칸 채널 매니저 시뮬레이터 및 데이터 세팅 (ADMIN, STAFF)
                        .requestMatchers("/api/simulation/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF")

                        // 5. 현장 근무자 공통 (체크인, 체크아웃)
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/check-in").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/check-out").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF", "ROLE_PART_TIME")

                        // 6. 예약 조회 (목록 조회 및 단건 조회 모두 허용: 게스트 포함 인증된 사용자)
                        .requestMatchers(HttpMethod.GET, "/api/reservations/**").authenticated()

                        // 7. 그 외 모든 요청 인증 필요
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}