package com.hotel.domain;

public class PaymentLedger {
    private final PaymentType paymentType;
    private long totalCharges;  // (+) 발생 비용 총액 (숙박료 + 부대비용)
    private long totalPayments; // (-) 고객 수납 총액 (사전결제 + 현장결제)

    public PaymentLedger(PaymentType paymentType, long roomRateTotal) {
        this.paymentType = paymentType;
        this.totalCharges = Math.max(0, roomRateTotal);
        this.totalPayments = 0;

        // 사전 결제(PREPAID)인 경우 이미 숙박료만큼 수납 완료 처리
        if (paymentType == PaymentType.PREPAID) {
            this.totalPayments = this.totalCharges;
        }
    }

    /**
     * 추가 비용 청구 (+) : 미니바, 조식 현장 추가 등
     */
    public void addCharge(long amount) {
        if (amount > 0) {
            this.totalCharges += amount;
        }
    }

    // 기존 addIncidental 호출 호환용
    public void addIncidental(long amount) {
        addCharge(amount);
    }

    /**
     * 고객 수납 처리 (-) : 카드 승인, 현금 지불
     */
    public void recordPayment(long amount) {
        if (amount > 0) {
            this.totalPayments += amount;
        }
    }

    /**
     * 남은 잔액 전액 수납 (settle)
     */
    public void settle() {
        long balance = getBalance();
        if (balance > 0) {
            recordPayment(balance);
        }
    }

    /**
     * 현재 원장 잔액 (Balance)
     * 0: 전액 정산 완료
     * 양수(+): 고객이 미납한 금액 (Unpaid)
     * 음수(-): 과납/보증금 등 환불해야 할 금액 (Refund Due)
     */
    public long getBalance() {
        return totalCharges - totalPayments;
    }

    /**
     * 체크아웃 가능 여부: 남은 미납금이나 미환불금이 전혀 없는 0원 상태
     */
    public boolean isSettled() {
        return getBalance() == 0;
    }

    public long getTotalDue() {
        return Math.max(0, getBalance());
    }

    public PaymentType getPaymentType() { return paymentType; }
    public long getTotalCharges() { return totalCharges; }
    public long getTotalPayments() { return totalPayments; }

    public enum PaymentType {
        PREPAID("사전 카드 결제"),
        PAY_ON_ARRIVAL("현장 프론트 결제");

        private final String desc;
        PaymentType(String desc) { this.desc = desc; }
        public String getDesc() { return desc; }
    }
}