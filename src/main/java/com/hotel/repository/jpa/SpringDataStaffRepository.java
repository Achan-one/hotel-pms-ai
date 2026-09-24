package com.hotel.repository.jpa;

import com.hotel.entity.StaffAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataStaffRepository extends JpaRepository<StaffAccountEntity, String> {
}