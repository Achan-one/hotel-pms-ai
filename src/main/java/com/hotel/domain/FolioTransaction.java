package com.hotel.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.UUID;

public class FolioTransaction {
    private String transactionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    private TransactionType type; // CHARGE(+), PAYMENT(-)
    private String category;      // ROOM_CHARGE, MINIBAR, CASH, CREDIT_CARD 등
    private String description;   // 메모
    private long amount;          // 금액

    // 🚀 Jackson 역직렬화에 필수적인 기본 생성자
    protected FolioTransaction() {}

    public FolioTransaction(TransactionType type, String category, String description, long amount) {
        this.transactionId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.timestamp = LocalDateTime.now();
        this.type = type;
        this.category = category;
        this.description = description;
        this.amount = amount;
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