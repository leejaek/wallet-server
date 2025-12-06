package com.walletserver.wallet.facade;

import com.walletserver.transaction.dto.WithdrawalRequest;
import com.walletserver.wallet.entity.Wallet;
import com.walletserver.wallet.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
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
@Import(com.walletserver.global.config.TestContainersConfig.class)
class WalletConcurrencyTest {

    @Autowired
    private WalletLockFacade walletLockFacade;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    @DisplayName("100개 스레드 동시 출금 테스트")
    void withdraw_concurrency_test() throws InterruptedException {
        // given
        BigDecimal initialBalance = BigDecimal.valueOf(1000000); // 100만원
        BigDecimal withdrawAmount = BigDecimal.valueOf(10000); // 1만원
        int threadCount = 100;

        Wallet wallet = walletRepository.save(Wallet.builder()
                .balance(initialBalance)
                .build());

        log.info("=== Concurrency Test Started ===");
        log.info("Initial Balance: {}, Thread Count: {}, Withdraw Amount: {}", initialBalance, threadCount,
                withdrawAmount);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            int threadNum = i + 1;
            executorService.submit(() -> {
                try {
                    WithdrawalRequest request = new WithdrawalRequest(UUID.randomUUID(), withdrawAmount);
                    log.info("[Thread-{}] Requesting withdrawal. TransactionId: {}", threadNum,
                            request.transactionId());

                    walletLockFacade.withdraw(wallet.getId(), request);

                    successCount.incrementAndGet();
                    log.info("[Thread-{}] Withdrawal success.", threadNum);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("[Thread-{}] Withdrawal failed: {}", threadNum, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        Wallet updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();

        log.info("=== Concurrency Test Finished ===");
        log.info("Success count: {}", successCount.get());
        log.info("Fail count: {}", failCount.get());
        log.info("Final Balance: {}", updatedWallet.getBalance());

        // 검증: 최종 잔액은 0원 미만이 아니어야 함
        assertThat(updatedWallet.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // 검증: (성공 횟수 * 출금액) + 잔액 == 초기 잔액
        BigDecimal totalWithdrawn = withdrawAmount.multiply(BigDecimal.valueOf(successCount.get()));
        assertThat(updatedWallet.getBalance().add(totalWithdrawn)).isEqualByComparingTo(initialBalance);
    }
}
