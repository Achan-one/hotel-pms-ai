package com.hotel.domain;

public class BreakfastOption {
    private boolean included;           // 플랜 포함 여부
    private int dailyBreakfastCount;    // 1일당 조식 이용 인원수
    private boolean ticketsIssued;      // 프론트 체크인 시 식권 교부 완료 여부

    public BreakfastOption(boolean included, int dailyBreakfastCount) {
        this.included = included;
        this.dailyBreakfastCount = Math.max(0, dailyBreakfastCount);
        this.ticketsIssued = false;
    }

    public static BreakfastOption none() {
        return new BreakfastOption(false, 0);
    }

    public static BreakfastOption included(int dailyCount) {
        return new BreakfastOption(true, dailyCount);
    }

    // 현장 유료 조식 추가
    public void addBreakfastAtFront(int guestCount) {
        this.included = true;
        this.dailyBreakfastCount = guestCount;
        this.ticketsIssued = false;
    }

    // 식권 발급 처리 (체크인 시점)
    public void issueTickets() {
        if (!included || dailyBreakfastCount == 0) {
            throw new IllegalStateException("조식 옵션이 포함되지 않은 예약은 식권을 발급할 수 없습니다.");
        }
        this.ticketsIssued = true;
    }

    // 총 체류 기간 동안 필요한 식권 총합 계산
    public int calculateTotalTickets(int stayNights) {
        return included ? (dailyBreakfastCount * stayNights) : 0;
    }

    public boolean isIncluded() { return included; }
    public int getDailyBreakfastCount() { return dailyBreakfastCount; }
    public boolean isTicketsIssued() { return ticketsIssued; }
}