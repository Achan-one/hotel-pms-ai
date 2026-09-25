package com.hotel.repository.jpa;

import com.hotel.entity.HotelOperationStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataHotelOperationStatusRepository extends JpaRepository<HotelOperationStatusEntity, String> {
}