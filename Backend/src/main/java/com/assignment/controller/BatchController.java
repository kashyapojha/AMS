package com.assignment.controller;

import com.assignment.dto.request.BatchRequest;
import com.assignment.dto.response.ApiResponse;
import com.assignment.dto.response.BatchResponse;
import com.assignment.service.BatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/teacher/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @PostMapping
    public ResponseEntity<ApiResponse<BatchResponse>> createBatch(
            @Valid @RequestBody BatchRequest request,
            Principal principal
    ) {
        BatchResponse response = batchService.createBatch(request, principal.getName());
        return ResponseEntity.status(201).body(ApiResponse.success("Batch created successfully", response, 201));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getAllBatches(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal
    ) {
        List<BatchResponse> response = batchService.getAllBatches(principal.getName(), page, size);
        return ResponseEntity.ok(ApiResponse.success("Batches retrieved successfully", response));
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<ApiResponse<BatchResponse>> getBatch(
            @PathVariable Long batchId,
            Principal principal
    ) {
        BatchResponse response = batchService.getBatchById(batchId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Batch details retrieved successfully", response));
    }

    @PutMapping("/{batchId}")
    public ResponseEntity<ApiResponse<BatchResponse>> updateBatch(
            @PathVariable Long batchId,
            @Valid @RequestBody BatchRequest request,
            Principal principal
    ) {
        BatchResponse response = batchService.updateBatch(batchId, request, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Batch updated successfully", response));
    }

    @DeleteMapping("/{batchId}")
    public ResponseEntity<ApiResponse<Void>> deleteBatch(
            @PathVariable Long batchId,
            Principal principal
    ) {
        batchService.deleteBatch(batchId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Batch deleted successfully", null));
    }
}
