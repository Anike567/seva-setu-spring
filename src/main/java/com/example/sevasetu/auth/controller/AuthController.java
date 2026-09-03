package com.example.sevasetu.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sevasetu.auth.dto.SendOtpRequest;
import com.example.sevasetu.auth.service.AuthService;
import com.example.sevasetu.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;


@RestController
@RequestMapping("/auth")
public class AuthController {
    private AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }
 
    @PostMapping(
       value = "/sendotp",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<Void>> login(@RequestBody SendOtpRequest request) {
        return this.authService.sendOtp(request);
    }
}