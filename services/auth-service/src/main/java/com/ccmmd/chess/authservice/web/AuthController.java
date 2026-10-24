package com.ccmmd.chess.authservice.web;

import com.ccmmd.chess.authservice.dto.LoginRequest;
import com.ccmmd.chess.authservice.dto.RefreshRequest;
import com.ccmmd.chess.authservice.dto.RegisterRequest;
import com.ccmmd.chess.authservice.dto.TokenResponse;
import com.ccmmd.chess.authservice.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.register(request));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return accounts.login(request);
    }

    /** Gateway-only: guarded by the internal service token, not by a user token. */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return accounts.refresh(request.userId());
    }
}
