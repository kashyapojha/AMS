package com.assignment.service;

import com.assignment.dto.request.BatchRequest;
import com.assignment.dto.response.BatchResponse;

import java.util.List;

public interface BatchService {
    BatchResponse createBatch(BatchRequest request, String teacherEmail);
    List<BatchResponse> getAllBatches(String teacherEmail, int page, int size);
    BatchResponse getBatchById(Long id, String teacherEmail);
    BatchResponse updateBatch(Long id, BatchRequest request, String teacherEmail);
    void deleteBatch(Long id, String teacherEmail);
}
