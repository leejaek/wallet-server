package com.walletserver.wallet.e2e;

import com.walletserver.transaction.dto.WithdrawalRequest;
import com.walletserver.transaction.dto.WithdrawalResponse;
import com.walletserver.wallet.entity.Wallet;
import com.walletserver.wallet.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
class WalletE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    @DisplayName("E2E: 100개 스레드 동시 출금 API 요청 테스트")
    void withdraw_concurrency_e2e_test() throws InterruptedException {
        // given
        BigDecimal initialBalance = BigDecimal.valueOf(20000000); // 2000만원
        BigDecimal withdrawAmount = BigDecimal.valueOf(10000); // 1만원
        int threadCount = 2000;

        Wallet wallet = walletRepository.save(Wallet.builder()
                .balance(initialBalance)
                .build());

        String url = "http://localhost:" + port + "/api/wallets/" + wallet.getId() + "/withdraw";
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
        Wallet updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();

        log.info("=== E2E Test Finished ===");
        log.info("Success count: {}", successCount.get());
        log.info("Fail count: {}", failCount.get());
        log.info("Final Balance: {}", updatedWallet.getBalance());

        // 검증: 성공 횟수는 100회여야 함
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);

        // 검증: 최종 잔액은 0원이어야 함 (100만원 - 100 * 1만원)
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
