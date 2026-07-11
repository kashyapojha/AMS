package com.assignment.service.impl;

import com.assignment.dto.request.AddStudentRequest;
import com.assignment.dto.response.StudentResponse;
import com.assignment.entity.Assignment;
import com.assignment.entity.Batch;
import com.assignment.entity.Student;
import com.assignment.entity.Teacher;
import com.assignment.enums.Role;
import com.assignment.exception.BadRequestException;
import com.assignment.exception.ResourceNotFoundException;
import com.assignment.mapper.UserMapper;
import com.assignment.repository.AssignmentRepository;
import com.assignment.repository.BatchRepository;
import com.assignment.repository.StudentRepository;
import com.assignment.repository.TeacherRepository;
import com.assignment.service.StudentService;
import com.assignment.service.RedisService;
import com.assignment.dto.response.AssignmentStatusResponse;
import com.assignment.entity.Submission;
import com.assignment.enums.SubmissionStatus;
import com.assignment.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final BatchRepository batchRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final RedisService redisService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    private Teacher getTeacher(String email) {
        Teacher teacher = teacherRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));
        if (teacher.getRole() != com.assignment.enums.Role.TEACHER) {
            throw new com.assignment.exception.UnauthorizedException("Access Denied: Only teachers can perform this action");
        }
        return teacher;
    }

    private void syncBatchAssignmentsRedis(Long batchId) {
        List<Assignment> assignments = assignmentRepository.findByBatchId(batchId, Pageable.unpaged()).getContent();
        for (Assignment assignment : assignments) {
            rebuildAssignmentStatusCache(assignment.getId());
        }
    }

    private void rebuildAssignmentStatusCache(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null) return;

        if (assignment.getBatch() == null) {
            AssignmentStatusResponse cache = AssignmentStatusResponse.builder()
                    .submittedStudentIds(List.of())
                    .pendingStudentIds(List.of())
                    .submittedCount(0)
                    .pendingCount(0)
                    .completionPercentage(0.0)
                    .build();
            redisService.saveAssignmentStatus(assignmentId, cache);
            return;
        }

        List<Student> students = studentRepository.findByBatchId(assignment.getBatch().getId());
        List<Long> allStudentIds = students.stream().map(Student::getId).toList();

        List<Submission> submissions = submissionRepository.findByAssignmentId(assignmentId);
        List<Long> submittedStudentIds = submissions.stream()
                .filter(sub -> sub.getStatus() == SubmissionStatus.SUBMITTED || sub.getStatus() == SubmissionStatus.REVIEWED)
                .map(sub -> sub.getStudent().getId())
                .toList();

        List<Long> pendingStudentIds = allStudentIds.stream()
                .filter(id -> !submittedStudentIds.contains(id))
                .toList();

        int total = allStudentIds.size();
        int submitted = submittedStudentIds.size();
        int pending = pendingStudentIds.size();
        double pct = total > 0 ? ((double) submitted / total) * 100.0 : 0.0;

        AssignmentStatusResponse cache = AssignmentStatusResponse.builder()
                .submittedStudentIds(submittedStudentIds)
                .pendingStudentIds(pendingStudentIds)
                .submittedCount(submitted)
                .pendingCount(pending)
                .completionPercentage(Math.round(pct * 100.0) / 100.0)
                .build();

        redisService.saveAssignmentStatus(assignmentId, cache);
    }

    @Override
    @Transactional
    public StudentResponse addStudentToBatch(AddStudentRequest request, String teacherEmail) {
        Teacher teacher = getTeacher(teacherEmail);
        Batch batch = batchRepository.findByIdAndTeacherId(request.getBatchId(), teacher.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found or unauthorized"));

        Student student;

        if (request.getStudentId() != null) {
            student = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        } else if (request.getStudentEmail() != null && !request.getStudentEmail().isBlank()) {
            student = studentRepository.findByEmail(request.getStudentEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with email: " + request.getStudentEmail()));
        } else if (request.getEmail() != null && !request.getEmail().isBlank()) {
            // Register a new student directly
            if (teacherRepository.existsByEmail(request.getEmail()) || studentRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email is already registered");
            }
            student = Student.builder()
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword() != null ? request.getPassword() : "Password123"))
                    .phone(request.getPhone())
                    .role(Role.STUDENT)
                    .build();
        } else {
            throw new BadRequestException("Provide either studentId, studentEmail, or new student details to add to batch");
        }

        student.setBatch(batch);
        Student savedStudent = studentRepository.save(student);

        // Sync Redis for all assignments of this batch
        syncBatchAssignmentsRedis(batch.getId());

        return userMapper.toStudentResponse(savedStudent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponse> getStudentsByBatch(Long batchId, String teacherEmail) {
        Teacher teacher = getTeacher(teacherEmail);
        Batch batch = batchRepository.findByIdAndTeacherId(batchId, teacher.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found or unauthorized"));

        List<Student> students = studentRepository.findByBatchId(batch.getId());
        return userMapper.toStudentResponseList(students);
    }

    @Override
    @Transactional
    public void removeStudentFromBatch(Long studentId, String teacherEmail) {
        Teacher teacher = getTeacher(teacherEmail);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        if (student.getBatch() == null) {
            throw new BadRequestException("Student is not assigned to any batch");
        }

        Batch batch = student.getBatch();
        if (!batch.getTeacher().getId().equals(teacher.getId())) {
            throw new BadRequestException("Unauthorized: You do not own the batch this student belongs to");
        }

        student.setBatch(null);
        studentRepository.save(student);

        // Sync Redis cache for assignments of the old batch
        syncBatchAssignmentsRedis(batch.getId());
    }
}
