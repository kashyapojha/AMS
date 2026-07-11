package com.assignment.service;

import com.assignment.dto.response.AssignmentStatusResponse;

public interface RedisService {
    void saveAssignmentStatus(Long assignmentId, AssignmentStatusResponse status);
    AssignmentStatusResponse getAssignmentStatus(Long assignmentId);
    void deleteAssignmentStatus(Long assignmentId);
}
