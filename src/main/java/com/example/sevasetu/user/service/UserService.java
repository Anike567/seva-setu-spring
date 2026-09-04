package com.example.sevasetu.user.service;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.user.dto.CreateUserDTO;
import java.util.Map;

@Service
public class UserService {
    JdbcClient jdbcClient;

    public UserService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ResponseEntity<ApiResponse<Map<String, String>>> createUser(CreateUserDTO createUserDTO, String phoneNumber) {
        // Implement the logic to create a user in the database using jdbcClient
        // For now, just return a placeholder response

        System.out.println("Creating user with phone number: " + phoneNumber);
        return ResponseEntity.ok(ApiResponse.success(
            "User data saved successfully",
            Map.of("name" , createUserDTO.fullName(), "phoneNumber", phoneNumber)
        ));
    }
}
