package com.hotel.service.report;

import com.hotel.service.dto.ReservationSearchCondition;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 대용량 데이터 덤프(전체 테이블 스캔) 방어 및 리포트 조회 제약 정책.
 */
public final class ReportPolicy {

    /**
     * 단일 CSV 익스포트 시 허용되는 최대 조회 기간 (일 단위)
     */
    public static final int MAX_EXPORT_RANGE_DAYS = 31;

    /**
     * 단일 CSV 익스포트 시 허용되는 최대 행 수 (안전 상한치)
     */
    public static final int MAX_ROW_LIMIT = 5_000;

    private ReportPolicy() {
        // 인스턴스화 방지
    }

    /**
     * CSV 예약 리포트 출력 조건의 유효성을 검증합니다.
     * 전체 덤프를 방지하기 위해 최소 1개 이상의 유효한 필터 조건이 필요하며,
     * 날짜 범위가 지정된 경우 최대 31일을 초과할 수 없습니다.
     *
     * @param condition 검색 조건
     * @throws IllegalArgumentException 조건이 누락되었거나 범위를 초과한 경우
     */
    public static void validateExportCondition(ReservationSearchCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException(
                    "전체 예약 무제한 다운로드는 허용되지 않습니다. 검색 조건을 입력하세요."
            );
        }

        boolean hasId = condition.reservationId() != null && !condition.reservationId().isBlank();
        boolean hasGuestName = condition.guestName() != null && !condition.guestName().isBlank();
        boolean hasCheckInDate = condition.checkInDate() != null;
        boolean hasRoomType = condition.roomType() != null;
        boolean hasStatus = condition.status() != null;
        boolean hasAssignedRoom = condition.assignedRoomNumber() != null && !condition.assignedRoomNumber().isBlank();

        // 1. 최소한의 필터 조건 존재 여부 검증 (Full Dump 차단)
        if (!hasId && !hasGuestName && !hasCheckInDate && !hasRoomType && !hasStatus && !hasAssignedRoom) {
            throw new IllegalArgumentException(
                    "시스템 보호를 위해 전체 예약 덤프가 차단되었습니다. 체크인 날짜, 상태, 고객명 중 최소 1개 이상의 조건을 지정하세요."
            );
        }

        // 2. 체크인 날짜 + 박수 기반의 범위 제한 검증
        if (hasCheckInDate && condition.stayNights() != null) {
            if (condition.stayNights() > MAX_EXPORT_RANGE_DAYS) {
                throw new IllegalArgumentException(String.format(
                        "CSV 조회 허용 기간은 최대 %d일입니다. (요청: %d일)",
                        MAX_EXPORT_RANGE_DAYS, condition.stayNights()
                ));
            }
        }
    }

    /**
     * 시작일과 종료일 간의 날짜 범위 유효성을 검증합니다.
     */
    public static void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("조회 시작일과 종료일은 필수입니다.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(String.format(
                    "시작일(%s)이 종료일(%s)보다 이후일 수 없습니다.", from, to
            ));
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_EXPORT_RANGE_DAYS) {
            throw new IllegalArgumentException(String.format(
                    "조회 기간은 최대 %d일까지만 가능합니다. (요청: %d일)", MAX_EXPORT_RANGE_DAYS, days
            ));
        }
    }
}