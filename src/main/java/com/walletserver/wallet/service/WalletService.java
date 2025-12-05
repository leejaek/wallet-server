package com.walletserver.wallet.service;

import com.walletserver.transaction.dto.WithdrawalRequest;
import com.walletserver.transaction.dto.WithdrawalResponse;
import com.walletserver.transaction.entity.TransactionHistory;
import com.walletserver.transaction.entity.TransactionHistory.TransactionStatus;
import com.walletserver.transaction.entity.TransactionHistory.TransactionType;
import com.walletserver.transaction.repository.TransactionHistoryRepository;
import com.walletserver.wallet.entity.Wallet;
import com.walletserver.wallet.exception.DuplicateTransactionException;
import com.walletserver.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionHistoryRepository historyRepository;

    @Transactional
    public WithdrawalResponse withdraw(Long walletId, WithdrawalRequest req, boolean useDbLock) {
        if (historyRepository.existsByTransactionId(req.transactionId())) {
            throw new DuplicateTransactionException("이미 처리된 트랜잭션입니다.");
        }

        Wallet wallet = useDbLock
                ? walletRepository.findByIdForUpdate(walletId).orElseThrow()
                : walletRepository.findById(walletId).orElseThrow();

        wallet.decreaseBalance(req.amount());

        TransactionHistory history = TransactionHistory.builder()
                .transactionId(req.transactionId())
                .walletId(walletId)
                .type(TransactionType.WITHDRAWAL)
                .amount(req.amount())
                .balanceSnapshot(wallet.getBalance())
                .status(TransactionStatus.SUCCESS)
                .build();

        historyRepository.save(history);

        return WithdrawalResponse.from(history);
    }
}
