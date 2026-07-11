package com.assignment.service;

import com.assignment.dto.response.StudentDashboardResponse;
import com.assignment.dto.response.TeacherDashboardResponse;

public interface DashboardService {
    TeacherDashboardResponse getTeacherDashboard(String teacherEmail);
    StudentDashboardResponse getStudentDashboard(String studentEmail);
}
