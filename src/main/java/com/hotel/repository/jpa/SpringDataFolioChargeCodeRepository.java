package com.hotel.repository.jpa;

import com.hotel.entity.FolioChargeCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataFolioChargeCodeRepository extends JpaRepository<FolioChargeCodeEntity, String> {
}