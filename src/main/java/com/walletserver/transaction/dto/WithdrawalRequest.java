package com.walletserver.transaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawalRequest(
        @NotNull UUID transactionId,
        @NotNull @Positive BigDecimal amount
) {
}
