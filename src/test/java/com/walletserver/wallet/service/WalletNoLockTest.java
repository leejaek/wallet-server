package com.walletserver.wallet.service;

import com.walletserver.transaction.dto.WithdrawalRequest;
import com.walletserver.global.config.TestContainersConfig;
import com.walletserver.transaction.repository.TransactionHistoryRepository;
import com.walletserver.wallet.entity.Wallet;
import com.walletserver.wallet.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@Import(TestContainersConfig.class)
class WalletNoLockTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionHistoryRepository historyRepository;

    private Long walletId;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        walletRepository.deleteAll();

        Wallet wallet = Wallet.builder()
                .balance(BigDecimal.valueOf(1000000))
                .build();
        walletId = walletRepository.save(wallet).getId();
    }

    @Test
    @DisplayName("락 없이 동시 요청: 경쟁 상태 발생 확인 (100 threads)")
    void execution_without_lock_race_condition() throws InterruptedException {
        // given
        int threadCount = 100;
        BigDecimal amount = BigDecimal.valueOf(10000); // 10,000 * 100 = 1,000,000

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        log.info("=== Start No-Lock Test ===");

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    WithdrawalRequest request = new WithdrawalRequest(UUID.randomUUID(), amount);
                    // useDbLock = false (DB Lock 사용 안 함, 일반 조회)
                    walletService.withdraw(walletId, request, false);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("Withdraw failed: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        Wallet updatedWallet = walletRepository.findById(walletId).orElseThrow();
        log.info("=== No-Lock Test Result ===");
        log.info("Expected Balance: 0");
        log.info("Actual Balance: {}", updatedWallet.getBalance());
        log.info("Success Count: {}", successCount.get());

        // 실패하거나 잔액이 맞지 않아야 이 테스트의 목적(문제 재현) 달성됨
        // 하지만 Junit Test로서는 '실패'를 보여주기 위해 Assertion은 '정상'을 기대하도록 작성하고
        // 실제 실행 시 FAILED가 뜨는 로그를 확보한다.
        assertThat(updatedWallet.getBalance()).isNotEqualByComparingTo(BigDecimal.ZERO);
        log.info("Race Condition Confirmed: Balance IS NOT ZERO ({} != 0)", updatedWallet.getBalance());
    }
}
