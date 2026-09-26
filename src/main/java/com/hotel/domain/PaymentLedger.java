package com.hotel.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PaymentLedger {

    private final PaymentType paymentType;
    private final List<FolioTransaction> transactions = new ArrayList<>();

    public PaymentLedger(PaymentType paymentType, long roomRateTotal) {
        this.paymentType = paymentType;
        long initialCharge = Math.max(0L, roomRateTotal);

        if (initialCharge > 0) {
            this.transactions.add(new FolioTransaction(
                    FolioTransaction.TransactionType.CHARGE,
                    "ROOM_RATE",
                    "기본 객실 예약 요금 청구",
                    initialCharge
            ));
        }

        if (paymentType == PaymentType.PREPAID && initialCharge > 0) {
            this.transactions.add(new FolioTransaction(
                    FolioTransaction.TransactionType.PAYMENT,
                    "CREDIT_CARD",
                    "OTA 사전 카드 승인 결제 완료",
                    initialCharge
            ));
        }
    }

    public void postRoomCharge(long dailyRate) {
        if (dailyRate > 0) {
            this.transactions.add(new FolioTransaction(
                    FolioTransaction.TransactionType.CHARGE,
                    "DAILY_ROOM_CHARGE",
                    "나이트 오딧 일일 객실료 청구",
                    dailyRate
            ));
        }
    }

    public void addCharge(String category, String description, long amount) {
        if (amount > 0) {
            this.transactions.add(new FolioTransaction(
                    FolioTransaction.TransactionType.CHARGE,
                    category,
                    description,
                    amount
            ));
        }
    }

    public void addCharge(long amount) {
        addCharge("EXTRA_CHARGE", "추가 부대시설/서비스 이용료", amount);
    }

    public void addIncidental(long amount) {
        addCharge("INCIDENTAL", "부대시설 이용료", amount);
    }

    public void recordPayment(String paymentMethod, String memo, long amount) {
        if (amount > 0) {
            this.transactions.add(new FolioTransaction(
                    FolioTransaction.TransactionType.PAYMENT,
                    paymentMethod,
                    memo,
                    amount
            ));
        }
    }

    public void recordPayment(long amount) {
        recordPayment("CASH", "프론트 현금 수납", amount);
    }

    /**
     * 사유 선택 시 청구(+)와 수납(-)을 1쌍으로 동시 분개하여 ±0 상쇄 처리
     */
    public void recordInstantSettlement(String chargeCategory, String chargeDesc, String paymentMethod, String paymentMemo, long amount) {
        if (amount > 0) {
            this.transactions.add(new FolioTransaction(
                    FolioTransaction.TransactionType.CHARGE,
                    chargeCategory,
                    chargeDesc,
                    amount
            ));
            this.transactions.add(new FolioTransaction(
                    FolioTransaction.TransactionType.PAYMENT,
                    paymentMethod,
                    paymentMemo,
                    amount
            ));
        }
    }

    public void settle() {
        long due = getTotalDue();
        if (due > 0) {
            recordPayment("SETTLEMENT", "체크아웃 전액 정산 수납", due);
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
        if (paymentType == PaymentType.PREPAID) {
            return true;
        }
        return isSettled();
    }

    public long getTotalDue() {
        return Math.max(0L, getBalance());
    }

    public List<FolioTransaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
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