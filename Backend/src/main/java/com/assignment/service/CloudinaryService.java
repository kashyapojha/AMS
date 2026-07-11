package com.assignment.service;

import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {
    String uploadFile(MultipartFile file, String folder);
    String uploadBytes(byte[] bytes, String folder);
}
