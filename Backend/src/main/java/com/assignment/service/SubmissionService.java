package com.assignment.service;

import com.assignment.dto.request.StudentSubmitRequest;
import com.assignment.dto.request.SubmissionReviewRequest;
import com.assignment.dto.response.StudentResponse;
import com.assignment.dto.response.SubmissionResponse;

import java.util.List;

public interface SubmissionService {
    SubmissionResponse submitAssignment(Long assignmentId, StudentSubmitRequest request, String studentEmail);
    List<SubmissionResponse> getSubmittedSubmissions(Long assignmentId, String teacherEmail);
    List<StudentResponse> getPendingStudents(Long assignmentId, String teacherEmail);
    SubmissionResponse reviewSubmission(Long submissionId, SubmissionReviewRequest request, String teacherEmail);
    List<SubmissionResponse> getStudentSubmissions(String studentEmail, int page, int size);
    SubmissionResponse getSubmissionById(Long id, String email, String role);
}
