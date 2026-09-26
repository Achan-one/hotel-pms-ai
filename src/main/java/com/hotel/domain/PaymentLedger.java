package com.hotel.domain;

public class PaymentLedger {

    private final PaymentType paymentType;
    private long totalCharges;   // 발생한 객실료 및 부대비용 (+)
    private long totalPayments;  // 고객으로부터 실제로 수납한 금액 (-)

    public PaymentLedger(PaymentType paymentType, long roomRateTotal) {
        this.paymentType = paymentType;
        this.totalCharges = Math.max(0L, roomRateTotal);
        this.totalPayments = 0L;

        // 사전 결제(PREPAID)인 경우 이미 숙박료만큼 수납 완료 처리
        if (paymentType == PaymentType.PREPAID) {
            this.totalPayments = this.totalCharges;
        }
    }

    public void postRoomCharge(long dailyRate) {
        // 일일 요금 집계용
    }

    public void addCharge(long amount) {
        if (amount > 0) {
            this.totalCharges += amount;
        }
    }

    public void addIncidental(long amount) {
        addCharge(amount);
    }

    public void recordPayment(long amount) {
        if (amount > 0) {
            this.totalPayments += amount;
        }
    }

    public void settle() {
        long balance = getBalance();
        if (balance > 0) {
            recordPayment(balance);
        }
    }

    public long getBalance() {
        return totalCharges - totalPayments;
    }

    public boolean isSettled() {
        return getBalance() == 0L;
    }

    public long getTotalDue() {
        return Math.max(0L, getBalance());
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