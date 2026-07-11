package com.assignment.service;

import com.assignment.dto.request.LoginRequest;
import com.assignment.dto.request.StudentRegisterRequest;
import com.assignment.dto.request.TeacherRegisterRequest;
import com.assignment.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse registerTeacher(TeacherRegisterRequest request);
    AuthResponse registerStudent(StudentRegisterRequest request);
    AuthResponse login(LoginRequest request);
    java.util.List<com.assignment.dto.response.BatchResponse> getPublicBatches();
    AuthResponse updateProfile(String email, String newName);
}
