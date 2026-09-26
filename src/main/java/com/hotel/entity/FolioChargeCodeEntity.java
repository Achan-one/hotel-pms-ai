package com.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "folio_charge_codes")
public class FolioChargeCodeEntity {

    @Id
    @Column(name = "code", length = 30)
    private String code; // 예: "ROOM_CHARGE", "EXTRA_BED", "MINIBAR"

    @Column(name = "name", nullable = false, length = 50)
    private String name; // 예: "룸 차지", "엑스트라 베드", "미니바"

    @Column(name = "default_amount", nullable = false)
    private long defaultAmount;

    @Column(name = "is_system_default", nullable = false)
    private boolean systemDefault;

    protected FolioChargeCodeEntity() {}

    public FolioChargeCodeEntity(String code, String name, long defaultAmount, boolean systemDefault) {
        this.code = code != null ? code.trim().toUpperCase() : null;
        this.name = name;
        this.defaultAmount = defaultAmount;
        this.systemDefault = systemDefault;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public long getDefaultAmount() { return defaultAmount; }
    public boolean isSystemDefault() { return systemDefault; }
}