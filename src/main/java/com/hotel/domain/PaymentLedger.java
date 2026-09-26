package com.hotel.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PaymentLedger {

    private final PaymentType paymentType;
    private final List<FolioTransaction> transactions = new ArrayList<>();

    // 🚀 사전 청구 강제 생성을 없앰 (체크인 전에는 원장이 0원이어야 정상)
    public PaymentLedger(PaymentType paymentType, long roomRateTotal) {
        this.paymentType = paymentType != null ? paymentType : PaymentType.PAY_ON_ARRIVAL;
    }

    public PaymentLedger(PaymentType paymentType, long totalCharges, long totalPayments) {
        this.paymentType = paymentType != null ? paymentType : PaymentType.PAY_ON_ARRIVAL;
    }

    // 나이트 오딧 시 호출: 1박 객실료를 장부에 청구(+) 포스팅
    public void postRoomCharge(long dailyRate) {
        if (dailyRate > 0) {
            this.transactions.add(new FolioTransaction(
                    FolioTransaction.TransactionType.CHARGE,
                    "ROOM_CHARGE",
                    "나이트 오딧 일일 객실료 청구",
                    dailyRate
            ));
        }
    }

    // 🚀 이용 명세 등록 (+): 부대시설, 미니바, 엑스트라 베드 등 한 줄씩 청구 추가
    public void addCharge(String category, String description, long amount) {
        this.transactions.add(new FolioTransaction(
                FolioTransaction.TransactionType.CHARGE,
                category != null ? category : "EXTRA_CHARGE",
                description != null ? description : "이용 요금 청구",
                amount
        ));
    }

    // 🚀 수납 등록 (-): 카드, 현금 결제 한 줄씩 수납 추가
    public void recordPayment(String paymentMethod, String memo, long amount) {
        this.transactions.add(new FolioTransaction(
                FolioTransaction.TransactionType.PAYMENT,
                paymentMethod != null ? paymentMethod : "CASH",
                memo != null ? memo : "수납 등록",
                amount
        ));
    }

    public void addCharge(long amount) {
        addCharge("EXTRA_CHARGE", "추가 부대시설/서비스 이용료", amount);
    }

    public void recordPayment(long amount) {
        recordPayment("CASH", "프론트 현금 수납", amount);
    }

    public void recordInstantSettlement(String chargeCategory, String chargeDesc, String paymentMethod, String paymentMemo, long amount) {
        if (amount != 0) {
            addCharge(chargeCategory, chargeDesc, amount);
            recordPayment(paymentMethod, paymentMemo, amount);
        }
    }

    public long getTotalCharges() {
        return transactions.stream()
                .filter(t -> t.getType() == FolioTransaction.TransactionType.CHARGE)
                .mapToLong(FolioTransaction::getAmount)
                .sum();
    }

    public long getTotalPayments() {
        return transactions.stream()
                .filter(t -> t.getType() == FolioTransaction.TransactionType.PAYMENT)
                .mapToLong(FolioTransaction::getAmount)
                .sum();
    }

    public long getBalance() {
        return getTotalCharges() - getTotalPayments();
    }

    public boolean isSettled() {
        return getBalance() == 0L;
    }

    public boolean isSettledForCheckOut() {
        return isSettled();
    }

    public long getTotalDue() {
        return Math.max(0L, getBalance());
    }

    public List<FolioTransaction> getTransactions() {
        return transactions;
    }

    public PaymentType getPaymentType() { return paymentType; }

    public enum PaymentType {
        PREPAID("사전 카드 결제"),
        PAY_ON_ARRIVAL("현장 프론트 결제");

        private final String desc;
        PaymentType(String desc) { this.desc = desc; }
        public String getDesc() { return desc; }
    }
}