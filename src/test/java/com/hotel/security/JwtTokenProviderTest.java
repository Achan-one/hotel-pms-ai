package com.hotel.security;

import com.hotel.domain.StaffRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-key-0123456789abcdef-xyz";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000L);

    @Test
    @DisplayName("[JWT] 발급한 토큰은 유효하고 사용자와 역할을 그대로 돌려준다")
    void issuedTokenRoundTrips() {
        String token = provider.generateToken("tanaka", StaffRole.ROLE_STAFF);

        assertTrue(provider.validateToken(token));
        assertEquals("tanaka", provider.getUsername(token));
        assertEquals(StaffRole.ROLE_STAFF, provider.getRole(token));
    }

    @Test
    @DisplayName("[JWT] 만료된 토큰은 거부한다")
    void expiredTokenIsRejected() {
        JwtTokenProvider alreadyExpired = new JwtTokenProvider(SECRET, -1_000L);

        assertFalse(provider.validateToken(alreadyExpired.generateToken("tanaka", StaffRole.ROLE_STAFF)));
    }

    @Test
    @DisplayName("[JWT] 서명이 변조된 토큰은 거부한다")
    void tamperedTokenIsRejected() {
        String token = provider.generateToken("tanaka", StaffRole.ROLE_STAFF);
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertFalse(provider.validateToken(tampered));
    }

    @Test
    @DisplayName("[JWT] 다른 비밀키로 서명한 토큰은 거부한다")
    void tokenSignedWithOtherKeyIsRejected() {
        String forged = Jwts.builder()
                .subject("tanaka")
                .claim("role", "ROLE_ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor("another-secret-key-0123456789abcdef-abc".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(provider.validateToken(forged));
    }

    @Test
    @DisplayName("[JWT] 서명 없는(alg=none) 토큰은 거부한다")
    void unsignedTokenIsRejected() {
        String unsigned = Jwts.builder().subject("tanaka").claim("role", "ROLE_ADMIN").compact();

        assertFalse(provider.validateToken(unsigned));
    }

    @Test
    @DisplayName("[JWT] 비밀키가 32바이트보다 짧으면 기동 단계에서 막는다")
    void shortSecretIsRejectedAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("too-short", 60_000L));
    }

    @Test
    @DisplayName("[JWT] 알 수 없는 역할 클레임은 역할 변환에서 예외가 난다")
    void unknownRoleClaimFailsToParse() {
        String token = Jwts.builder()
                .subject("tanaka")
                .claim("role", "ROLE_SUPERUSER")
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertTrue(provider.validateToken(token));
        assertThrows(IllegalArgumentException.class, () -> provider.getRole(token));
    }
}
