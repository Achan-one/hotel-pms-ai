package com.hotel.entity;

import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;
import jakarta.persistence.*;

@Entity
@Table(name = "staff_accounts")
public class StaffAccountEntity {

    @Id
    @Column(name = "staff_id", length = 50)
    private String staffId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private StaffRole role;

    protected StaffAccountEntity() {}

    public StaffAccountEntity(String staffId, String passwordHash, String name, StaffRole role) {
        this.staffId = staffId != null ? staffId.trim().toLowerCase() : null;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role != null ? role : StaffRole.ROLE_STAFF;
    }

    public static StaffAccountEntity fromDomain(StaffAccount domain) {
        return new StaffAccountEntity(
                domain.staffId(),
                domain.passwordHash(),
                domain.name(),
                domain.role()
        );
    }

    public StaffAccount toDomain() {
        return new StaffAccount(
                this.staffId,
                this.passwordHash,
                this.name,
                this.role
        );
    }

    public String getStaffId() { return staffId; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public StaffRole getRole() { return role; }
}