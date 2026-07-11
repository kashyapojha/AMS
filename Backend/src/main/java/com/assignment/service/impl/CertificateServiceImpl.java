package com.assignment.service.impl;

import com.assignment.dto.response.CertificateResponse;
import com.assignment.entity.Assignment;
import com.assignment.entity.Certificate;
import com.assignment.entity.Student;
import com.assignment.entity.Submission;
import com.assignment.enums.SubmissionStatus;
import com.assignment.exception.BadRequestException;
import com.assignment.exception.CustomException;
import com.assignment.exception.ResourceNotFoundException;
import com.assignment.exception.UnauthorizedException;
import com.assignment.mapper.CertificateMapper;
import com.assignment.repository.AssignmentRepository;
import com.assignment.repository.CertificateRepository;
import com.assignment.repository.StudentRepository;
import com.assignment.repository.SubmissionRepository;
import com.assignment.service.CertificateService;
import com.assignment.service.CloudinaryService;
import com.assignment.util.QrCodeUtil;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {

    private final CertificateRepository certificateRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentRepository studentRepository;
    private final CloudinaryService cloudinaryService;
    private final CertificateMapper certificateMapper;
    private final AssignmentRepository assignmentRepository;

    @Override
    @Transactional
    public CertificateResponse generateCertificateForSubmission(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));

        if (submission.getStatus() != SubmissionStatus.REVIEWED && submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BadRequestException("Submission must be submitted or reviewed before generating certificate");
        }

        Student student = submission.getStudent();
        Assignment assignment = submission.getAssignment();

        boolean isQuiz = assignment.getAssignmentType() == com.assignment.enums.AssignmentType.QUIZ;
        
        // 1. Verify passing grade criteria
        Double maxMarks = assignment.getTotalMarks();
        Double marks = submission.getMarks();
        if (marks == null) {
            marks = maxMarks; // Default to maximum marks for completion certificate if not yet graded
        }
        Double passingMarks = assignment.getPassingMarks();
        if (passingMarks == null) {
            passingMarks = maxMarks * 0.4; // fallback to 40%
        }
        
        if (marks < passingMarks) {
            throw new BadRequestException("Student did not pass the passing score required for certificate generation");
        }

        // 2. Validate / Check for duplicate certificate generation
        Optional<Certificate> existingCert = isQuiz ?
                certificateRepository.findByStudentIdAndQuizId(student.getId(), assignment.getId()) :
                certificateRepository.findByStudentIdAndAssignmentId(student.getId(), assignment.getId());

        if (existingCert.isPresent()) {
            return certificateMapper.toResponse(existingCert.get());
        }

        String studentName = student.getFullName();
        String title = assignment.getTitle();
        LocalDateTime completedAt = submission.getReviewedAt() != null ? submission.getReviewedAt() : submission.getSubmittedAt();
        if (completedAt == null) {
            completedAt = LocalDateTime.now();
        }
        String completionDateStr = completedAt.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
        String teacherName = assignment.getTeacher() != null ? assignment.getTeacher().getFullName() : "Course Instructor";

        // Generate IDs and verification tokens
        String certId = "CERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String verificationToken = UUID.randomUUID().toString();
        
        String frontendUrl = System.getenv("FRONTEND_URL") != null ? System.getenv("FRONTEND_URL") : "http://localhost:5173";
        String verificationUrl = frontendUrl + "/verify-certificate/" + verificationToken;

        // 3. Generate QR Code image and upload to Cloudinary
        byte[] qrBytes = QrCodeUtil.generateQrCode(verificationUrl, 200, 200);
        String qrCodeUrl = cloudinaryService.uploadBytes(qrBytes, "assignment_system/qrcodes");

        // 4. Generate landscape PDF using OpenPDF
        byte[] pdfBytes = renderPdfCertificate(studentName, title, marks, maxMarks, completionDateStr, teacherName, isQuiz, certId, qrBytes);
        String pdfFileUrl = cloudinaryService.uploadBytes(pdfBytes, "assignment_system/certificates");

        if (pdfFileUrl == null) {
            throw new CustomException("Failed to upload certificate PDF to storage provider", 500);
        }

        // 5. Save metadata to database
        Certificate certificate = Certificate.builder()
                .student(student)
                .assignment(isQuiz ? null : assignment)
                .quiz(isQuiz ? assignment : null)
                .certificateUrl(pdfFileUrl) // keep sync
                .cloudinaryPublicId(certId)
                .marks(marks)
                .generatedAt(LocalDateTime.now())
                .certificateType(isQuiz ? "QUIZ" : "ASSIGNMENT")
                .certificateId(certId)
                .studentName(studentName)
                .assignmentName(title)
                .teacherId(assignment.getTeacher() != null ? assignment.getTeacher().getId() : null)
                .teacherName(teacherName)
                .completionDate(completedAt)
                .generatedDate(LocalDateTime.now())
                .pdfFileUrl(pdfFileUrl)
                .verificationToken(verificationToken)
                .qrCodeUrl(qrCodeUrl)
                .build();

        Certificate saved = certificateRepository.save(certificate);
        return certificateMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateResponse> getStudentCertificates(String studentEmail) {
        Student student = studentRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
        List<Certificate> certs = certificateRepository.findByStudentId(student.getId());
        return certificateMapper.toResponseList(certs);
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateResponse getCertificateById(Long id, String email, String role) {
        Certificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));

        if ("STUDENT".equals(role)) {
            Student student = studentRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
            if (!cert.getStudent().getId().equals(student.getId())) {
                throw new UnauthorizedException("Access Denied: You can only view your own certificates");
            }
        }

        return certificateMapper.toResponse(cert);
    }

    @Override
    @Transactional
    public CertificateResponse getCertificateByAssignment(Long assignmentId, String studentEmail) {
        Student student = studentRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
        Optional<Certificate> certOpt = certificateRepository.findByStudentIdAndAssignmentId(student.getId(), assignmentId);
        if (certOpt.isPresent()) {
            return certificateMapper.toResponse(certOpt.get());
        }
        
        // Fallback: Check if submission exists and generate on-the-fly
        Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found. No submission found for this assignment."));
        
        if (submission.getStatus() == SubmissionStatus.SUBMITTED || submission.getStatus() == SubmissionStatus.REVIEWED) {
            return generateCertificateForSubmission(submission.getId());
        } else {
            throw new BadRequestException("Submission has not been completed/submitted yet");
        }
    }
 
    @Override
    @Transactional
    public CertificateResponse getCertificateByQuiz(Long quizId, String studentEmail) {
        Student student = studentRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
        Optional<Certificate> certOpt = certificateRepository.findByStudentIdAndQuizId(student.getId(), quizId);
        if (certOpt.isPresent()) {
            return certificateMapper.toResponse(certOpt.get());
        }
        
        // Fallback: Check if submission exists and generate on-the-fly
        Submission submission = submissionRepository.findByAssignmentIdAndStudentId(quizId, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found. No submission found for this quiz."));
        
        if (submission.getStatus() == SubmissionStatus.SUBMITTED || submission.getStatus() == SubmissionStatus.REVIEWED) {
            return generateCertificateForSubmission(submission.getId());
        } else {
            throw new BadRequestException("Submission has not been completed/submitted yet");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateResponse> searchCertificatesForTeacher(String teacherEmail, String studentName, String type) {
        String queryName = (studentName != null && !studentName.isBlank()) ? "%" + studentName.trim().toLowerCase() + "%" : null;
        String queryType = (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) ? type.toUpperCase() : null;
        List<Certificate> certs = certificateRepository.searchCertificatesForTeacher(teacherEmail, queryName, queryType);
        return certificateMapper.toResponseList(certs);
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateResponse getCertificateByToken(String token) {
        Certificate cert = certificateRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid verification token or certificate not found"));
        return certificateMapper.toResponse(cert);
    }

    private byte[] renderPdfCertificate(
            String studentName,
            String title,
            Double marks,
            Double maxMarks,
            String completionDate,
            String teacherName,
            boolean isQuiz,
            String certId,
            byte[] qrCodeBytes
    ) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate());
        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            PdfContentByte cb = writer.getDirectContent();
            float width = PageSize.A4.rotate().getWidth();  // 842.0
            float height = PageSize.A4.rotate().getHeight(); // 595.0

            Graphics2D g2d = cb.createGraphics(width, height);

            // Anti-aliasing hints
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 1. Draw soft premium off-white background
            g2d.setColor(new Color(253, 253, 253));
            g2d.fillRect(0, 0, (int) width, (int) height);
 
            // Corner decorative gradient
            GradientPaint gp = new GradientPaint(0, 0, new Color(239, 68, 68, 20), 300, 300, new Color(239, 68, 68, 0));
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, 400, 400);
 
            // 2. Draw border frame
            g2d.setColor(new Color(239, 68, 68));
            g2d.setStroke(new BasicStroke(10));
            g2d.drawRect(15, 15, (int) width - 30, (int) height - 30);
 
            g2d.setColor(new Color(210, 210, 210));
            g2d.setStroke(new BasicStroke(1));
            g2d.drawRect(25, 25, (int) width - 50, (int) height - 50);
 
            // Ornaments
            g2d.setColor(new Color(239, 68, 68));
            g2d.fillRect(22, 22, 35, 4);
            g2d.fillRect(22, 22, 4, 35);
            g2d.fillRect((int) width - 57, 22, 35, 4);
            g2d.fillRect((int) width - 26, 22, 4, 35);
            g2d.fillRect(22, (int) height - 26, 35, 4);
            g2d.fillRect(22, (int) height - 57, 4, 35);
            g2d.fillRect((int) width - 57, (int) height - 26, 35, 4);
            g2d.fillRect((int) width - 26, (int) height - 57, 4, 35);
 
            // 3. Draw Branding Logo (Top Left)
            int logoX = 50;
            int logoY = 45;
            g2d.setColor(new Color(239, 68, 68));
            g2d.fillRoundRect(logoX, logoY, 30, 30, 8, 8);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
            g2d.drawString("L", logoX + 10, logoY + 22);
 
            g2d.setColor(new Color(51, 51, 51));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2d.drawString("LMS Portal", logoX + 38, logoY + 21);
 
            // Draw Brand Name (Top Right)
            g2d.setColor(new Color(239, 68, 68));
            g2d.fillRoundRect((int) width - 180, logoY, 24, 24, 6, 6);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2d.drawString("X", (int) width - 173, logoY + 18);
 
            g2d.setColor(new Color(51, 51, 51));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2d.drawString("Xebia", (int) width - 150, logoY + 18);
 
            // 4. Certificate Header
            g2d.setColor(new Color(239, 68, 68));
            g2d.setFont(new Font("Serif", Font.BOLD, 36));
            drawCenteredString(g2d, "Certificate of Achievement", (int) width, 140);
 
            // Underline
            g2d.setColor(new Color(254, 202, 202));
            g2d.fillRect((int) width / 2 - 120, 155, 240, 2);
 
            // 5. Presentee text
            g2d.setColor(new Color(102, 102, 102));
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 15));
            drawCenteredString(g2d, "Congratulations,", (int) width, 195);
 
            // 6. Student Name
            g2d.setColor(new Color(51, 51, 51));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 28));
            drawCenteredString(g2d, studentName, (int) width, 250);
 
            // 7. Details description
            g2d.setColor(new Color(102, 102, 102));
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 14));
            drawCenteredString(g2d, "you have successfully completed the " + (isQuiz ? "Quiz" : "Assignment"), (int) width, 310);
 
            // 8. Quiz/Assignment Title
            g2d.setColor(new Color(51, 51, 51));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
            drawCenteredString(g2d, "\"" + title + "\"", (int) width, 345);
 
            // Marks / Date
            g2d.setColor(new Color(102, 102, 102));
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 13));
            String scoreText = String.format("Grade Secured: %.2f / %.2f", marks, maxMarks);
            drawCenteredString(g2d, scoreText, (int) width, 390);
 
            String dateText = "Completion Date: " + completionDate;
            drawCenteredString(g2d, dateText, (int) width, 415);
 
            // 9. QR Code Integration
            if (qrCodeBytes != null) {
                try {
                    java.awt.Image qrImg = ImageIO.read(new java.io.ByteArrayInputStream(qrCodeBytes));
                    g2d.drawImage(qrImg, 70, (int) height - 150, 80, 80, null);
                    g2d.setColor(new Color(120, 120, 120));
                    g2d.setFont(new Font("SansSerif", Font.PLAIN, 9));
                    g2d.drawString("Scan to Verify", 78, (int) height - 58);
                } catch (Exception ex) {
                    // Fail silently
                }
            }
 
            // 10. Platform Gold Certified Seal Stamp
            int sealX = (int) width / 2;
            int sealY = (int) height - 100;
            g2d.setColor(new Color(254, 215, 0, 45)); // Soft gold fill
            g2d.fillOval(sealX - 35, sealY - 35, 70, 70);
            g2d.setColor(new Color(212, 175, 55)); // Gold outline
            g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 3}, 0));
            g2d.drawOval(sealX - 35, sealY - 35, 70, 70);
            g2d.setStroke(new BasicStroke(1));
            g2d.drawOval(sealX - 31, sealY - 31, 62, 62);
            g2d.setColor(new Color(239, 68, 68));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 8));
            g2d.drawString("LMS", sealX - 10, sealY - 5);
            g2d.drawString("PORTAL", sealX - 18, sealY + 8);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 7));
            g2d.drawString("CERTIFIED", sealX - 20, sealY + 20);

            // 11. Instructor Signatures
            g2d.setColor(new Color(102, 102, 102));
            g2d.fillRect((int) width - 250, (int) height - 100, 180, 1);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
            String signatureLabel = (teacherName != null && !teacherName.isBlank()) ? teacherName : "Course Instructor";
            g2d.drawString(signatureLabel, (int) width - 250, (int) height - 82);
            g2d.setFont(new Font("SansSerif", Font.ITALIC, 10));
            g2d.drawString("Authorized Reviewer", (int) width - 250, (int) height - 68);

            // Certificate Business ID details (Bottom Left corner)
            g2d.setColor(new Color(150, 150, 150));
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g2d.drawString("Certificate ID: " + certId, 50, (int) height - 38);

            g2d.dispose();
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation error: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    private void drawCenteredString(Graphics2D g, String text, int width, int y) {
        FontMetrics metrics = g.getFontMetrics(g.getFont());
        int x = (width - metrics.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateResponse getCertificatePreview(Long assignmentOrQuizId, String studentEmail) {
        Student student = studentRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
        Assignment assignment = assignmentRepository.findById(assignmentOrQuizId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment/Quiz not found"));

        boolean isQuiz = assignment.getAssignmentType() == com.assignment.enums.AssignmentType.QUIZ;

        // Check if certificate already exists in database
        Optional<Certificate> existingCert = isQuiz ?
                certificateRepository.findByStudentIdAndQuizId(student.getId(), assignment.getId()) :
                certificateRepository.findByStudentIdAndAssignmentId(student.getId(), assignment.getId());

        if (existingCert.isPresent()) {
            CertificateResponse res = certificateMapper.toResponse(existingCert.get());
            res.setMaxMarks(assignment.getTotalMarks());
            return res;
        }

        // Generate dynamic preview metadata
        Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No submission found for this assignment/quiz"));

        if (submission.getStatus() != SubmissionStatus.REVIEWED && submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BadRequestException("Submission has not been completed/submitted yet");
        }

        Double maxMarks = assignment.getTotalMarks();
        Double marks = submission.getMarks();
        if (marks == null) {
            marks = maxMarks; // Default to maximum marks for completion certificate if not yet graded
        }
        Double passingMarks = assignment.getPassingMarks() != null ? assignment.getPassingMarks() : maxMarks * 0.4;
        if (marks < passingMarks) {
            throw new BadRequestException("Student did not pass the required passing score");
        }

        LocalDateTime completedAt = submission.getReviewedAt() != null ? submission.getReviewedAt() : submission.getSubmittedAt();
        if (completedAt == null) {
            completedAt = LocalDateTime.now();
        }

        String teacherName = assignment.getTeacher() != null ? assignment.getTeacher().getFullName() : "Course Instructor";

        return CertificateResponse.builder()
                .studentId(student.getId())
                .studentName(student.getFullName())
                .assignmentId(isQuiz ? null : assignment.getId())
                .assignmentTitle(isQuiz ? null : assignment.getTitle())
                .quizId(isQuiz ? assignment.getId() : null)
                .quizTitle(isQuiz ? assignment.getTitle() : null)
                .marks(marks)
                .maxMarks(maxMarks)
                .certificateType(isQuiz ? "QUIZ" : "ASSIGNMENT")
                .teacherName(teacherName)
                .completionDate(completedAt)
                .generatedAt(LocalDateTime.now())
                .generatedDate(LocalDateTime.now())
                .certificateId("PREVIEW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();
    }

    @Override
    @Transactional
    public byte[] downloadOrGenerateCertificate(Long assignmentOrQuizId, String studentEmail) {
        Student student = studentRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
        Assignment assignment = assignmentRepository.findById(assignmentOrQuizId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment/Quiz not found"));

        boolean isQuiz = assignment.getAssignmentType() == com.assignment.enums.AssignmentType.QUIZ;

        // Check if certificate already exists in database
        Optional<Certificate> existingCert = isQuiz ?
                certificateRepository.findByStudentIdAndQuizId(student.getId(), assignment.getId()) :
                certificateRepository.findByStudentIdAndAssignmentId(student.getId(), assignment.getId());

        if (existingCert.isPresent()) {
            Certificate cert = existingCert.get();
            // If PDF data is stored directly in database, return it immediately!
            if (cert.getPdfData() != null && cert.getPdfData().length > 0) {
                return cert.getPdfData();
            }
            
            // Fallback: If not stored, download it, save it in the database for next time, and return
            try {
                byte[] pdfBytes = fetchPdfFromUrl(cert.getPdfFileUrl() != null ? cert.getPdfFileUrl() : cert.getCertificateUrl());
                cert.setPdfData(pdfBytes);
                cert.setFileName("certificate-" + cert.getCertificateId() + ".pdf");
                cert.setContentType("application/pdf");
                certificateRepository.saveAndFlush(cert);
                return pdfBytes;
            } catch (Exception e) {
                throw new CustomException("Failed to download existing certificate PDF: " + e.getMessage(), 500);
            }
        }

        // Generate new certificate on-the-fly and save to database
        Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No submission found for this assignment/quiz"));

        if (submission.getStatus() != SubmissionStatus.REVIEWED && submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BadRequestException("Submission has not been completed/submitted yet");
        }

        Double maxMarks = assignment.getTotalMarks();
        Double marks = submission.getMarks();
        if (marks == null) {
            marks = maxMarks;
        }
        Double passingMarks = assignment.getPassingMarks() != null ? assignment.getPassingMarks() : maxMarks * 0.4;
        if (marks < passingMarks) {
            throw new BadRequestException("Student did not pass the required passing score");
        }

        String studentName = student.getFullName();
        String title = assignment.getTitle();
        LocalDateTime completedAt = submission.getReviewedAt() != null ? submission.getReviewedAt() : submission.getSubmittedAt();
        if (completedAt == null) {
            completedAt = LocalDateTime.now();
        }
        String completionDateStr = completedAt.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
        String teacherName = assignment.getTeacher() != null ? assignment.getTeacher().getFullName() : "Course Instructor";

        String certId = "CERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String verificationToken = UUID.randomUUID().toString();
        
        String frontendUrl = System.getenv("FRONTEND_URL") != null ? System.getenv("FRONTEND_URL") : "http://localhost:5173";
        String verificationUrl = frontendUrl + "/verify-certificate/" + verificationToken;

        // Save placeholder first to verify database constraints
        Certificate certificate = Certificate.builder()
                .student(student)
                .assignment(isQuiz ? null : assignment)
                .quiz(isQuiz ? assignment : null)
                .certificateUrl("PENDING_UPLOAD")
                .cloudinaryPublicId(certId)
                .marks(marks)
                .generatedAt(LocalDateTime.now())
                .certificateType(isQuiz ? "QUIZ" : "ASSIGNMENT")
                .certificateId(certId)
                .studentName(studentName)
                .assignmentName(title)
                .teacherId(assignment.getTeacher() != null ? assignment.getTeacher().getId() : null)
                .teacherName(teacherName)
                .completionDate(completedAt)
                .generatedDate(LocalDateTime.now())
                .pdfFileUrl("PENDING_UPLOAD")
                .verificationToken(verificationToken)
                .qrCodeUrl("PENDING_UPLOAD")
                .build();

        certificate = certificateRepository.saveAndFlush(certificate);

        // Generate QR code and PDF bytes
        byte[] qrBytes = QrCodeUtil.generateQrCode(verificationUrl, 200, 200);
        String qrCodeUrl = cloudinaryService.uploadBytes(qrBytes, "assignment_system/qrcodes");

        byte[] pdfBytes = renderPdfCertificate(studentName, title, marks, maxMarks, completionDateStr, teacherName, isQuiz, certId, qrBytes);
        String pdfFileUrl = cloudinaryService.uploadBytes(pdfBytes, "assignment_system/certificates");

        if (pdfFileUrl == null) {
            throw new CustomException("Failed to upload certificate PDF to storage provider", 500);
        }

        // Update the certificate with the actual Cloudinary URLs AND the PDF bytes!
        certificate.setQrCodeUrl(qrCodeUrl);
        certificate.setCertificateUrl(pdfFileUrl);
        certificate.setPdfFileUrl(pdfFileUrl);
        certificate.setPdfData(pdfBytes);
        certificate.setFileName("certificate-" + certId + ".pdf");
        certificate.setContentType("application/pdf");
        
        certificateRepository.saveAndFlush(certificate);

        return pdfBytes;
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateResponse getCertificateByUuid(String uuid, String email, String role) {
        Certificate certificate = certificateRepository.findByCertificateId(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
        
        if ("STUDENT".equals(role)) {
            Student student = studentRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
            if (!certificate.getStudent().getId().equals(student.getId())) {
                throw new UnauthorizedException("You are not authorized to access this certificate");
            }
        }
        return certificateMapper.toResponse(certificate);
    }

    @Override
    @Transactional
    public byte[] downloadCertificateByUuid(String idOrUuid, String studentEmail) {
        Certificate certificate;
        if (idOrUuid.matches("\\d+")) {
            certificate = certificateRepository.findById(Long.parseLong(idOrUuid))
                    .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
        } else {
            certificate = certificateRepository.findByCertificateId(idOrUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
        }

        // Verify ownership
        if (!certificate.getStudent().getEmail().equals(studentEmail)) {
            throw new UnauthorizedException("You are not authorized to access this certificate");
        }

        // If PDF data is stored directly in database, return it directly
        if (certificate.getPdfData() != null && certificate.getPdfData().length > 0) {
            return certificate.getPdfData();
        }

        // Generate PDF and save to database
        Assignment assignment = certificate.getAssignment() != null ? certificate.getAssignment() : certificate.getQuiz();
        if (assignment == null) {
            throw new BadRequestException("Certificate is not linked to any assignment or quiz");
        }

        boolean isQuiz = certificate.getCertificateType().equals("QUIZ");
        Double maxMarks = assignment.getTotalMarks();
        Double marks = certificate.getMarks();
        String studentName = certificate.getStudentName();
        String title = certificate.getAssignmentName();
        String completionDateStr = certificate.getCompletionDate().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
        String teacherName = certificate.getTeacherName();
        String certId = certificate.getCertificateId();

        String frontendUrl = System.getenv("FRONTEND_URL") != null ? System.getenv("FRONTEND_URL") : "http://localhost:5173";
        String verificationUrl = frontendUrl + "/verify-certificate/" + certificate.getVerificationToken();

        byte[] qrBytes = QrCodeUtil.generateQrCode(verificationUrl, 200, 200);
        byte[] pdfBytes = renderPdfCertificate(studentName, title, marks, maxMarks, completionDateStr, teacherName, isQuiz, certId, qrBytes);

        certificate.setPdfData(pdfBytes);
        certificate.setFileName("certificate-" + certId + ".pdf");
        certificate.setContentType("application/pdf");
        
        certificateRepository.saveAndFlush(certificate);

        return pdfBytes;
    }

    private byte[] fetchPdfFromUrl(String fileUrl) throws Exception {
        java.net.URL url = new java.net.URL(fileUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
            throw new Exception("Server returned HTTP response code: " + responseCode);
        }
        
        java.io.InputStream in = conn.getInputStream();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        in.close();
        conn.disconnect();
        return out.toByteArray();
    }
}
