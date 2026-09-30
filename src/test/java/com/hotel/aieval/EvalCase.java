package com.hotel.aieval;

import java.util.Set;

/**
 * 정답이 붙은 평가 사례 하나.
 *
 * @param preferred         정답 선호 태그
 * @param avoid             정답 기피 태그
 * @param optionalPreferred 정답은 아니지만 맞다고 볼 수 있는 선호 태그. 예측해도 감점하지 않는다.
 * @param optionalAvoid     정답은 아니지만 맞다고 볼 수 있는 기피 태그. 예측해도 감점하지 않는다.
 */
public record EvalCase(String id, String lang, String kind, String note,
                       Set<String> preferred, Set<String> avoid,
                       Set<String> optionalPreferred, Set<String> optionalAvoid) {

    public boolean expectsNothing() {
        return preferred.isEmpty() && avoid.isEmpty();
    }
}
