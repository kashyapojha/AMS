package com.assignment.controller;

import com.assignment.dto.request.LoginRequest;
import com.assignment.dto.request.StudentRegisterRequest;
import com.assignment.dto.request.TeacherRegisterRequest;
import com.assignment.dto.response.ApiResponse;
import com.assignment.dto.response.AuthResponse;
import com.assignment.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private void setJwtCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("JWT_TOKEN", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // In production, set to true to enforce HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60); // 24 hours
        response.addCookie(cookie);
    }

    @PostMapping("/register/teacher")
    public ResponseEntity<ApiResponse<AuthResponse>> registerTeacher(
            @Valid @RequestBody TeacherRegisterRequest request,
            HttpServletResponse servletResponse
    ) {
        AuthResponse response = authService.registerTeacher(request);
        setJwtCookie(servletResponse, response.getToken());
        return ResponseEntity.ok(ApiResponse.success("Teacher registered successfully", response));
    }

    @PostMapping("/register/student")
    public ResponseEntity<ApiResponse<AuthResponse>> registerStudent(
            @Valid @RequestBody StudentRegisterRequest request,
            HttpServletResponse servletResponse
    ) {
        AuthResponse response = authService.registerStudent(request);
        setJwtCookie(servletResponse, response.getToken());
        return ResponseEntity.ok(ApiResponse.success("Student registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse servletResponse
    ) {
        AuthResponse response = authService.login(request);
        setJwtCookie(servletResponse, response.getToken());
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse servletResponse) {
        Cookie cookie = new Cookie("JWT_TOKEN", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        servletResponse.addCookie(cookie);
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }

    @GetMapping("/batches")
    public ResponseEntity<ApiResponse<java.util.List<com.assignment.dto.response.BatchResponse>>> getPublicBatches() {
        java.util.List<com.assignment.dto.response.BatchResponse> response = authService.getPublicBatches();
        return ResponseEntity.ok(ApiResponse.success("Batches retrieved successfully", response));
    }

    @PutMapping("/profile/update")
    public ResponseEntity<ApiResponse<AuthResponse>> updateProfile(
            @RequestParam("name") String name,
            java.security.Principal principal
    ) {
        if (principal == null) {
            throw new com.assignment.exception.UnauthorizedException("Access Denied: You must be logged in to update your profile");
        }
        AuthResponse response = authService.updateProfile(principal.getName(), name);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }
}
