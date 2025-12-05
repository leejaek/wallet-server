package com.walletserver.transaction.dto;

import com.walletserver.transaction.entity.TransactionHistory;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawalResponse(
        UUID transactionId,
        BigDecimal amount,
        BigDecimal remainingBalance,
        String status
) {
    public static WithdrawalResponse from(TransactionHistory history) {
        return new WithdrawalResponse(
                history.getTransactionId(),
                history.getAmount(),
                history.getBalanceSnapshot(),
                history.getStatus().name()
        );
    }
}
