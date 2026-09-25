package com.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hotel_operation_status")
public class HotelOperationStatusEntity {

    @Id
    @Column(name = "property_id", length = 32)
    private String propertyId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "last_audit_at")
    private LocalDateTime lastAuditAt;

    protected HotelOperationStatusEntity() {}

    public HotelOperationStatusEntity(String propertyId, LocalDate businessDate, LocalDateTime lastAuditAt) {
        this.propertyId = propertyId;
        this.businessDate = businessDate;
        this.lastAuditAt = lastAuditAt;
    }

    public static HotelOperationStatusEntity defaultStatus(LocalDate initialDate) {
        return new HotelOperationStatusEntity("DEFAULT", initialDate, LocalDateTime.now());
    }

    public String getPropertyId() { return propertyId; }
    public LocalDate getBusinessDate() { return businessDate; }
    public LocalDateTime getLastAuditAt() { return lastAuditAt; }

    public void rollover(LocalDate newDate) {
        this.businessDate = newDate;
        this.lastAuditAt = LocalDateTime.now();
    }

    public void updateBusinessDate(LocalDate newDate) {
        this.businessDate = newDate;
    }
}