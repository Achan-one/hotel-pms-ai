package com.hotel.repository;

import com.hotel.domain.StaffAccount;

import java.util.Optional;

public interface StaffRepository {
    Optional<StaffAccount> findByStaffId(String staffId);
    void save(StaffAccount account);
}