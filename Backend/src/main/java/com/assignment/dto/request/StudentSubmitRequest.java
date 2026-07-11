package com.assignment.dto.request;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSubmitRequest {
    private MultipartFile file;
    private String comment;
    private String quizAnswersJson;
}
