package com.hotel.domain;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * PMS가 예약마다 직접 발급하는 예약 번호. 형식은 {@code PMS-yymmdd-XXXXXXXX}.
 * 뒤 8자리는 헷갈리는 글자(0, O, 1, I)를 뺀 32개 문자에서 무작위로 뽑는다. 하루에 만들어도 충돌할 일이 사실상 없고,
 * 만에 하나 겹치면 DB의 유니크 제약이 저장을 막는다.
 * 투숙객 이름이나 OTA 예약 ID 같은 개인정보와 무관해서 외부 서비스(AI)에 예약을 가리키는 값으로 안전하게 쓸 수 있다.
 */
public final class PmsReservationNumber {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int RANDOM_LENGTH = 8;
    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private PmsReservationNumber() {
    }

    public static String generate() {
        return generate(LocalDate.now());
    }

    public static String generate(LocalDate issuedOn) {
        StringBuilder sb = new StringBuilder("PMS-").append(issuedOn.format(DATE_PART)).append('-');
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
