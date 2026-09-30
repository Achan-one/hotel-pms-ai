package com.hotel.repository.jpa;

import com.hotel.entity.LoginAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface SpringDataLoginAttemptRepository extends JpaRepository<LoginAttemptEntity, LoginAttemptEntity.Key> {

    // 행이 없으면 만들고 있으면 1 늘린다. 동시에 여러 번 틀려도 카운트가 유실되지 않는다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO login_attempts (staff_id, client_ip, failed_attempts) VALUES (:staffId, :clientIp, 1) "
            + "ON DUPLICATE KEY UPDATE failed_attempts = failed_attempts + 1", nativeQuery = true)
    int incrementFailures(@Param("staffId") String staffId, @Param("clientIp") String clientIp);

    // 한도에 도달했으면 잠금 시각을 정하고 카운트를 0으로 되돌린다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LoginAttemptEntity a set a.lockedUntil = :until, a.failedAttempts = 0 "
            + "where a.staffId = :staffId and a.clientIp = :clientIp and a.failedAttempts >= :maxAttempts")
    int lockIfExceeded(@Param("staffId") String staffId,
                       @Param("clientIp") String clientIp,
                       @Param("maxAttempts") int maxAttempts,
                       @Param("until") LocalDateTime until);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LoginAttemptEntity a where a.staffId = :staffId and a.clientIp = :clientIp")
    int deleteByStaffIdAndClientIp(@Param("staffId") String staffId, @Param("clientIp") String clientIp);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LoginAttemptEntity a where a.staffId = :staffId")
    int deleteByStaffId(@Param("staffId") String staffId);
}
