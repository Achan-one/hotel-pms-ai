package com.hotel.repository.jpa;

import com.hotel.domain.StaffRole;
import com.hotel.entity.StaffAccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataStaffRepository extends JpaRepository<StaffAccountEntity, String> {

    // 활성 관리자 행을 잠근다. 잠금이 풀린 뒤에는 최신 커밋 상태로 다시 읽으므로 동시에 서로를 끄는 요청은 직렬화된다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StaffAccountEntity s where s.role = :role and s.enabled = true")
    List<StaffAccountEntity> findEnabledByRoleForUpdate(@Param("role") StaffRole role);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StaffAccountEntity s set s.enabled = :enabled where s.staffId = :staffId")
    int updateEnabled(@Param("staffId") String staffId, @Param("enabled") boolean enabled);
}
