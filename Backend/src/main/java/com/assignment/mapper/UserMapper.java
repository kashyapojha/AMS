package com.assignment.mapper;

import com.assignment.dto.response.StudentResponse;
import com.assignment.dto.response.TeacherResponse;
import com.assignment.entity.Student;
import com.assignment.entity.Teacher;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    TeacherResponse toTeacherResponse(Teacher teacher);

    @Mapping(target = "batchId", source = "batch.id")
    @Mapping(target = "batchName", source = "batch.batchName")
    StudentResponse toStudentResponse(Student student);

    List<StudentResponse> toStudentResponseList(List<Student> students);
}
