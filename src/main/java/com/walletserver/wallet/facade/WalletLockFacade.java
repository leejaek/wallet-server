package com.walletserver.wallet.facade;

import com.walletserver.transaction.dto.WithdrawalRequest;
import com.walletserver.transaction.dto.WithdrawalResponse;
import com.walletserver.wallet.exception.LockAcquisitionException;
import com.walletserver.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletLockFacade {

    private final RedissonClient redissonClient;
    private final WalletService walletService;

    @Value("${wallet.lock.wait-time:3}")
    private long waitTime;

    public WithdrawalResponse withdraw(Long walletId, WithdrawalRequest req) {
        String lockKey = "wallet:lock:" + walletId;
        RLock lock = redissonClient.getFairLock(lockKey);

        try {
            boolean available = lock.tryLock(waitTime, TimeUnit.SECONDS);

            if (!available) {
                throw new LockAcquisitionException("잠시 후 다시 시도해주세요.");
            }

            try {
                return walletService.withdraw(walletId, req, false);
            } finally {
                lock.unlock();
            }

        } catch (RedisConnectionException | RedisTimeoutException e) {
            log.error("Redis 장애 감지! DB Lock으로 전환합니다. Error: {}", e.getMessage());
            return walletService.withdraw(walletId, req, true);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Server Interrupted");
        }
    }
}
