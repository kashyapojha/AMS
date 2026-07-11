package com.assignment.controller;

import com.assignment.dto.request.AddStudentRequest;
import com.assignment.dto.response.ApiResponse;
import com.assignment.dto.response.StudentResponse;
import com.assignment.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @PostMapping("/students")
    public ResponseEntity<ApiResponse<StudentResponse>> addStudentToBatch(
            @Valid @RequestBody AddStudentRequest request,
            Principal principal
    ) {
        StudentResponse response = studentService.addStudentToBatch(request, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Student added to batch successfully", response));
    }

    @GetMapping("/batches/{batchId}/students")
    public ResponseEntity<ApiResponse<List<StudentResponse>>> getStudentsByBatch(
            @PathVariable Long batchId,
            Principal principal
    ) {
        List<StudentResponse> response = studentService.getStudentsByBatch(batchId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Students retrieved successfully", response));
    }

    @DeleteMapping("/students/{studentId}")
    public ResponseEntity<ApiResponse<Void>> removeStudentFromBatch(
            @PathVariable Long studentId,
            Principal principal
    ) {
        studentService.removeStudentFromBatch(studentId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Student removed from batch successfully", null));
    }
}
