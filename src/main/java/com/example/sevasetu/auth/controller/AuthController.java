package com.example.sevasetu.auth.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sevasetu.auth.dto.SendOtpRequest;
import com.example.sevasetu.auth.dto.VerifyOtp;
import com.example.sevasetu.auth.service.AuthService;
import com.example.sevasetu.common.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;


@RestController
@RequestMapping("/auth")
public class AuthController {
    private AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }
 
    @PostMapping(
       value = "sendotp",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        return this.authService.sendOtp(request);
    }


    @PostMapping (
        value = "verifyotp",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<Map<String,String>>> verifyOtp(@RequestBody VerifyOtp verifyOtp){

        System.out.println(verifyOtp.phoneNumber());
        System.out.println(verifyOtp.otp());
        return this.authService.verifyOtp(verifyOtp);
    }
}