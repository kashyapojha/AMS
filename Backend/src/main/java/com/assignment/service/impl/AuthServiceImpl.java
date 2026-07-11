package com.assignment.service.impl;

import com.assignment.config.JwtService;
import com.assignment.dto.request.LoginRequest;
import com.assignment.dto.request.StudentRegisterRequest;
import com.assignment.dto.request.TeacherRegisterRequest;
import com.assignment.dto.response.AuthResponse;
import com.assignment.entity.Student;
import com.assignment.entity.Teacher;
import com.assignment.enums.Role;
import com.assignment.exception.BadRequestException;
import com.assignment.repository.StudentRepository;
import com.assignment.repository.TeacherRepository;
import com.assignment.repository.BatchRepository;
import com.assignment.entity.Batch;
import com.assignment.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @Override
    @Transactional
    public AuthResponse registerTeacher(TeacherRegisterRequest request) {
        if (teacherRepository.existsByEmail(request.getEmail()) || studentRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        Teacher teacher = Teacher.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(Role.TEACHER)
                .build();

        teacherRepository.save(teacher);

        var userDetails = userDetailsService.loadUserByUsername(teacher.getEmail());
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .email(teacher.getEmail())
                .fullName(teacher.getFullName())
                .role(Role.TEACHER)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse registerStudent(StudentRegisterRequest request) {
        if (teacherRepository.existsByEmail(request.getEmail()) || studentRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        Batch batch = null;
        if (request.getBatchId() != null) {
            batch = batchRepository.findById(request.getBatchId()).orElse(null);
        }

        Student student = Student.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .batch(batch)
                .role(Role.STUDENT)
                .build();

        studentRepository.save(student);

        var userDetails = userDetailsService.loadUserByUsername(student.getEmail());
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .email(student.getEmail())
                .fullName(student.getFullName())
                .role(Role.STUDENT)
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        var userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtService.generateToken(userDetails);

        String fullName = "";
        Role role = Role.STUDENT;

        var teacherOpt = teacherRepository.findByEmail(request.getEmail());
        if (teacherOpt.isPresent()) {
            fullName = teacherOpt.get().getFullName();
            role = Role.TEACHER;
        } else {
            var studentOpt = studentRepository.findByEmail(request.getEmail());
            if (studentOpt.isPresent()) {
                fullName = studentOpt.get().getFullName();
                role = Role.STUDENT;
            }
        }

        return AuthResponse.builder()
                .token(token)
                .email(request.getEmail())
                .fullName(fullName)
                .role(role)
                .build();
    }

    @Override
    public java.util.List<com.assignment.dto.response.BatchResponse> getPublicBatches() {
        return batchRepository.findAll().stream()
                .map(b -> com.assignment.dto.response.BatchResponse.builder()
                        .id(b.getId())
                        .batchName(b.getBatchName())
                        .description(b.getDescription())
                        .createdAt(b.getCreatedAt())
                        .updatedAt(b.getUpdatedAt())
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public AuthResponse updateProfile(String email, String newName) {
        var teacherOpt = teacherRepository.findByEmail(email);
        if (teacherOpt.isPresent()) {
            Teacher teacher = teacherOpt.get();
            teacher.setFullName(newName);
            teacherRepository.save(teacher);
            
            var userDetails = userDetailsService.loadUserByUsername(email);
            String token = jwtService.generateToken(userDetails);
            return AuthResponse.builder()
                    .token(token)
                    .email(email)
                    .fullName(teacher.getFullName())
                    .role(Role.TEACHER)
                    .build();
        } else {
            var studentOpt = studentRepository.findByEmail(email);
            if (studentOpt.isPresent()) {
                Student student = studentOpt.get();
                student.setFullName(newName);
                studentRepository.save(student);
                
                var userDetails = userDetailsService.loadUserByUsername(email);
                String token = jwtService.generateToken(userDetails);
                return AuthResponse.builder()
                        .token(token)
                        .email(email)
                        .fullName(student.getFullName())
                        .role(Role.STUDENT)
                        .build();
            }
        }
        throw new com.assignment.exception.ResourceNotFoundException("User profile not found");
    }
}
