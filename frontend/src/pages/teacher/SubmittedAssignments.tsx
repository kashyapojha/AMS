import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { 
  Download, Star, ChevronDown, CheckCircle2, FileText, 
  Search, Calendar, Award, Eye, SlidersHorizontal, Check, Info, FileSpreadsheet, RefreshCw
} from 'lucide-react';
import toast from 'react-hot-toast';
import { Layout } from '../../components/layout/Layout';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { Card } from '../../components/ui/Card';
import { Input, Textarea } from '../../components/ui/Input';
import { EmptyState } from '../../components/shared/EmptyState';
import { TableRowSkeleton } from '../../components/shared/LoadingSkeleton';
import { teacherService } from '../../services/teacher.service';
import { formatDateTime, formatDate, getFileIcon } from '../../utils/helpers';
import type { Assignment, Submission } from '../../types';

export const SubmittedAssignments: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [selectedAssignment, setSelectedAssignment] = useState<Assignment | null>(null);
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [loadingAssignments, setLoadingAssignments] = useState(true);
  const [loadingSubmissions, setLoadingSubmissions] = useState(false);
  const [gradeModal, setGradeModal] = useState<Submission | null>(null);
  const [viewingQuizSub, setViewingQuizSub] = useState<Submission | null>(null);
  
  // Grade Form State
  const [marks, setMarks] = useState('');
  const [feedback, setFeedback] = useState('');
  const [saving, setSaving] = useState(false);

  // Left Panel Tabs & Search
  const [typeTab, setTypeTab] = useState<'all' | 'assignments' | 'quizzes'>('all');
  const [assignmentSearch, setAssignmentSearch] = useState('');
  const [assignmentFilter, setAssignmentFilter] = useState<'all' | 'active' | 'closed' | 'pending_review'>('all');

  // Right Panel Search, Filter & Sort
  const [studentSearch, setStudentSearch] = useState('');
  const [rightPanelTypeFilter, setRightPanelTypeFilter] = useState<'all' | 'assignment' | 'quiz'>('all');
  const [studentFilter, setStudentFilter] = useState<'all' | 'submitted' | 'reviewed' | 'pending' | 'late'>('all');
  const [sortBy, setSortBy] = useState<'name' | 'enrollment' | 'latest' | 'oldest' | 'highest' | 'lowest'>('latest');

  // Right Panel Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 8;

  // Fetch assignments on mount
  const fetchAllAssignments = useCallback(async () => {
    setLoadingAssignments(true);
    try {
      const res = await teacherService.getAssignments({ status: 'published', limit: '100' });
      const list = res.assignments || [];
      setAssignments(list);
      
      const qid = searchParams.get('assignment');
      if (qid) {
        const found = list.find((a: Assignment) => String(a.id) === qid);
        if (found) {
          selectAssignment(found);
        }
      } else if (list.length > 0) {
        selectAssignment(list[0]);
      }
    } catch {
      toast.error('Failed to load assignments.');
    } finally {
      setLoadingAssignments(false);
    }
  }, [searchParams]);

  useEffect(() => {
    fetchAllAssignments();
  }, [fetchAllAssignments]);

  const selectAssignment = useCallback(async (assignment: Assignment) => {
    setLoadingSubmissions(true);
    setCurrentPage(1);
    setStudentSearch('');
    setStudentFilter('all');
    try {
      const [submissionsRes, fullAssignmentRes] = await Promise.all([
        teacherService.getSubmissions(assignment.id),
        teacherService.getAssignmentById(assignment.id)
      ]);
      const updated = (fullAssignmentRes as any).assignment || fullAssignmentRes;
      setSelectedAssignment(updated);
      setSubmissions(submissionsRes.submissions || []);
      
      // Update this assignment's details in the left panel list state
      setAssignments((prev) =>
        prev.map((a) => (a.id === updated.id ? { ...a, ...updated } : a))
      );
    } catch {
      toast.error('Failed to load submissions.');
    } finally {
      setLoadingSubmissions(false);
    }
  }, []);

  const handleTabChange = (tab: 'all' | 'assignments' | 'quizzes') => {
    setTypeTab(tab);
    // Find the first assignment matching the new tab
    const matched = assignments.filter((a) => {
      if (tab === 'assignments' && a.assignmentType === 'QUIZ') return false;
      if (tab === 'quizzes' && a.assignmentType !== 'QUIZ') return false;
      return true;
    });
    if (matched.length > 0) {
      selectAssignment(matched[0]);
    } else {
      setSelectedAssignment(null);
      setSubmissions([]);
    }
  };

  const openGradeModal = (sub: Submission) => {
    setGradeModal(sub);
    setMarks(sub.marks !== null && sub.marks !== undefined ? String(sub.marks) : '');
    setFeedback(sub.feedback || '');
  };

  const handleGrade = async () => {
    if (!gradeModal || gradeModal.status === 'pending') {
      toast.error('Cannot grade a student who has not submitted yet.');
      return;
    }
    if (marks === '') {
      toast.error('Please enter marks.');
      return;
    }
    const numericMarks = Number(marks);
    if (selectedAssignment && (isNaN(numericMarks) || numericMarks < 0 || numericMarks > selectedAssignment.maxMarks)) {
      toast.error(`Marks must be a valid number between 0 and ${selectedAssignment.maxMarks}.`);
      return;
    }

    setSaving(true);
    try {
      await teacherService.gradeSubmission({
        submissionId: gradeModal.id.startsWith('pending-') ? gradeModal.id.replace('pending-', '') : gradeModal.id,
        marks: numericMarks,
        feedback,
      });

      // Update local state
      setSubmissions((prev) =>
        prev.map((s) => {
          if (s.id === gradeModal.id) {
            return {
              ...s,
              marks: numericMarks,
              feedback,
              status: 'reviewed' as any,
            };
          }
          return s;
        })
      );
      
      toast.success('Graded and reviewed successfully!');
      setGradeModal(null);
      
      // Re-fetch assignments list to update counts
      const savedSelectedId = selectedAssignment?.id;
      const resAssignments = await teacherService.getAssignments({ status: 'published', limit: '100' });
      setAssignments(resAssignments.assignments || []);
      if (savedSelectedId) {
        const found = resAssignments.assignments.find((a: Assignment) => a.id === savedSelectedId);
        if (found) setSelectedAssignment(found);
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Failed to grade submission.');
    } finally {
      setSaving(false);
    }
  };

  // Helper: Get active/closed status of assignment
  const getAssignmentStatus = (a: Assignment) => {
    const todayStr = new Date().toISOString().split('T')[0];
    const dueDateStr = a.dueDate;
    if (dueDateStr === todayStr) return 'due_today';
    if (dueDateStr < todayStr) return 'closed';
    return 'active';
  };

  // Helper: Check if submission is late
  const isSubmissionLate = (sub: Submission, dueDateStr: string, dueTimeStr?: string) => {
    if (!sub.submittedAt || !dueDateStr) return false;
    const dueTime = dueTimeStr || '23:59:00';
    return new Date(sub.submittedAt) > new Date(`${dueDateStr}T${dueTime}`);
  };

  // Filtered Assignments List (Left Panel)
  const filteredAssignments = useMemo(() => {
    return assignments.filter((a) => {
      // Tab type filter
      if (typeTab === 'assignments' && a.assignmentType === 'QUIZ') return false;
      if (typeTab === 'quizzes' && a.assignmentType !== 'QUIZ') return false;

      const matchSearch = 
        a.title.toLowerCase().includes(assignmentSearch.toLowerCase()) || 
        a.subject.toLowerCase().includes(assignmentSearch.toLowerCase());
      
      if (!matchSearch) return false;
      
      const status = getAssignmentStatus(a);
      if (assignmentFilter === 'active') return status === 'active' || status === 'due_today';
      if (assignmentFilter === 'closed') return status === 'closed';
      if (assignmentFilter === 'pending_review') {
        return (a.submittedCount || 0) > 0;
      }
      return true;
    });
  }, [assignments, assignmentSearch, assignmentFilter, typeTab]);

  // Combined student details and grading statuses (Right Panel)
  const studentSubmissionsList = useMemo(() => {
    if (!selectedAssignment) return [];

    return submissions.map((sub) => {
      const late = isSubmissionLate(sub, selectedAssignment.dueDate, selectedAssignment.dueTime);
      return {
        ...sub,
        isLate: late,
      };
    });
  }, [submissions, selectedAssignment]);

  // Filter student submissions
  const filteredSubmissions = useMemo(() => {
    return studentSubmissionsList.filter((sub) => {
      // Filter by assignment vs quiz type
      const isQuiz = selectedAssignment?.assignmentType === 'QUIZ';
      if (rightPanelTypeFilter === 'assignment' && isQuiz) return false;
      if (rightPanelTypeFilter === 'quiz' && !isQuiz) return false;

      // Search matches name, email or enrollment
      const matchSearch =
        sub.student?.name.toLowerCase().includes(studentSearch.toLowerCase()) ||
        sub.student?.email.toLowerCase().includes(studentSearch.toLowerCase()) ||
        sub.student?.enrollmentNumber.toLowerCase().includes(studentSearch.toLowerCase());

      if (!matchSearch) return false;

      // Status filters
      if (studentFilter === 'submitted') return sub.status === 'submitted';
      if (studentFilter === 'reviewed') return sub.status === 'reviewed';
      if (studentFilter === 'pending') return sub.status === 'pending';
      if (studentFilter === 'late') return sub.isLate;

      return true;
    });
  }, [studentSubmissionsList, studentSearch, studentFilter, rightPanelTypeFilter, selectedAssignment]);

  // Sort student submissions
  const sortedSubmissions = useMemo(() => {
    return [...filteredSubmissions].sort((a, b) => {
      if (sortBy === 'name') {
        return (a.student?.name || '').localeCompare(b.student?.name || '');
      }
      if (sortBy === 'enrollment') {
        return (a.student?.enrollmentNumber || '').localeCompare(b.student?.enrollmentNumber || '');
      }
      if (sortBy === 'latest') {
        if (!a.submittedAt) return 1;
        if (!b.submittedAt) return -1;
        return new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime();
      }
      if (sortBy === 'oldest') {
        if (!a.submittedAt) return 1;
        if (!b.submittedAt) return -1;
        return new Date(a.submittedAt).getTime() - new Date(b.submittedAt).getTime();
      }
      if (sortBy === 'highest') {
        return (b.marks || 0) - (a.marks || 0);
      }
      if (sortBy === 'lowest') {
        return (a.marks || 0) - (b.marks || 0);
      }
      return 0;
    });
  }, [filteredSubmissions, sortBy]);

  // Paginated student submissions
  const paginatedSubmissions = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return sortedSubmissions.slice(start, start + pageSize);
  }, [sortedSubmissions, currentPage]);

  const totalPages = Math.ceil(sortedSubmissions.length / pageSize) || 1;

  // Metrics for assignment
  const metrics = useMemo(() => {
    if (!selectedAssignment) return { total: 0, submitted: 0, pending: 0, pct: 0 };
    const total = selectedAssignment.totalStudents || submissions.length || 0;
    const submitted = submissions.filter(s => s.status !== 'pending').length;
    const pending = total - submitted;
    const pct = total > 0 ? Math.round((submitted / total) * 100) : 0;
    return { total, submitted, pending, pct };
  }, [selectedAssignment, submissions]);

  // Dynamic counts for all assignments
  const counts = useMemo(() => {
    let assignmentsSubmitted = 0;
    let quizzesSubmitted = 0;
    let pendingAssignments = 0;
    let pendingQuizzes = 0;
    let totalSubmissions = 0;

    assignments.forEach((a) => {
      if (a.assignmentType === 'QUIZ') {
        quizzesSubmitted += a.submittedCount || 0;
        pendingQuizzes += a.pendingCount || 0;
      } else {
        assignmentsSubmitted += a.submittedCount || 0;
        pendingAssignments += a.pendingCount || 0;
      }
      totalSubmissions += a.submittedCount || 0;
    });

    return {
      assignmentsSubmitted,
      quizzesSubmitted,
      pendingAssignments,
      pendingQuizzes,
      totalSubmissions,
    };
  }, [assignments]);

  const submittedAnswersMap = useMemo(() => {
    if (!viewingQuizSub?.quizAnswers) return {};
    try {
      const parsed = JSON.parse(viewingQuizSub.quizAnswers);
      const map: Record<string, string> = {};
      if (Array.isArray(parsed)) {
        parsed.forEach((item: any) => {
          if (item.questionId !== undefined && item.questionId !== null) {
            map[String(item.questionId)] = item.selectedOption ?? item.selectedAnswer ?? '';
          }
        });
      }
      return map;
    } catch {
      return {};
    }
  }, [viewingQuizSub]);

  return (
    <Layout role="teacher" title="Submitted" subtitle="Review and grade student submissions">
      
      {/* Dynamic Counts Banner */}
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 mb-6 select-none">
        {[
          { label: 'Total Submissions', count: counts.totalSubmissions },
          { label: 'Assignments Submitted', count: counts.assignmentsSubmitted },
          { label: 'Quizzes Submitted', count: counts.quizzesSubmitted },
          { label: 'Pending Assignments', count: counts.pendingAssignments },
          { label: 'Pending Quizzes', count: counts.pendingQuizzes },
        ].map((stat, idx) => (
          <div key={idx} className="bg-white dark:bg-[#1E293B] border border-[var(--brand-border)] rounded-2xl p-4 shadow-sm flex flex-col justify-between">
            <span className="text-[10px] text-[var(--text-secondary)] block uppercase font-bold tracking-wider">{stat.label}</span>
            <span className="text-xl font-black text-[var(--text-primary)] mt-1">{stat.count}</span>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        
        {/* =================================LEFT PANEL ================================= */}
        <div className="lg:col-span-4 space-y-4">
          <div className="bg-white dark:bg-[#1E293B] border border-[var(--brand-border)] rounded-2xl p-4 shadow-sm space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-[var(--text-primary)]">Published Assessments</h3>
                <p className="text-xs text-[var(--text-secondary)] mt-0.5">Select an assessment card to view submissions</p>
              </div>
              <button
                onClick={fetchAllAssignments}
                disabled={loadingAssignments}
                className="p-1.5 rounded-lg text-[var(--text-secondary)] hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
                title="Refresh Assessments"
              >
                <RefreshCw size={14} className={loadingAssignments ? 'animate-spin' : ''} />
              </button>
            </div>

            {/* Assessment Type Tabs */}
            <div className="flex gap-1 p-1 bg-slate-50 dark:bg-slate-800/40 border border-[var(--brand-border)] rounded-xl">
              {[
                { value: 'all', label: 'All' },
                { value: 'assignments', label: 'Assignments' },
                { value: 'quizzes', label: 'Quizzes' },
              ].map((tab) => (
                <button
                  key={tab.value}
                  type="button"
                  onClick={() => handleTabChange(tab.value as any)}
                  className={`flex-1 py-1.5 px-2 rounded-lg text-[10px] font-bold tracking-wider uppercase transition-all cursor-pointer ${
                    typeTab === tab.value
                      ? 'bg-[#4A1F4F] text-white shadow-sm'
                      : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Search Bar */}
            <div className="relative">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
              <input
                type="text"
                placeholder="Search assessments..."
                value={assignmentSearch}
                onChange={(e) => setAssignmentSearch(e.target.value)}
                className="w-full h-12 pl-11 pr-4 bg-slate-50 dark:bg-slate-800/40 border border-slate-150 dark:border-slate-700/60 rounded-xl text-sm focus:outline-none focus:border-[#4A1F4F] transition-all"
              />
            </div>

            {/* Filters Tabs */}
            <div className="flex flex-wrap gap-1 bg-slate-50 dark:bg-slate-800/40 p-1 border border-[var(--brand-border)] rounded-xl">
              {[
                { value: 'all', label: 'All' },
                { value: 'active', label: 'Active' },
                { value: 'closed', label: 'Closed' },
                { value: 'pending_review', label: 'Review' },
              ].map((tab) => (
                <button
                  key={tab.value}
                  type="button"
                  onClick={() => setAssignmentFilter(tab.value as any)}
                  className={`flex-1 py-1.5 px-2.5 rounded-lg text-[10px] font-bold tracking-wider uppercase transition-all cursor-pointer ${
                    assignmentFilter === tab.value
                      ? 'bg-[#4A1F4F] text-white shadow-sm'
                      : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          {/* Assignments List */}
          <div className="space-y-3 max-h-[calc(100vh-380px)] overflow-y-auto pr-1">
            {loadingAssignments ? (
              Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="bg-white dark:bg-[#1E293B] border border-[var(--brand-border)] rounded-2xl p-5 space-y-3 animate-pulse">
                  <div className="h-4 w-3/4 bg-slate-200 dark:bg-slate-700 rounded" />
                  <div className="h-3 w-1/2 bg-slate-200 dark:bg-slate-700 rounded" />
                  <div className="h-2 w-full bg-slate-200 dark:bg-slate-700 rounded" />
                </div>
              ))
            ) : filteredAssignments.length === 0 ? (
              <EmptyState icon="inbox" title="No assessments found" description="Try clearing filters or search query." />
            ) : (
              filteredAssignments.map((a) => {
                const isSelected = selectedAssignment?.id === a.id;
                const status = getAssignmentStatus(a);
                const total = a.totalStudents || 0;
                const submitted = a.submittedCount || 0;
                const pending = a.pendingCount || 0;
                const progressPct = total > 0 ? Math.round((submitted / total) * 100) : 0;

                return (
                  <div
                    key={a.id}
                    onClick={() => selectAssignment(a)}
                    className={`p-4 rounded-2xl border transition-all cursor-pointer flex flex-col gap-3 shadow-sm ${
                      isSelected
                        ? 'border-[#4A1F4F] bg-[#4A1F4F05] dark:bg-[#4A1F4F]/5 ring-1 ring-[#4A1F4F]'
                        : 'bg-white dark:bg-[#1E293B] border-[var(--brand-border)] hover:border-[#4A1F4F50] hover:shadow-md'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-center gap-2.5 min-w-0">
                        <div className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 ${
                          isSelected ? 'bg-[#4A1F4F]/15 text-[#4A1F4F] dark:text-purple-400' : 'bg-slate-100 dark:bg-slate-800 text-[var(--text-secondary)]'
                        }`}>
                          {a.assignmentType === 'QUIZ' ? <FileSpreadsheet size={16} /> : <FileText size={16} />}
                        </div>
                        <div className="min-w-0">
                          <h4 className="text-sm font-bold text-[var(--text-primary)] truncate">{a.title}</h4>
                          <p className="text-[10px] text-[var(--text-secondary)] mt-0.5 truncate">{a.subject}</p>
                        </div>
                      </div>

                      {/* Status Badges */}
                      <div className="shrink-0">
                        {status === 'active' && (
                          <span className="text-[9px] uppercase font-extrabold tracking-wider px-2 py-0.5 rounded bg-emerald-100 dark:bg-emerald-950/20 text-emerald-600 dark:text-emerald-400">
                            Active
                          </span>
                        )}
                        {status === 'due_today' && (
                          <span className="text-[9px] uppercase font-extrabold tracking-wider px-2 py-0.5 rounded bg-amber-100 dark:bg-amber-950/20 text-amber-600 dark:text-amber-400">
                            Due Today
                          </span>
                        )}
                        {status === 'closed' && (
                          <span className="text-[9px] uppercase font-extrabold tracking-wider px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-[var(--text-secondary)]">
                            Closed
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Progress details */}
                    <div className="space-y-1.5">
                      <div className="flex justify-between items-center text-[10px] text-[var(--text-secondary)] font-medium">
                        <span>{submitted}/{total} Submitted</span>
                        <span>{progressPct}%</span>
                      </div>
                      <div className="w-full bg-slate-100 dark:bg-slate-800 rounded-full h-1.5 overflow-hidden border border-[var(--brand-border)]">
                        <div
                          className="bg-gradient-to-r from-[#4A1F4F] to-[#2563EB] h-full rounded-full transition-all duration-300"
                          style={{ width: `${progressPct}%` }}
                        />
                      </div>
                    </div>

                    <div className="flex justify-between items-center pt-2 border-t border-[var(--brand-border)] text-[10px] text-[var(--text-secondary)]">
                      <span className="flex items-center gap-1">
                        <Calendar size={11} /> Due: {formatDate(a.dueDate)}
                      </span>
                      <span className="font-semibold text-rose-500 bg-rose-500/5 px-2 py-0.5 rounded">
                        {pending} Pending
                      </span>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* =================================RIGHT PANEL ================================= */}
        <div className="lg:col-span-8 space-y-5">
          {!selectedAssignment ? (
            <Card className="flex flex-col items-center justify-center py-20 text-center">
              <EmptyState
                icon="file"
                title="Select an assessment"
                description="Choose an assignment or quiz from the left list to view and manage student submissions."
              />
            </Card>
          ) : (
            <>
              {/* Assignment Summary Card */}
              <Card className="bg-gradient-to-br from-white to-slate-50/50 dark:from-[#1E293B] dark:to-slate-800/20 relative overflow-hidden">
                <div className="absolute right-0 top-0 w-24 h-24 bg-gradient-to-br from-[#4A1F4F]/10 to-[#2563EB]/10 rounded-bl-full pointer-events-none" />
                <div className="flex flex-col sm:flex-row justify-between sm:items-start gap-4 mb-4 pb-4 border-b border-[var(--brand-border)]">
                  <div>
                    <h2 className="text-base font-bold text-[var(--text-primary)]">{selectedAssignment.title}</h2>
                    <div className="flex flex-wrap items-center gap-2 mt-1.5 text-xs text-[var(--text-secondary)]">
                      <span className="bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded-md font-medium">{selectedAssignment.subject}</span>
                      {selectedAssignment.topic && <span>• Topic: {selectedAssignment.topic}</span>}
                    </div>
                  </div>
                  <div className="flex gap-2 shrink-0">
                    <span className="text-xs font-bold text-[#4A1F4F] dark:text-purple-400 bg-[#F5EAF8]0/5 px-2.5 py-1 rounded-xl border border-purple-500/10 flex items-center gap-1">
                      <Award size={13} /> Max Marks: {selectedAssignment.maxMarks}
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-5">
                  {[
                    { label: 'Created Date', value: formatDate(selectedAssignment.createdAt) || '—', color: 'text-[var(--text-primary)]' },
                    { label: 'Due Date', value: formatDate(selectedAssignment.dueDate), color: 'text-rose-500 font-semibold' },
                    { label: 'Total Students', value: String(metrics.total), color: 'text-[var(--text-primary)] font-bold' },
                    { label: 'Submissions', value: `${metrics.submitted} (${metrics.total - metrics.submitted} Pending)`, color: 'text-[#2563EB] font-bold' },
                  ].map((m, idx) => (
                    <div key={idx} className="bg-white dark:bg-slate-800/40 p-3 rounded-2xl border border-[var(--brand-border)] shadow-sm">
                      <p className="text-[10px] uppercase font-bold tracking-wider text-[var(--text-secondary)] mb-1">{m.label}</p>
                      <p className={`text-xs ${m.color}`}>{m.value}</p>
                    </div>
                  ))}
                </div>

                {/* Progress bar */}
                <div className="space-y-1.5">
                  <div className="flex justify-between items-center text-xs font-semibold">
                    <span className="text-[var(--text-secondary)]">Submission Progress</span>
                    <span className="text-[#2563EB]">{metrics.pct}% Submitted</span>
                  </div>
                  <div className="w-full bg-slate-100 dark:bg-slate-800 rounded-full h-2 overflow-hidden border border-[var(--brand-border)]">
                    <div
                      className="bg-gradient-to-r from-[#4A1F4F] to-[#2563EB] h-full rounded-full transition-all duration-500"
                      style={{ width: `${metrics.pct}%` }}
                    />
                  </div>
                </div>
              </Card>

              {/* Submissions Section Header & Filters */}
              <div className="bg-white dark:bg-[#1E293B] border border-[var(--brand-border)] rounded-2xl p-4 shadow-sm space-y-4">
                <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-3">
                  <h3 className="text-sm font-bold text-[var(--text-primary)]">Student Submissions ({sortedSubmissions.length})</h3>
                  <div className="flex items-center gap-2 text-xs text-[var(--text-secondary)] font-medium">
                    <Info size={12} />
                    <span>Auto-graded quiz grades are marked reviewed instantly.</span>
                  </div>
                </div>

                <div className="flex flex-col md:flex-row gap-3">
                  {/* Search Student */}
                  <div className="relative flex-1">
                    <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
                    <input
                      type="text"
                      placeholder="Search by student name or enrollment..."
                      value={studentSearch}
                      onChange={(e) => { setStudentSearch(e.target.value); setCurrentPage(1); }}
                      className="w-full h-11 pl-10 pr-4 bg-slate-50 dark:bg-slate-800/40 border border-slate-150 dark:border-slate-700/60 rounded-xl text-xs focus:outline-none focus:border-[#4A1F4F] transition-all shadow-inner"
                    />
                  </div>

                  {/* Filter Dropdowns */}
                  <div className="flex gap-2 flex-wrap sm:flex-nowrap">
                    {/* Assessment Type Filter (Right Panel) */}
                    <div className="relative flex-1 sm:flex-none">
                      <select
                        value={rightPanelTypeFilter}
                        onChange={(e) => { setRightPanelTypeFilter(e.target.value as any); setCurrentPage(1); }}
                        className="w-full sm:w-40 pl-3 pr-8 py-2 text-xs bg-slate-50 dark:bg-slate-800 border border-[var(--brand-border)] rounded-xl text-[var(--text-primary)] cursor-pointer focus:outline-none focus:border-[#4A1F4F] appearance-none"
                      >
                        <option value="all">All Types</option>
                        <option value="assignment">Assignments Only</option>
                        <option value="quiz">Quizzes Only</option>
                      </select>
                      <ChevronDown size={12} className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-secondary)] pointer-events-none" />
                    </div>

                    <div className="relative flex-1 sm:flex-none">
                      <select
                        value={studentFilter}
                        onChange={(e) => { setStudentFilter(e.target.value as any); setCurrentPage(1); }}
                        className="w-full sm:w-44 pl-3 pr-8 py-2 text-xs bg-slate-50 dark:bg-slate-800 border border-[var(--brand-border)] rounded-xl text-[var(--text-primary)] cursor-pointer focus:outline-none focus:border-[#4A1F4F] appearance-none"
                      >
                        <option value="all">All Submissions</option>
                        <option value="submitted">Submitted (Pending Review)</option>
                        <option value="reviewed">Reviewed (Graded)</option>
                        <option value="pending">Pending Submission</option>
                        <option value="late">Late Submission</option>
                      </select>
                      <ChevronDown size={12} className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-secondary)] pointer-events-none" />
                    </div>

                    {/* Sort Dropdown */}
                    <div className="relative flex-1 sm:flex-none">
                      <select
                        value={sortBy}
                        onChange={(e) => setSortBy(e.target.value as any)}
                        className="w-full sm:w-40 pl-3 pr-8 py-2 text-xs bg-slate-50 dark:bg-slate-800 border border-[var(--brand-border)] rounded-xl text-[var(--text-primary)] cursor-pointer focus:outline-none focus:border-[#4A1F4F] appearance-none"
                      >
                        <option value="latest">Latest Submission</option>
                        <option value="oldest">Oldest Submission</option>
                        <option value="name">Name (A-Z)</option>
                        <option value="enrollment">Enrollment No</option>
                        <option value="highest">Highest Marks</option>
                        <option value="lowest">Lowest Marks</option>
                      </select>
                      <ChevronDown size={12} className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-secondary)] pointer-events-none" />
                    </div>
                  </div>
                </div>
              </div>

              {/* Submissions List / Cards */}
              <div className="space-y-4">
                {loadingSubmissions ? (
                  Array.from({ length: 3 }).map((_, i) => (
                    <div key={i} className="bg-white dark:bg-[#1E293B] border border-[var(--brand-border)] rounded-2xl p-5 space-y-3 animate-pulse">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-slate-200 dark:bg-slate-700" />
                        <div className="flex-1 space-y-2">
                          <div className="h-4 w-1/3 bg-slate-200 dark:bg-slate-700 rounded" />
                          <div className="h-3 w-1/4 bg-slate-200 dark:bg-slate-700 rounded" />
                        </div>
                      </div>
                    </div>
                  ))
                ) : paginatedSubmissions.length === 0 ? (
                  <Card className="text-center py-12">
                    <EmptyState
                      icon="inbox"
                      title="No submissions found"
                      description="No student submissions matches the search query or status filter."
                    />
                  </Card>
                ) : (
                  <div className="grid grid-cols-1 gap-4">
                    {paginatedSubmissions.map((sub) => {
                      const isPending = sub.status === 'pending';
                      const isReviewed = sub.status === 'reviewed';
                      const isLate = sub.isLate;

                      // Avatar Initials
                      const initials = sub.student?.name
                        ? sub.student.name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase()
                        : 'S';

                      return (
                        <div
                          key={sub.id}
                          className="bg-white dark:bg-[#1E293B] border border-[var(--brand-border)] rounded-2xl p-5 hover:shadow-md transition-all duration-200 relative group space-y-4"
                        >
                          {/* Top Row: Student profile & Status Badge */}
                          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 pb-3 border-b border-[var(--brand-border)]">
                            <div className="flex items-center gap-4 min-w-0">
                              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#4A1F4F] to-[#2563EB] flex items-center justify-center text-white text-xs font-black shadow-inner shrink-0">
                                {initials}
                              </div>
                              <div className="min-w-0">
                                <h4 className="text-sm font-bold text-[var(--text-primary)] truncate">
                                  {sub.student?.name}
                                </h4>
                                <p className="text-[11px] text-[var(--text-secondary)] font-mono mt-0.5">
                                  Enrollment: {sub.student?.enrollmentNumber}
                                </p>
                              </div>
                            </div>

                            <div className="flex items-center gap-2">
                              {isPending && (
                                <span className="text-[10px] font-bold px-2.5 py-1 rounded-xl bg-amber-100 dark:bg-amber-950/20 text-amber-600 dark:text-amber-400 border border-amber-200 dark:border-amber-900/30">
                                  Pending
                                </span>
                              )}
                              {!isPending && isLate && (
                                <span className="text-[10px] font-bold px-2.5 py-1 rounded-xl bg-rose-100 dark:bg-rose-950/20 text-rose-600 dark:text-rose-400 border border-rose-200 dark:border-rose-900/30">
                                  Late Submission
                                </span>
                              )}
                              {!isPending && !isReviewed && (
                                <span className="text-[10px] font-bold px-2.5 py-1 rounded-xl bg-blue-100 dark:bg-blue-950/20 text-blue-600 dark:text-blue-400 border border-blue-200 dark:border-blue-900/30">
                                  Submitted
                                </span>
                              )}
                              {!isPending && isReviewed && (
                                <span className="text-[10px] font-bold px-2.5 py-1 rounded-xl bg-emerald-100 dark:bg-emerald-950/20 text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-900/30">
                                  Reviewed
                                </span>
                              )}
                            </div>
                          </div>

                          {/* Middle Row: Full-width Details Grid */}
                          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                            {/* Submission Type */}
                            <div className="bg-slate-50 dark:bg-slate-800/40 p-2.5 rounded-xl border border-[var(--brand-border)]">
                              <span className="text-[9px] uppercase font-bold tracking-wider text-[var(--text-secondary)] block">Type</span>
                              <span className={`text-[11px] font-bold ${
                                selectedAssignment.assignmentType === 'QUIZ' ? 'text-emerald-600 dark:text-blue-400' : 'text-purple-600 dark:text-purple-400'
                              }`}>
                                {selectedAssignment.assignmentType === 'QUIZ' ? 'Quiz' : 'Assignment'}
                              </span>
                            </div>

                            {/* Title / Subject */}
                            <div className="bg-slate-50 dark:bg-slate-800/40 p-2.5 rounded-xl border border-[var(--brand-border)] min-w-0">
                              <span className="text-[9px] uppercase font-bold tracking-wider text-[var(--text-secondary)] block">Title & Subject</span>
                              <span className="text-[11px] text-[var(--text-primary)] font-medium truncate block" title={selectedAssignment.title}>
                                {selectedAssignment.title} ({selectedAssignment.subject})
                              </span>
                            </div>

                            {/* Submission date & time */}
                            <div className="bg-slate-50 dark:bg-slate-800/40 p-2.5 rounded-xl border border-[var(--brand-border)]">
                              <span className="text-[9px] uppercase font-bold tracking-wider text-[var(--text-secondary)] block">Submitted</span>
                              <span className="text-[11px] text-[var(--text-primary)] font-medium truncate block">
                                {isPending ? 'Not submitted yet' : formatDateTime(sub.submittedAt)}
                              </span>
                            </div>

                            {/* Marks / Score */}
                            <div className="bg-slate-50 dark:bg-slate-800/40 p-2.5 rounded-xl border border-[var(--brand-border)]">
                              <span className="text-[9px] uppercase font-bold tracking-wider text-[var(--text-secondary)] block">Marks</span>
                              <span className="text-[11px] font-bold text-[#4A1F4F] dark:text-purple-300">
                                {sub.marks !== null && sub.marks !== undefined ? `${sub.marks} / ${selectedAssignment.maxMarks}` : 'Not Graded'}
                              </span>
                            </div>
                          </div>

                          {/* Remarks display if exists */}
                          {!isPending && (sub.feedback || isReviewed) && (
                            <div className="p-2.5 bg-slate-50 dark:bg-slate-800/30 border border-slate-100 dark:border-slate-800 rounded-xl text-xs">
                              <span className="text-[9px] uppercase font-bold tracking-wider text-[var(--text-secondary)] block mb-0.5">Remarks</span>
                              <p className="text-[var(--text-primary)] italic">
                                {sub.feedback || 'No comments written.'}
                              </p>
                            </div>
                          )}

                          {/* Action Buttons Row */}
                          <div className="flex justify-between items-center pt-2 border-t border-[var(--brand-border)]">
                            <div className="text-[10px] text-[var(--text-secondary)] font-medium">
                              <span>Batch: {sub.student?.batchName || 'General Class'}</span>
                            </div>
                            
                            <div className="flex items-center gap-2">
                              {!isPending && sub.fileName === 'QUIZ_SUBMISSION' && (
                                <button
                                  type="button"
                                  onClick={() => setViewingQuizSub(sub)}
                                  className="p-2 bg-slate-50 dark:bg-slate-800 hover:bg-[#4A1F4F10] border border-[var(--brand-border)] text-[var(--text-secondary)] hover:text-[#4A1F4F] rounded-xl transition-all cursor-pointer shadow-sm flex items-center gap-1 text-[11px] font-bold"
                                  title="View Quiz Answers Review"
                                >
                                  <Eye size={13} />
                                  <span>View Answers</span>
                                </button>
                              )}
                              {!isPending && sub.fileName !== 'QUIZ_SUBMISSION' && (
                                <>
                                  <a
                                    href={sub.uploadedFile}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="p-2 bg-slate-50 dark:bg-slate-800 hover:bg-[#2563EB10] border border-[var(--brand-border)] text-[var(--text-secondary)] hover:text-[#2563EB] rounded-xl transition-all cursor-pointer shadow-sm"
                                    title="View File"
                                  >
                                    <Eye size={14} />
                                  </a>
                                  <a
                                    href={sub.uploadedFile}
                                    download
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="p-2 bg-slate-50 dark:bg-slate-800 hover:bg-[#2563EB10] border border-[var(--brand-border)] text-[var(--text-secondary)] hover:text-[#2563EB] rounded-xl transition-all cursor-pointer shadow-sm"
                                    title="Download File"
                                  >
                                    <Download size={14} />
                                  </a>
                                </>
                              )}
                              <Button
                                variant={isReviewed ? 'outline' : 'primary'}
                                size="sm"
                                onClick={() => openGradeModal(sub)}
                                icon={isReviewed ? <CheckCircle2 size={13} /> : <Star size={13} />}
                                disabled={isPending}
                              >
                                {isPending ? 'Awaiting Submit' : isReviewed ? 'Edit Marks' : 'Grade'}
                              </Button>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Pagination controls */}
              {!loadingSubmissions && sortedSubmissions.length > 0 && (
                <div className="bg-white dark:bg-[#1E293B] border border-[var(--brand-border)] rounded-2xl p-4 shadow-sm flex flex-col sm:flex-row items-center justify-between gap-3 select-none">
                  <span className="text-xs text-[var(--text-secondary)] font-medium">
                    Showing {Math.min(sortedSubmissions.length, (currentPage - 1) * pageSize + 1)}-
                    {Math.min(sortedSubmissions.length, currentPage * pageSize)} of {sortedSubmissions.length} submissions.
                  </span>

                  <div className="flex items-center gap-1">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={currentPage === 1}
                      onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))}
                    >
                      Previous
                    </Button>
                    {Array.from({ length: totalPages }).map((_, i) => {
                      const pageNum = i + 1;
                      return (
                        <button
                          key={pageNum}
                          onClick={() => setCurrentPage(pageNum)}
                          className={`w-7 h-7 text-xs font-semibold rounded-lg border transition-all cursor-pointer ${
                            currentPage === pageNum
                              ? 'bg-[#4A1F4F] text-white border-transparent shadow-sm'
                              : 'bg-white dark:bg-slate-800 border-[var(--brand-border)] text-[var(--text-secondary)] hover:bg-slate-50'
                          }`}
                        >
                          {pageNum}
                        </button>
                      );
                    })}
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={currentPage === totalPages}
                      onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages))}
                    >
                      Next
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Grade Submission Modal */}
      <Modal
        isOpen={!!gradeModal}
        onClose={() => setGradeModal(null)}
        title="Grade Submission"
        size="md"
        footer={
          <>
            <Button variant="ghost" onClick={() => setGradeModal(null)}>Cancel</Button>
            <Button variant="primary" loading={saving} onClick={handleGrade}>Save Review</Button>
          </>
        }
      >
        {gradeModal && (
          <div className="space-y-4">
            <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-800/50 border border-[var(--brand-border)]">
              <p className="text-[10px] uppercase font-bold tracking-wider text-[var(--text-secondary)]">Student</p>
              <p className="text-sm font-bold text-[var(--text-primary)] mt-0.5">{gradeModal.student?.name}</p>
              <p className="text-xs text-[var(--text-secondary)] font-mono">{gradeModal.student?.enrollmentNumber}</p>
            </div>
            
            <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-slate-800/50 border border-[var(--brand-border)]">
              <p className="text-[10px] uppercase font-bold tracking-wider text-[var(--text-secondary)] mb-1">Submitted File</p>
              {gradeModal.fileName === 'QUIZ_SUBMISSION' ? (
                <span className="text-xs font-semibold text-[#4A1F4F] dark:text-purple-400 bg-[#F5EAF8] dark:bg-purple-950/20 px-2 py-0.5 rounded">Online Auto-Graded Quiz</span>
              ) : gradeModal.fileName ? (
                <a
                  href={gradeModal.uploadedFile}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-2 text-sm text-[#2563EB] hover:underline font-semibold"
                >
                  <span>{getFileIcon(gradeModal.fileName)}</span>
                  {gradeModal.fileName}
                  <Download size={13} />
                </a>
              ) : (
                <span className="text-xs text-[var(--text-secondary)] font-medium">No submission file (Pending)</span>
              )}
            </div>

            <Input
              label={`Marks (out of ${selectedAssignment?.maxMarks})`}
              type="number"
              min={0}
              max={selectedAssignment?.maxMarks}
              value={marks}
              onChange={(e) => setMarks(e.target.value)}
              placeholder="Enter marks"
              required
            />
            <Textarea
              label="Feedback Remarks (Optional)"
              value={feedback}
              onChange={(e) => setFeedback(e.target.value)}
              placeholder="Write your feedback for the student..."
              rows={4}
            />
          </div>
        )}
      </Modal>

      {/* Quiz Submission Review Modal */}
      <Modal
        isOpen={!!viewingQuizSub}
        onClose={() => setViewingQuizSub(null)}
        title={`Quiz Submission Review — ${viewingQuizSub?.student?.name}`}
        size="lg"
        footer={<Button variant="primary" onClick={() => setViewingQuizSub(null)}>Close</Button>}
      >
        {viewingQuizSub && selectedAssignment && (
          <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-1">
            <div className="flex gap-6 p-4 bg-slate-50 dark:bg-slate-800/40 border border-[var(--brand-border)] rounded-2xl shadow-inner">
              <div>
                <p className="text-[10px] uppercase font-bold tracking-wider text-[var(--text-secondary)] mb-0.5">Score Awarded</p>
                <p className="text-xl font-black text-emerald-500">
                  {viewingQuizSub.marks} <span className="text-xs font-normal text-[var(--text-secondary)]">/ {selectedAssignment.maxMarks}</span>
                </p>
              </div>
              <div className="h-8 border-r border-[var(--brand-border)]" />
              <div>
                <p className="text-[10px] uppercase font-bold tracking-wider text-[var(--text-secondary)] mb-0.5">Accuracy</p>
                <p className="text-xl font-black text-[#2563EB]">
                  {Math.round(((viewingQuizSub.marks || 0) / (selectedAssignment.maxMarks || 1)) * 100)}%
                </p>
              </div>
            </div>

            <div className="space-y-3">
              {selectedAssignment.questions?.map((q, idx) => {
                const selected = submittedAnswersMap[q.id!];
                const isCorrect = String(selected).trim().toLowerCase() === String(q.correctAnswer).trim().toLowerCase();
                return (
                  <div
                    key={q.id}
                    className={`p-4 bg-white dark:bg-slate-900 border-[var(--brand-border)] rounded-2xl border-l-4 shadow-sm ${
                      isCorrect ? 'border-l-emerald-500' : 'border-l-red-500'
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      <span className={`flex items-center justify-center w-6 h-6 rounded-lg text-xs font-bold shrink-0 ${
                        isCorrect ? 'bg-emerald-500/10 text-emerald-600' : 'bg-[#F5EAF8]0/10 text-red-600'
                      }`}>
                        {idx + 1}
                      </span>
                      <div className="flex-1 min-w-0">
                        <div className="flex justify-between items-start gap-2 mb-2">
                          <p className="text-xs font-bold text-[var(--text-primary)] leading-normal">{q.questionText}</p>
                          <span className={`text-[9px] font-extrabold px-2 py-0.5 rounded shrink-0 ${
                            isCorrect ? 'bg-emerald-100 text-emerald-600' : 'bg-red-100 text-red-600'
                          }`}>
                            {isCorrect ? `+${q.marks} Marks` : '0 Marks'}
                          </span>
                        </div>

                        {q.questionType !== 'SHORT_ANSWER' ? (
                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-3">
                            {[
                              { key: 'A', value: q.optionA },
                              { key: 'B', value: q.optionB },
                              ...(q.optionC ? [{ key: 'C', value: q.optionC }] : []),
                              ...(q.optionD ? [{ key: 'D', value: q.optionD }] : []),
                            ].map((opt) => {
                              const isSelected = selected === opt.key;
                              const isCorrectAnswer = q.correctAnswer === opt.key;

                              let optStyle = 'border-[var(--brand-border)] text-[var(--text-secondary)]';
                              if (isSelected) {
                                optStyle = isCorrect
                                  ? 'bg-emerald-500/10 border-emerald-500 text-emerald-700 font-semibold'
                                  : 'bg-[#F5EAF8]0/10 border-red-500 text-red-700 font-semibold';
                              } else if (isCorrectAnswer) {
                                optStyle = 'bg-emerald-500/5 border-emerald-500/30 text-emerald-600 font-medium';
                              }

                              return (
                                <div key={opt.key} className={`flex items-center gap-2 p-2 border rounded-xl text-[11px] ${optStyle}`}>
                                  <span className={`w-4.5 h-4.5 rounded-full flex items-center justify-center text-[9px] border shrink-0 ${
                                    isSelected
                                      ? isCorrect ? 'bg-emerald-500 border-transparent text-white' : 'bg-[#F5EAF8]0 border-transparent text-white'
                                      : isCorrectAnswer ? 'bg-emerald-500/20 border-transparent text-emerald-600' : 'border-[var(--brand-border)] text-[var(--text-secondary)]'
                                  }`}>
                                    {opt.key}
                                  </span>
                                  <span className="truncate">{opt.value}</span>
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <div className="space-y-1 mt-2 text-xs">
                            <p className="text-[var(--text-secondary)]">
                              Student Answer: <span className={`font-semibold ${isCorrect ? 'text-emerald-500' : 'text-red-500'}`}>{selected || '(No Answer)'}</span>
                            </p>
                            {!isCorrect && (
                              <p className="text-[11px] text-[var(--text-secondary)]">
                                Correct Answer: <span className="font-semibold text-emerald-500">{q.correctAnswer}</span>
                              </p>
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </Modal>
    </Layout>
  );
};
