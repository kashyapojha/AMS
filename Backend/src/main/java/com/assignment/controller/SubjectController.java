package com.assignment.controller;

import com.assignment.dto.response.ApiResponse;
import com.assignment.dto.response.SubjectResponse;
import com.assignment.entity.Teacher;
import com.assignment.exception.ResourceNotFoundException;
import com.assignment.mapper.SubjectMapper;
import com.assignment.repository.SubjectRepository;
import com.assignment.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectMapper subjectMapper;

    @GetMapping("/api/teacher/subjects")
    public ResponseEntity<ApiResponse<List<SubjectResponse>>> getTeacherSubjects(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String department,
            Principal principal
    ) {
        Teacher teacher = teacherRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));
        
        String sem = (semester == null || semester.isBlank()) ? null : semester;
        String dept = (department == null || department.isBlank()) ? null : department;

        List<com.assignment.entity.Subject> subjects = subjectRepository.findFilteredSubjectsForTeacher(
                teacher.getId(),
                sem,
                dept
        );

        List<SubjectResponse> response = subjectMapper.toResponseList(subjects);
        return ResponseEntity.ok(ApiResponse.success("Subjects retrieved successfully", response));
    }
}
