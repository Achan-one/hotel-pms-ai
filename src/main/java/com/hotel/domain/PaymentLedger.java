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

    // 🚀 DB 엔티티로부터 저장된 총액 및 세부 거래 내역을 복원하기 위한 전용 생성자
    public PaymentLedger(PaymentType paymentType, long totalCharges, long totalPayments) {
        this.paymentType = paymentType != null ? paymentType : PaymentType.PAY_ON_ARRIVAL;
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

    // 🚀 이용 명세 등록 (+): 오등록 취소/조정을 위해 음수/양수 모두 기록 허용
    public void addCharge(String category, String description, long amount) {
        this.transactions.add(new FolioTransaction(
                FolioTransaction.TransactionType.CHARGE,
                category != null ? category : "EXTRA_CHARGE",
                description != null ? description : "추가 이용 요금",
                amount
        ));
    }

    public void addCharge(long amount) {
        addCharge("EXTRA_CHARGE", "추가 부대시설/서비스 이용료", amount);
    }

    public void addIncidental(long amount) {
        addCharge("INCIDENTAL", "부대시설 이용료", amount);
    }

    // 🚀 수납 등록 (-): 카드/현금/환불/정정 수납 기록 허용
    public void recordPayment(String paymentMethod, String memo, long amount) {
        this.transactions.add(new FolioTransaction(
                FolioTransaction.TransactionType.PAYMENT,
                paymentMethod != null ? paymentMethod : "CASH",
                memo != null ? memo : "수납 등록",
                amount
        ));
    }

    public void recordPayment(long amount) {
        recordPayment("CASH", "프론트 현금 수납", amount);
    }

    /**
     * 사유 선택 시 청구(+)와 수납(-)을 1쌍으로 동시 분개하여 ±0 상쇄 처리
     */
    public void recordInstantSettlement(String chargeCategory, String chargeDesc, String paymentMethod, String paymentMemo, long amount) {
        if (amount != 0) {
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