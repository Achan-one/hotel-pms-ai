package com.hotel.service;

import java.time.LocalDate;
import java.util.List;

/**
 * 일괄 배정 해제 결과.
 *
 * @param checkInDate            해제 대상 체크인 일자
 * @param releasedReservationIds 배정이 해제되어 미배정으로 돌아간 예약 ID
 * @param keptInHouseCount       이미 체크인했거나 퇴실해서 그대로 둔 예약 수
 */
public record BatchUnassignResult(LocalDate checkInDate, List<String> releasedReservationIds, int keptInHouseCount) {

    public int releasedCount() {
        return releasedReservationIds.size();
    }
}
