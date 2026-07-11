package com.assignment.service;

import com.assignment.entity.Assignment;
import com.assignment.entity.Student;
import com.assignment.entity.Submission;

import java.util.List;

public interface ExcelExportService {
    byte[] generateAssignmentResultExcel(Assignment assignment, List<Student> students, List<Submission> submissions);
}
