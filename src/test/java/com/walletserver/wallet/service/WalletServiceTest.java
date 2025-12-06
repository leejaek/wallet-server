package com.walletserver.wallet.service;

import com.walletserver.transaction.dto.WithdrawalRequest;
import com.walletserver.transaction.dto.WithdrawalResponse;
import com.walletserver.transaction.entity.TransactionHistory;
import com.walletserver.transaction.repository.TransactionHistoryRepository;
import com.walletserver.wallet.entity.Wallet;
import com.walletserver.wallet.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @InjectMocks
    private WalletService walletService;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionHistoryRepository historyRepository;

    @Test
    @DisplayName("출금 성공 테스트")
    void withdraw_success() {
        // given
        Long walletId = 1L;
        UUID transactionId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(1000);
        BigDecimal initialBalance = BigDecimal.valueOf(5000);
        WithdrawalRequest request = new WithdrawalRequest(transactionId, amount);

        Wallet wallet = Wallet.builder()
                .id(walletId)
                .balance(initialBalance)
                .build();

        given(historyRepository.findByTransactionId(transactionId)).willReturn(Optional.empty());
        given(walletRepository.findById(walletId)).willReturn(Optional.of(wallet));

        // when
        WithdrawalResponse response = walletService.withdraw(walletId, request, false);

        // then
        assertThat(response.amount()).isEqualTo(amount);
        assertThat(response.remainingBalance()).isEqualTo(initialBalance.subtract(amount));
        verify(historyRepository).save(any(TransactionHistory.class));
    }

    @Test
    @DisplayName("중복 트랜잭션 시 기존 성공 응답 반환 테스트")
    void withdraw_duplicate_transaction_returns_existing() {
        // given
        Long walletId = 1L;
        UUID transactionId = UUID.randomUUID();
        WithdrawalRequest request = new WithdrawalRequest(transactionId, BigDecimal.valueOf(1000));
        TransactionHistory existingHistory = TransactionHistory.builder()
                .transactionId(transactionId)
                .walletId(walletId)
                .amount(BigDecimal.valueOf(1000))
                .balanceSnapshot(BigDecimal.valueOf(4000))
                .status(TransactionHistory.TransactionStatus.SUCCESS)
                .build();

        given(historyRepository.findByTransactionId(transactionId)).willReturn(Optional.of(existingHistory));

        // when
        WithdrawalResponse response = walletService.withdraw(walletId, request, false);

        // then
        assertThat(response.transactionId()).isEqualTo(transactionId);
        assertThat(response.amount()).isEqualTo(BigDecimal.valueOf(1000));
        assertThat(response.remainingBalance()).isEqualTo(BigDecimal.valueOf(4000));
    }

    @Test
    @DisplayName("잔액 부족 예외 테스트")
    void withdraw_insufficient_balance() {
        // given
        Long walletId = 1L;
        UUID transactionId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(10000);
        BigDecimal initialBalance = BigDecimal.valueOf(5000);
        WithdrawalRequest request = new WithdrawalRequest(transactionId, amount);

        Wallet wallet = Wallet.builder()
                .id(walletId)
                .balance(initialBalance)
                .build();

        given(historyRepository.findByTransactionId(transactionId)).willReturn(Optional.empty());
        given(walletRepository.findById(walletId)).willReturn(Optional.of(wallet));

        // when & then
        assertThatThrownBy(() -> walletService.withdraw(walletId, request, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient balance");
    }
}
