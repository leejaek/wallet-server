package com.walletserver.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "transaction_history")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID transactionId;

    @Column(nullable = false, updatable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public TransactionHistory(
            UUID transactionId,
            Long walletId,
            TransactionType type,
            BigDecimal amount,
            BigDecimal balanceSnapshot,
            TransactionStatus status
    ) {
        this.transactionId = transactionId;
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.balanceSnapshot = balanceSnapshot;
        this.status = status;
    }

    public enum TransactionType {
        DEPOSIT, WITHDRAWAL
    }

    public enum TransactionStatus {
        SUCCESS, FAILED
    }
}
