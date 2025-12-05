package com.walletserver.wallet.controller;

import com.walletserver.transaction.dto.WithdrawalRequest;
import com.walletserver.transaction.dto.WithdrawalResponse;
import com.walletserver.wallet.facade.WalletLockFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletLockFacade walletLockFacade;

    @PostMapping("/{walletId}/withdraw")
    public ResponseEntity<WithdrawalResponse> withdraw(
            @PathVariable Long walletId,
            @RequestBody @Valid WithdrawalRequest request
    ) {
        WithdrawalResponse response = walletLockFacade.withdraw(walletId, request);
        return ResponseEntity.ok(response);
    }
}
