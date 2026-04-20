package com.stockstream.controller;

import com.stockstream.model.ApiResponse;
import com.stockstream.model.JwtRequest;
import com.stockstream.model.JwtResponse;
import com.stockstream.model.RegisterRequest;
import com.stockstream.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody JwtRequest request) {
        JwtResponse jwt = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", jwt));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<JwtResponse>> register(@Valid @RequestBody RegisterRequest request) {
        JwtResponse jwt = authService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("Registration successful", jwt));
    }
}
