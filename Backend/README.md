# Assignment Management System - Backend

This is the backend of the Assignment Management System, built using **Java Spring Boot**, **Spring Security (JWT)**, **Spring Data JPA (MySQL)**, **Redis** (for caching), and **Cloudinary** (for secure certificate storage).

---

## 📂 Project Architecture & Package Structure

The project follows a standard multi-layered Spring Boot architecture:

*   **`com.assignment.controller`**: REST controllers exposing APIs for Authentication, Teacher actions, Student actions, and Certificate management.
*   **`com.assignment.entity`**: JPA database entity classes representing the core database schema.
*   **`com.assignment.repository`**: Repository interfaces extending `JpaRepository` for database CRUD operations.
*   **`com.assignment.service` & `com.assignment.service.impl`**: Business logic layer defining service interfaces and their implementations.
*   **`com.assignment.dto`**: Data Transfer Objects (DTOs) for request and response payloads.
*   **`com.assignment.mapper`**: MapStruct mapper interfaces for clean conversion between Entities and DTOs.
*   **`com.assignment.security`**: Configurations for JWT filter authentication, token validation, custom UserDetailsService, and URL endpoint authorizations.
*   **`com.assignment.exception`**: Global error handling configurations and custom API exceptions (e.g., `ResourceNotFoundException`, `BadRequestException`).
*   **`com.assignment.enums`**: Enums for Roles (`TEACHER`, `STUDENT`), Assignment Types (`PDF`, `QUIZ`), Submission Statuses, and Assignment Statuses.
*   **`com.assignment.util`**: Utilities including Excel parsers, validators, and export helpers.

---

## 🗄️ Database Entities & Relationships

The database is built on relational integrity and isolation:

1.  **`User`**: Base class mapped via `@Inheritance(strategy = InheritanceType.JOINED)`.
    *   **`Teacher`**: Extends `User`. Owns batches and reviews student submissions.
    *   **`Student`**: Extends `User`. Belongs to a single `Batch` (`@ManyToOne`).
2.  **`Batch`**: Represents a classroom. Links to a single `Teacher` (`@ManyToOne`) and contains many `Student` records.
3.  **`Assignment`**:
    *   Has title, subject, total marks, passing marks, and due date.
    *   References a `Batch` (`@ManyToOne`, nullable to support Draft status) and a `Teacher` (`@ManyToOne`).
    *   Contains `AssignmentStatus` (`DRAFT`, `ACTIVE`, `COMPLETED`).
    *   Contains multiple `Question` entities (`@OneToMany`) for online quizzes.
4.  **`Submission`**:
    *   Represents a student's answer submission for an assignment or quiz.
    *   Links to `Assignment` (`@ManyToOne`) and `Student` (`@ManyToOne`).
    *   Stores `marks`, `feedback`, and `quizAnswers` (JSON format).
5.  **`Certificate`**:
    *   Represents academic certificate records generated upon successful completion of a quiz or assignment.
    *   Links to `Student` (`@ManyToOne`), `Assignment` (`@ManyToOne`, nullable), and `CertificateType` (graded quiz vs assignment).
    *   Stores the secure Cloudinary URL.

---

## 🔒 Authentication & Authorization Flow

The backend secures endpoints using **JWT tokens**:

1.  **Login**: User posts credentials to `/api/auth/login`. Returns user info and a JWT Bearer token in headers or body.
2.  **JWT Filter**: For every request, `JwtAuthenticationFilter` intercepts, extracts the Bearer token, validates against custom secrets, and sets the authenticated security context.
3.  **Role Authorization**:
    *   `/api/teacher/**` endpoints require `ROLE_TEACHER` authority.
    *   `/api/student/**` endpoints require `ROLE_STUDENT` authority.
    *   `/api/auth/**` endpoints are publicly accessible.

---

## 🚀 Key Workflows

### 1. Quiz Draft & Batch Assignment
*   Teachers can create a Quiz without assigning it to a batch immediately. The quiz batch is set to `null` and its status is saved as `DRAFT`.
*   Draft quizzes are cached and visible only to the creator.
*   Teachers can later assign one or multiple batches to the quiz.
*   *Database Isolation*: Since `Assignment` links to a single `Batch`, assigning a quiz to multiple batches clones the assignment entity and its list of questions for each batch, ensuring clean database isolation.

### 2. Auto-Grading & Certificate Generation
*   When a student attempts a quiz, the system automatically grades submissions (compares responses to option answers).
*   If the student's score is equal to or greater than the quiz's `passingMarks`, the backend automatically generates a landscape completion certificate.
*   *Graphics2D Rendering*: The certificate is rendered as a premium PNG using native Java `Graphics2D` (incorporating borders, Xebia branding, student details, marks, and system verification date).
*   The generated PNG is uploaded to **Cloudinary** as raw bytes, and the details are securely saved in the database.

---

## 🔌 API Endpoints Summary

### Teacher Endpoints (`/api/teacher`)
*   `POST /assignments`: Create standard assignments or quiz drafts.
*   `POST /assignments/{id}/assign`: Assign draft assignments/quizzes to one or more batches (supports cloning).
*   `POST /assignments/{id}/unassign`: Unassign an assignment/quiz, reverting it to `DRAFT`.
*   `PUT /submissions/{id}/review`: Review and grade a student's submission. Generates a certificate if the student passes.

### Student Endpoints (`/api/student`)
*   `GET /assignments`: Fetch assignments and quizzes assigned to the student's batch.
*   `POST /submissions`: Submit file uploads for standard assignments or auto-grade quiz responses.

### Certificate Endpoints (`/api/certificates`)
*   `GET /my`: Get list of certificates earned by the logged-in student.
*   `GET /{id}/download`: Fetch raw certificate file from Cloudinary.
