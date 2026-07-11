package com.assignment.service;

import com.assignment.dto.request.AssignmentRequest;
import com.assignment.dto.response.AssignmentResponse;

import java.util.List;

public interface AssignmentService {
    AssignmentResponse createAssignment(AssignmentRequest request, String teacherEmail);
    List<AssignmentResponse> getAllAssignments(String teacherEmail, int page, int size);
    List<AssignmentResponse> getStudentAssignments(String studentEmail, int page, int size);
    AssignmentResponse getAssignmentById(Long id, String email, String role);
    AssignmentResponse updateAssignment(Long id, AssignmentRequest request, String teacherEmail);
    void deleteAssignment(Long id, String teacherEmail);
    List<com.assignment.dto.request.QuestionRequest> importExcelQuestions(org.springframework.web.multipart.MultipartFile file);
    AssignmentResponse importAssignment(
            String title,
            String description,
            Long batchId,
            java.time.LocalDate dueDate,
            org.springframework.web.multipart.MultipartFile file,
            String teacherEmail
    );
    byte[] exportAssignmentResults(Long assignmentId, String teacherEmail);
    List<AssignmentResponse> assignBatch(Long assignmentId, List<Long> batchIds, String teacherEmail);
    AssignmentResponse unassignBatch(Long assignmentId, String teacherEmail);
}
