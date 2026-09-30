package com.hotel.service;

import java.util.regex.Pattern;

/**
 * 요구사항 메모에 투숙객이 직접 적은 연락처가 외부 AI 서비스로 나가지 않도록 가린다.
 * 이름처럼 문장 속에 섞인 개인정보는 정규식으로 안전하게 걸러낼 수 없다. 그래서 요청에는 이름 필드를 아예 싣지 않는 것이 기본이고,
 * 이 마스킹은 이메일과 전화번호 같은 명백한 패턴만 추가로 막는 보조 장치다.
 */
public final class PiiMasker {

    static final String MASK = "[가림]";

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    // 하이픈, 공백, 괄호, 점으로 이어 쓴 7자리 이상의 숫자열(국내외 전화번호). 국가번호 앞의 +도 함께 가린다.
    private static final Pattern PHONE = Pattern.compile("\\+?\\(?\\d[\\d\\s().-]{5,}\\d");

    private PiiMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String masked = EMAIL.matcher(text).replaceAll(MASK);
        masked = PHONE.matcher(masked).replaceAll(matchResult -> {
            // 숫자가 7개 미만이면 전화번호가 아니라 "10층", "2박 3일" 같은 일반 표현이다. 그대로 둔다.
            long digits = matchResult.group().chars().filter(Character::isDigit).count();
            return digits >= 7 ? MASK : java.util.regex.Matcher.quoteReplacement(matchResult.group());
        });
        return masked;
    }
}
