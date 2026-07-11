package com.assignment.service.impl;

import com.assignment.entity.Assignment;
import com.assignment.entity.Student;
import com.assignment.entity.Submission;
import com.assignment.service.ExcelExportService;
import com.assignment.util.ExcelExportUtil;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class ExcelExportServiceImpl implements ExcelExportService {

    @Override
    public byte[] generateAssignmentResultExcel(Assignment assignment, List<Student> students, List<Submission> submissions) {
        try (Workbook workbook = ExcelExportUtil.createResultWorkbook(assignment, students, submissions);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Excel report: " + e.getMessage(), e);
        }
    }
}
