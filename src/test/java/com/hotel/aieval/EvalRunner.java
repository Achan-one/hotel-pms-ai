package com.hotel.aieval;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.domain.TagPreference;
import com.hotel.service.AiPreferenceParser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 평가 사례를 AI 파서에 넣어 예측을 모은다. 운영과 같은 일괄 호출 경로(parseBatch)를 그대로 쓴다.
 */
public final class EvalRunner {

    /** 운영에서는 하루치 미배정 예약을 한 번에 보내므로, 평가도 한 번에 여러 건을 보낸다. */
    public static final int DEFAULT_BATCH_SIZE = 30;

    private EvalRunner() {
    }

    public static Map<String, TagPreference> predict(AiPreferenceParser parser, List<EvalCase> cases, int batchSize) {
        Map<String, TagPreference> predictions = new HashMap<>();
        for (int from = 0; from < cases.size(); from += batchSize) {
            List<Reservation> batch = new ArrayList<>();
            for (EvalCase c : cases.subList(from, Math.min(from + batchSize, cases.size()))) {
                // 투숙객 이름 같은 개인정보는 애초에 넣지 않는다. 운영과 마찬가지로 AI에는 요구사항만 전달된다.
                batch.add(new Reservation(c.id(), "평가용", RoomType.MODERATE_DOUBLE, LocalDate.of(2026, 9, 20), 1,
                        c.note(), GuestPreference.empty()));
            }
            predictions.putAll(parser.parseBatch(batch));
        }
        return predictions;
    }
}
