package com.walletserver.wallet.e2e;

import com.walletserver.global.config.TestContainersConfig;
import com.walletserver.transaction.dto.WithdrawalRequest;
import com.walletserver.transaction.dto.WithdrawalResponse;
import com.walletserver.transaction.repository.TransactionHistoryRepository;
import com.walletserver.wallet.entity.Wallet;
import com.walletserver.wallet.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainersConfig.class)
class WalletE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

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
                .balance(BigDecimal.valueOf(1000000)) // 1,000,000 initial
                .build();
        walletId = walletRepository.save(wallet).getId();
    }

    @Test
    @DisplayName("동시성 테스트: 100명 동시 출금")
    void concurrent_withdraw_success_100_threads() throws InterruptedException {
        // given
        BigDecimal initialBalance = BigDecimal.valueOf(1000000); // 1,000,000 initial
        BigDecimal withdrawAmount = BigDecimal.valueOf(10000); // 10,000 * 100 = 1,000,000
        int threadCount = 100;

        String url = "http://localhost:" + port + "/api/wallets/" + walletId + "/withdraw";
        log.info("Target URL: {}", url);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        long startTime = System.currentTimeMillis();

        // when
        for (int i = 0; i < threadCount; i++) {
            int threadNum = i + 1;
            executorService.submit(() -> {
                try {
                    WithdrawalRequest requestPayload = new WithdrawalRequest(UUID.randomUUID(), withdrawAmount);
                    HttpEntity<WithdrawalRequest> requestEntity = new HttpEntity<>(requestPayload, headers);

                    log.info("[Thread-{}] Sending withdrawal request. TransactionId: {}", threadNum,
                            requestPayload.transactionId());

                    ResponseEntity<WithdrawalResponse> response = restTemplate.postForEntity(url, requestEntity,
                            WithdrawalResponse.class);

                    if (response.getStatusCode().is2xxSuccessful()) {
                        successCount.incrementAndGet();
                        log.info("[Thread-{}] Request success. Status: {}", threadNum, response.getStatusCode());
                    } else {
                        failCount.incrementAndGet();
                        log.error("[Thread-{}] Request failed. Status: {}", threadNum, response.getStatusCode());
                    }

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("[Thread-{}] Request exception: {}", threadNum, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        log.info("Total E2E execution time: {} ms", duration);

        // then
        Wallet updatedWallet = walletRepository.findById(walletId).orElseThrow();

        log.info("=== E2E Test Finished ===");
        log.info("Success count: {}", successCount.get());
        log.info("Fail count: {}", failCount.get());
        log.info("Final Balance: {}", updatedWallet.getBalance());

        // 검증: 성공 횟수는 100회여야 함
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);

        // 검증: 최종 잔액은 0원이어야 함 (모두 성공했으므로)
        // 100 threads * 10,000 = 1,000,000. Balance was 1,000,000.
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
