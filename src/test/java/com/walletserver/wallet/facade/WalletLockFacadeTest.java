package com.walletserver.wallet.facade;

import com.walletserver.transaction.dto.WithdrawalRequest;
import com.walletserver.transaction.dto.WithdrawalResponse;
import com.walletserver.wallet.exception.LockAcquisitionException;
import com.walletserver.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WalletLockFacadeTest {

    @InjectMocks
    private WalletLockFacade walletLockFacade;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private WalletService walletService;

    @Mock
    private RLock lock;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(walletLockFacade, "waitTime", 2L);
        ReflectionTestUtils.setField(walletLockFacade, "leaseTime", 3L);
    }

    @Test
    @DisplayName("락 획득 성공 시 서비스 호출")
    void withdraw_lock_success() throws InterruptedException {
        // given
        Long walletId = 1L;
        WithdrawalRequest request = new WithdrawalRequest(UUID.randomUUID(), BigDecimal.valueOf(1000));
        WithdrawalResponse expectedResponse = new WithdrawalResponse(request.transactionId(), request.amount(),
                BigDecimal.ZERO, "SUCCESS");

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(walletService.withdraw(walletId, request, false)).willReturn(expectedResponse);

        // when
        WithdrawalResponse response = walletLockFacade.withdraw(walletId, request);

        // then
        assertThat(response).isEqualTo(expectedResponse);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("락 획득 실패 시 예외 발생")
    void withdraw_lock_fail() throws InterruptedException {
        // given
        Long walletId = 1L;
        WithdrawalRequest request = new WithdrawalRequest(UUID.randomUUID(), BigDecimal.valueOf(1000));

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> walletLockFacade.withdraw(walletId, request))
                .isInstanceOf(LockAcquisitionException.class)
                .hasMessage("잠시 후 다시 시도해주세요.");
    }

    @Test
    @DisplayName("Redis 장애 시 DB Lock으로 폴백")
    void withdraw_redis_error_fallback() throws InterruptedException {
        // given
        Long walletId = 1L;
        WithdrawalRequest request = new WithdrawalRequest(UUID.randomUUID(), BigDecimal.valueOf(1000));
        WithdrawalResponse expectedResponse = new WithdrawalResponse(request.transactionId(), request.amount(),
                BigDecimal.ZERO, "SUCCESS");

        given(redissonClient.getLock(anyString())).willReturn(lock);
        doThrow(new RedisConnectionException("Redis Down")).when(lock).tryLock(anyLong(), anyLong(),
                any(TimeUnit.class));
        given(walletService.withdraw(walletId, request, true)).willReturn(expectedResponse);

        // when
        WithdrawalResponse response = walletLockFacade.withdraw(walletId, request);

        // then
        assertThat(response).isEqualTo(expectedResponse);
        verify(walletService).withdraw(walletId, request, true);
    }
}
