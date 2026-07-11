import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider } from './contexts/AuthContext';
import { ThemeProvider } from './contexts/ThemeContext';
import { ProtectedRoute, PublicRoute } from './components/shared/ProtectedRoute';

// Pages
import { AuthPage } from './pages/auth/AuthPage';
import { TeacherDashboard } from './pages/teacher/TeacherDashboard';
import { TeacherAssignments } from './pages/teacher/TeacherAssignments';
import { TeacherQuizzes } from './pages/teacher/TeacherQuizzes';
import { CreateAssignment } from './pages/teacher/CreateAssignment';
import { SubmittedAssignments } from './pages/teacher/SubmittedAssignments';
import { TeacherProfile } from './pages/teacher/TeacherProfile';
import { BatchManagement } from './pages/teacher/BatchManagement';
import { CourseCatalog } from './pages/teacher/CourseCatalog';
import { StudentDashboard } from './pages/student/StudentDashboard';
import { StudentAssignments } from './pages/student/StudentAssignments';
import { AssignmentDetail } from './pages/student/AssignmentDetail';
import { LearningProgress } from './pages/student/LearningProgress';
import { StudentProfile } from './pages/student/StudentProfile';
import { StudentQuizzes } from './pages/student/StudentQuizzes';
import { QuizAttempt } from './pages/student/QuizAttempt';
import { QuizReview } from './pages/student/QuizReview';
import { StudentCertificates } from './pages/student/StudentCertificates';
import { CertificatePreview } from './pages/student/CertificatePreview';
import { TeacherCertificates } from './pages/teacher/TeacherCertificates';
import { VerifyCertificate } from './pages/shared/VerifyCertificate';

function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            {/* Unified Auth Page as default route */}
            <Route path="/" element={<AuthPage />} />
            
            {/* Public Certificate Verification Link */}
            <Route path="/verify-certificate/:token" element={<VerifyCertificate />} />

            {/* Redirect legacy teacher login */}
            <Route path="/teacher/login" element={<Navigate to="/?role=teacher" replace />} />

            {/* Teacher Protected */}
            <Route
              path="/teacher/dashboard"
              element={<ProtectedRoute role="teacher"><TeacherDashboard /></ProtectedRoute>}
            />
            <Route
              path="/teacher/batches"
              element={<ProtectedRoute role="teacher"><BatchManagement /></ProtectedRoute>}
            />
            <Route
              path="/teacher/assignments"
              element={<ProtectedRoute role="teacher"><TeacherAssignments /></ProtectedRoute>}
            />
            <Route
              path="/teacher/quizzes"
              element={<ProtectedRoute role="teacher"><TeacherQuizzes /></ProtectedRoute>}
            />
            <Route
              path="/teacher/assignments/create"
              element={<ProtectedRoute role="teacher"><CreateAssignment /></ProtectedRoute>}
            />
            <Route
              path="/teacher/assignments/edit/:id"
              element={<ProtectedRoute role="teacher"><CreateAssignment /></ProtectedRoute>}
            />
            <Route
              path="/teacher/submitted"
              element={<ProtectedRoute role="teacher"><SubmittedAssignments /></ProtectedRoute>}
            />
            <Route
              path="/teacher/certificates"
              element={<ProtectedRoute role="teacher"><TeacherCertificates /></ProtectedRoute>}
            />
            <Route
              path="/teacher/profile"
              element={<ProtectedRoute role="teacher"><TeacherProfile /></ProtectedRoute>}
            />
            <Route
              path="/teacher/catalog"
              element={<ProtectedRoute role="teacher"><CourseCatalog /></ProtectedRoute>}
            />

            {/* Redirect legacy student login */}
            <Route path="/student/login" element={<Navigate to="/?role=student" replace />} />

            {/* Student Protected */}
            <Route
              path="/student/dashboard"
              element={<ProtectedRoute role="student"><StudentDashboard /></ProtectedRoute>}
            />
            <Route
              path="/student/assignments"
              element={<ProtectedRoute role="student"><StudentAssignments /></ProtectedRoute>}
            />
            <Route
              path="/student/assignments/:id"
              element={<ProtectedRoute role="student"><AssignmentDetail /></ProtectedRoute>}
            />
            <Route
              path="/student/quizzes"
              element={<ProtectedRoute role="student"><StudentQuizzes /></ProtectedRoute>}
            />
            <Route
              path="/student/quizzes/:id/attempt"
              element={<ProtectedRoute role="student"><QuizAttempt /></ProtectedRoute>}
            />
            <Route
              path="/student/quizzes/:id/review"
              element={<ProtectedRoute role="student"><QuizReview /></ProtectedRoute>}
            />
            <Route
              path="/student/certificates"
              element={<ProtectedRoute role="student"><StudentCertificates /></ProtectedRoute>}
            />
            <Route
              path="/student/certificates/preview/:id"
              element={<ProtectedRoute role="student"><CertificatePreview /></ProtectedRoute>}
            />
            <Route
              path="/student/progress"
              element={<ProtectedRoute role="student"><LearningProgress /></ProtectedRoute>}
            />
            <Route
              path="/student/profile"
              element={<ProtectedRoute role="student"><StudentProfile /></ProtectedRoute>}
            />

            {/* Redirect /student to /student/dashboard */}
            <Route path="/student" element={<Navigate to="/student/dashboard" replace />} />

            {/* Catch all */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>

        <Toaster
          position="top-right"
          toastOptions={{
            duration: 4000,
            style: {
              background: 'var(--brand-background)',
              color: 'var(--text-primary)',
              border: '1px solid var(--brand-border)',
              borderRadius: '12px',
              fontSize: '14px',
              fontFamily: 'Inter, sans-serif',
            },
            success: {
              iconTheme: { primary: '#2563EB', secondary: 'white' },
            },
            error: {
              iconTheme: { primary: '#ef4444', secondary: 'white' },
            },
          }}
        />
      </AuthProvider>
    </ThemeProvider>
  );
}

export default App;
