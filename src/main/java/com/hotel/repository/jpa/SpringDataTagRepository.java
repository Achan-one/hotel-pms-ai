package com.hotel.repository.jpa;

import com.hotel.entity.RoomTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataTagRepository extends JpaRepository<RoomTagEntity, String> {
}