package com.assignment.service.impl;

import com.assignment.dto.request.BatchRequest;
import com.assignment.dto.response.BatchResponse;
import com.assignment.entity.Assignment;
import com.assignment.entity.Batch;
import com.assignment.entity.Student;
import com.assignment.entity.Teacher;
import com.assignment.exception.ResourceNotFoundException;
import com.assignment.mapper.BatchMapper;
import com.assignment.repository.AssignmentRepository;
import com.assignment.repository.BatchRepository;
import com.assignment.repository.StudentRepository;
import com.assignment.repository.TeacherRepository;
import com.assignment.service.BatchService;
import com.assignment.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchServiceImpl implements BatchService {

    private final BatchRepository batchRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final AssignmentRepository assignmentRepository;
    private final RedisService redisService;
    private final BatchMapper batchMapper;

    private Teacher getTeacher(String email) {
        Teacher teacher = teacherRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));
        if (teacher.getRole() != com.assignment.enums.Role.TEACHER) {
            throw new com.assignment.exception.UnauthorizedException("Access Denied: Only teachers can perform this action");
        }
        return teacher;
    }

    @Override
    @Transactional
    public BatchResponse createBatch(BatchRequest request, String teacherEmail) {
        Teacher teacher = getTeacher(teacherEmail);
        Batch batch = Batch.builder()
                .batchName(request.getBatchName())
                .description(request.getDescription())
                .teacher(teacher)
                .build();
        Batch savedBatch = batchRepository.save(batch);
        return batchMapper.toResponse(savedBatch);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BatchResponse> getAllBatches(String teacherEmail, int page, int size) {
        Teacher teacher = getTeacher(teacherEmail);
        Pageable pageable = PageRequest.of(page, size);
        Page<Batch> batchPage = batchRepository.findByTeacherId(teacher.getId(), pageable);
        return batchMapper.toResponseList(batchPage.getContent());
    }

    @Override
    @Transactional(readOnly = true)
    public BatchResponse getBatchById(Long id, String teacherEmail) {
        Teacher teacher = getTeacher(teacherEmail);
        Batch batch = batchRepository.findByIdAndTeacherId(id, teacher.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found or unauthorized"));
        return batchMapper.toResponse(batch);
    }

    @Override
    @Transactional
    public BatchResponse updateBatch(Long id, BatchRequest request, String teacherEmail) {
        Teacher teacher = getTeacher(teacherEmail);
        Batch batch = batchRepository.findByIdAndTeacherId(id, teacher.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found or unauthorized"));

        batch.setBatchName(request.getBatchName());
        batch.setDescription(request.getDescription());

        Batch updatedBatch = batchRepository.save(batch);
        return batchMapper.toResponse(updatedBatch);
    }

    @Override
    @Transactional
    public void deleteBatch(Long id, String teacherEmail) {
        Teacher teacher = getTeacher(teacherEmail);
        Batch batch = batchRepository.findByIdAndTeacherId(id, teacher.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found or unauthorized"));

        // 1. Unlink students
        for (Student student : batch.getStudents()) {
            student.setBatch(null);
            studentRepository.save(student);
        }

        // 2. Fetch and delete assignments of this batch + their Redis status keys
        Pageable unpaged = Pageable.unpaged();
        Page<Assignment> assignments = assignmentRepository.findByBatchId(batch.getId(), unpaged);
        for (Assignment assignment : assignments.getContent()) {
            redisService.deleteAssignmentStatus(assignment.getId());
            assignmentRepository.delete(assignment);
        }

        // 3. Delete the batch
        batchRepository.delete(batch);
    }
}
