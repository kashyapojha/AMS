package com.assignment.mapper;

import com.assignment.dto.response.AssignmentResponse;
import com.assignment.entity.Assignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {QuestionMapper.class})
public interface AssignmentMapper {

    @Mapping(target = "batchId", source = "batch.id")
    @Mapping(target = "batchName", source = "batch.batchName")
    @Mapping(target = "teacherId", source = "teacher.id")
    @Mapping(target = "teacherName", source = "teacher.fullName")
    AssignmentResponse toResponse(Assignment assignment);

    List<AssignmentResponse> toResponseList(List<Assignment> assignments);
}
