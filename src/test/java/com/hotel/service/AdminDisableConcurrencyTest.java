package com.hotel.service;

import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;
import com.hotel.repository.StaffRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AdminDisableConcurrencyTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private StaffRepository staffRepository;

    private boolean disable(String actor, String target) {
        try {
            authService.setStaffEnabled(actor, target, false);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @RepeatedTest(5)
    @DisplayName("[계정 관리] 관리자 둘이 동시에 서로를 비활성화해도 활성 관리자는 한 명 이상 남는다")
    void twoAdminsDisablingEachOtherLeaveOneActive() throws Exception {
        staffRepository.save(new StaffAccount("race-admin-a", "hash", "관리자A", StaffRole.ROLE_ADMIN));
        staffRepository.save(new StaffAccount("race-admin-b", "hash", "관리자B", StaffRole.ROLE_ADMIN));
        // 다른 테스트가 만든 관리자가 있으면 이 시나리오가 성립하지 않으므로 이 둘만 활성으로 남긴다.
        staffRepository.updateEnabled("race-admin-a", true);
        staffRepository.updateEnabled("race-admin-b", true);
        disableEveryOtherAdmin();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> aDisablesB = pool.submit(waiting(start, () -> disable("race-admin-a", "race-admin-b")));
            Future<Boolean> bDisablesA = pool.submit(waiting(start, () -> disable("race-admin-b", "race-admin-a")));
            start.countDown();

            boolean first = aDisablesB.get(20, TimeUnit.SECONDS);
            boolean second = bDisablesA.get(20, TimeUnit.SECONDS);

            assertTrue(first ^ second, "둘 중 정확히 하나만 성공해야 한다");
            assertEquals(1L, activeAdminCount(), "활성 관리자가 정확히 한 명 남아야 한다");
        } finally {
            pool.shutdownNow();
            staffRepository.updateEnabled("race-admin-a", true);
            staffRepository.updateEnabled("race-admin-b", true);
        }
    }

    private void disableEveryOtherAdmin() {
        for (String id : new String[]{"admin", "part_time", "staff", "guard-admin", "matrix-admin", "probe-admin"}) {
            staffRepository.findByStaffId(id)
                    .filter(a -> a.role() == StaffRole.ROLE_ADMIN)
                    .ifPresent(a -> staffRepository.updateEnabled(a.staffId(), false));
        }
    }

    private long activeAdminCount() {
        return staffRepository.lockAndCountEnabledByRole(StaffRole.ROLE_ADMIN);
    }

    private static Callable<Boolean> waiting(CountDownLatch start, Callable<Boolean> body) {
        return () -> {
            start.await();
            return body.call();
        };
    }
}
