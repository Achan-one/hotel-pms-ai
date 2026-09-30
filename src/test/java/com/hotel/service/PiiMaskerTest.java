package com.hotel.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PiiMaskerTest {

    @Test
    @DisplayName("[마스킹] 이메일과 다양한 형식의 전화번호를 가린다")
    void masksEmailsAndPhones() {
        assertFalse(PiiMasker.mask("a.b+c@sub.example.co.kr").contains("@"));
        assertFalse(PiiMasker.mask("010-1234-5678").contains("5678"));
        assertFalse(PiiMasker.mask("+81 (90) 1234-5678").contains("1234"));
        assertFalse(PiiMasker.mask("03.1234.5678").contains("1234"));
        assertFalse(PiiMasker.mask("연락처 01012345678 로").contains("12345678"));
    }

    @Test
    @DisplayName("[마스킹] 층수, 박수, 날짜 같은 일반 숫자 표현은 그대로 둔다")
    void keepsOrdinaryNumbers() {
        assertEquals("10층 이상 고층 원해요", PiiMasker.mask("10층 이상 고층 원해요"));
        assertEquals("2박 3일 예정, 성인 2명", PiiMasker.mask("2박 3일 예정, 성인 2명"));
        assertEquals("room 1204 or 1205", PiiMasker.mask("room 1204 or 1205"));
    }

    @Test
    @DisplayName("[마스킹] 빈 값은 빈 문자열로 처리한다")
    void handlesNullAndEmpty() {
        assertEquals("", PiiMasker.mask(null));
        assertEquals("", PiiMasker.mask(""));
    }
}
