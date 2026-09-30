package com.hotel.repository.jpa;

import com.hotel.domain.StaffRole;
import com.hotel.entity.StaffAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface SpringDataStaffRepository extends JpaRepository<StaffAccountEntity, String> {

    long countByRoleAndEnabledTrue(StaffRole role);

    // 동시에 여러 번 틀려도 카운트가 유실되지 않도록 DB에서 직접 증가시킨다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StaffAccountEntity s set s.failedAttempts = s.failedAttempts + 1 where s.staffId = :staffId")
    int incrementFailedAttempts(@Param("staffId") String staffId);

    // 한도에 도달했으면 잠그고 카운트를 0으로 되돌린다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StaffAccountEntity s set s.lockedUntil = :until, s.failedAttempts = 0 "
            + "where s.staffId = :staffId and s.failedAttempts >= :maxAttempts")
    int lockIfExceeded(@Param("staffId") String staffId,
                       @Param("maxAttempts") int maxAttempts,
                       @Param("until") LocalDateTime until);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StaffAccountEntity s set s.failedAttempts = 0, s.lockedUntil = null where s.staffId = :staffId")
    int clearLoginFailures(@Param("staffId") String staffId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update StaffAccountEntity s set s.enabled = :enabled, s.failedAttempts = 0, s.lockedUntil = null "
            + "where s.staffId = :staffId")
    int updateEnabled(@Param("staffId") String staffId, @Param("enabled") boolean enabled);
}
