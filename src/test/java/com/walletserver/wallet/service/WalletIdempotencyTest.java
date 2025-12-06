package com.walletserver.wallet.service;

import com.walletserver.transaction.dto.WithdrawalRequest;
import com.walletserver.transaction.dto.WithdrawalResponse;
import com.walletserver.wallet.entity.Wallet;
import com.walletserver.wallet.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WalletIdempotencyTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    @DisplayName("멱등성 테스트: 동일한 트랜잭션 ID로 중복 요청 시 잔액 차감 없이 기존 성공 응답 반환")
    void testWithdrawIdempotency() {
        // given
        BigDecimal initialBalance = BigDecimal.valueOf(10000);
        BigDecimal withdrawAmount = BigDecimal.valueOf(1000);
        Wallet wallet = walletRepository.save(Wallet.builder()
                .balance(initialBalance)
                .build());

        UUID transactionId = UUID.randomUUID();
        WithdrawalRequest request = new WithdrawalRequest(transactionId, withdrawAmount);

        // when: 첫 번째 요청
        WithdrawalResponse response1 = walletService.withdraw(wallet.getId(), request, false);

        // then: 첫 번째 요청 성공 및 잔액 차감 확인
        assertThat(response1.remainingBalance()).isEqualByComparingTo(BigDecimal.valueOf(9000));
        Wallet walletAfterFirst = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(walletAfterFirst.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(9000));

        // when: 두 번째 중복 요청
        WithdrawalResponse response2 = walletService.withdraw(wallet.getId(), request, false);

        // then: 두 번째 요청도 성공 응답, 하지만 잔액은 그대로여야 함
        assertThat(response2.remainingBalance()).isEqualByComparingTo(BigDecimal.valueOf(9000));
        Wallet walletAfterSecond = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(walletAfterSecond.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(9000));

        // 응답 객체도 동일한 내용이어야 함 (ID 등은 다를 수 있지만 핵심 데이터는 동일)
        assertThat(response2.transactionId()).isEqualTo(response1.transactionId());
        assertThat(response2.amount()).isEqualByComparingTo(response1.amount());
    }
}
