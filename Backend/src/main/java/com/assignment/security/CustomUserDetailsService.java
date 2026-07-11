package com.assignment.security;

import com.assignment.repository.StudentRepository;
import com.assignment.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Try finding teacher
        var teacherOpt = teacherRepository.findByEmail(username);
        if (teacherOpt.isPresent()) {
            var teacher = teacherOpt.get();
            return new org.springframework.security.core.userdetails.User(
                    teacher.getEmail(),
                    teacher.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + teacher.getRole().name()))
            );
        }

        // 2. Try finding student
        var studentOpt = studentRepository.findByEmail(username);
        if (studentOpt.isPresent()) {
            var student = studentOpt.get();
            return new org.springframework.security.core.userdetails.User(
                    student.getEmail(),
                    student.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + student.getRole().name()))
            );
        }

        throw new UsernameNotFoundException("User not found with email: " + username);
    }
}
