package com.assignment.service;

import com.assignment.dto.request.AddStudentRequest;
import com.assignment.dto.response.StudentResponse;

import java.util.List;

public interface StudentService {
    StudentResponse addStudentToBatch(AddStudentRequest request, String teacherEmail);
    List<StudentResponse> getStudentsByBatch(Long batchId, String teacherEmail);
    void removeStudentFromBatch(Long studentId, String teacherEmail);
}
