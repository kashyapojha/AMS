package com.assignment.mapper;

import com.assignment.dto.response.BatchResponse;
import com.assignment.entity.Batch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BatchMapper {

    @Mapping(target = "teacherId", source = "teacher.id")
    @Mapping(target = "teacherName", source = "teacher.fullName")
    @Mapping(target = "studentsCount", expression = "java(batch.getStudents() != null ? batch.getStudents().size() : 0)")
    BatchResponse toResponse(Batch batch);

    List<BatchResponse> toResponseList(List<Batch> batches);
}
