package com.assignment.mapper;

import com.assignment.dto.response.SubjectResponse;
import com.assignment.entity.Subject;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SubjectMapper {
    SubjectResponse toResponse(Subject subject);
    List<SubjectResponse> toResponseList(List<Subject> subjects);
}
