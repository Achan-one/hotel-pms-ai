package com.hotel.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public class FolioTransaction {
    private final String transactionId;
    private final LocalDateTime timestamp;
    private final TransactionType type; // CHARGE(+), PAYMENT(-)
    private final String category;      // ROOM_RATE, MINIBAR, CASH, CREDIT_CARD 등
    private final String description;   // 비고 / 메모
    private final long amount;          // 금액 (양수)

    public FolioTransaction(TransactionType type, String category, String description, long amount) {
        this.transactionId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.timestamp = LocalDateTime.now();
        this.type = type;
        this.category = category;
        this.description = description;
        this.amount = Math.max(0, amount);
    }

    public enum TransactionType {
        CHARGE("청구 (+)"),
        PAYMENT("수납 (-)");

        private final String desc;
        TransactionType(String desc) { this.desc = desc; }
        public String getDesc() { return desc; }
    }

    public String getTransactionId() { return transactionId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public TransactionType getType() { return type; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public long getAmount() { return amount; }
}