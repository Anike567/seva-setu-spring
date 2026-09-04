package com.example.sevasetu.user.controller;


import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import com.example.sevasetu.user.dto.CreateUserDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.example.sevasetu.common.ApiResponse;
import java.util.Map;
import com.example.sevasetu.user.service.UserService;


@RestController 
@RequestMapping ("/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService){
        this.userService = userService;
    }

    @PostMapping(
        value = "/create",
        consumes = "application/json",
        produces = "application/json"
    )
    public ResponseEntity<ApiResponse<Map<String, String>>> postMethodName(
        @Valid @RequestBody CreateUserDTO createUserDTO, 
        @AuthenticationPrincipal String authenticatedPhone
    ) {
        return this.userService.createUser(createUserDTO, authenticatedPhone);
           
    } 
}
