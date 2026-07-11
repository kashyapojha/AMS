package com.assignment.service;

import com.assignment.dto.QuizImportDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ExcelImportService {
    List<QuizImportDTO> parseExcelFile(MultipartFile file);
}
