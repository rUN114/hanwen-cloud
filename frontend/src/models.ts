export type Role = "ADMIN" | "TEACHER" | "STUDENT";
export interface User {
  id: string;
  username: string;
  displayName: string;
  role: Role;
  status: "ENABLED" | "DISABLED";
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
export interface Course {
  accessRole?: "OWNER" | "CO_TEACHER" | "ASSISTANT" | "NONE";
  teacherName?: string;
  id: string;
  name: string;
  summary: string | null;
  category: string;
  teacherId: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  createdAt: string;
  updatedAt: string;
}
export interface Chapter {
  lessonDocument?: string | null;
  id: string;
  courseId: string;
  title: string;
  sortNo: number;
  content?: string;
}
export interface Enrollment {
  id: string;
  courseId: string;
  studentId: string;
  joinedAt: string;
  courseName: string;
  courseStatus: string;
}
export interface Progress {
  chapterId: string;
  completed: boolean;
  completedAt: string;
}
export interface ProgressSummary {
  total: number;
  completedCount: number;
  percent: string;
  items: Progress[];
}
export interface Rubric { name: string; maxScore: string }
export interface Task {
  rubric: Rubric[];
  id: string;
  courseId: string;
  teacherId: string;
  title: string;
  description: string;
  maxScore: string;
  deadlineAt: string | null;
  status: "DRAFT" | "PUBLISHED" | "CLOSED";
  updatedAt: string;
}
export interface Grade {
  rubricScores?: string[] | null;
  submissionId: string;
  teacherId: string;
  score: string;
  comment: string | null;
  gradedAt: string;
  submissionVersion: number;
}
export interface Submission {
  rubric?: Rubric[];
  courseId?: string;
  id: string;
  assignmentId: string;
  studentId: string;
  studentName?: string;
  status: "SUBMITTED" | "GRADED";
  submittedAt: string;
  version: number;
  answerText?: string;
  taskTitle?: string;
  maxScore?: string;
  grade?: Grade | null;
}
export interface GradeBrief {
  submissionId: string;
  assignmentId: string;
  courseId: string;
  taskTitle: string;
  maxScore: string;
  status: string;
  score: string | null;
  comment: string | null;
  gradedAt: string | null;
}

export interface Profile extends User {
  email: string | null;
  biography: string | null;
}
export interface Notice {
  id: string;
  courseId: string;
  title: string;
  content: string;
  pinned: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}
export interface Question {
  id: string;
  courseId: string;
  chapterId: string | null;
  studentId: string;
  displayName: string | null;
  title: string;
  content: string;
  resolved: boolean;
  version: number;
  replyCount: number;
  createdAt: string;
}
export interface Reply {
  id: string;
  authorId: string;
  displayName: string | null;
  role: Role;
  content: string;
  createdAt: string;
}
export interface Note {
  id: string;
  enrollmentId: string;
  chapterId: string;
  title: string;
  content: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}
export interface Allusion {term:string;explanation:string;workId:string|null;sourceUrl:string|null}
export interface Work {
  translation?:string|null;
  annotationsText?:string|null;
  allusions?:Allusion[];
  id: string;
  teacherId: string | null;
  title: string;
  author: string;
  dynasty: string;
  genre: string;
  status: string;
  version: number;
  content?: string;
  readingGuide?: string | null;
  sourceUrl?: string | null;
  sourceNote?: string | null;
}
export interface Favorite {
  workId: string;
  title: string;
  author: string;
  dynasty: string;
  genre: string;
  status: string;
  createdAt: string;
}
export interface Learner {
  studentId: string;
  displayName: string;
  enrollmentId: string;
  joinedAt: string;
  completed: number;
  chapters: number;
  percent: string;
  noteCount: number;
}
export interface AssignmentStats {
  tasks: {
    id: string;
    title: string;
    maxScore: string;
    submitted: number;
    graded: number;
    averageScore?: string;
    status: string;
    deadlineAt?: string;
  }[];
  learners: {
    studentId: string;
    submitted: number;
    graded: number;
    averagePercent?: string;
  }[];
}

export interface Classroom {
  canEdit?:boolean;
  id: string;
  courseId: string;
  courseName: string;
  courseStatus: string;
  teacherId: string;
  name: string;
  semester: string;
  description: string | null;
  joinCode?: string;
  status: "ACTIVE" | "CLOSED";
  version: number;
  memberCount: number;
  enrollmentId?: string;
  createdAt: string;
}
export interface ClassMember {
  id: string;
  studentId: string;
  displayName: string;
  enrollmentId: string;
  joinedAt: string;
}
export interface ResourceFile {
  id: string;
  courseId: string;
  chapterId: string | null;
  title: string;
  description: string | null;
  originalName: string;
  mimeType: string;
  bytes: number;
  sha256: string;
  createdAt: string;
}
export interface Annotation {
  id: string;
  enrollmentId: string;
  chapterId: string;
  startOffset: number;
  endOffset: number;
  quoteText: string;
  comment: string;
  color: "RED" | "PINE" | "GOLD";
  anchorValid: boolean;
  createdAt: string;
}
export interface QuizQuestion {
  id: string;
  prompt: string;
  optionA: string;
  optionB: string;
  optionC: string;
  optionD: string;
  correctOption?: string;
  explanation?: string;
  sortNo: number;
  questionType: "SINGLE" | "MULTIPLE" | "TRUE_FALSE" | "FILL_BLANK" | "SHORT_ANSWER";
  knowledgePoint?: string | null;
  points: number;
  bankId?: string;
  bankVersion?: number;
}
export interface Quiz {
  id: string;
  courseId: string;
  chapterId: string;
  title: string;
  instructions: string | null;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  version: number;
  questionCount: number;
  questions?: QuizQuestion[];
  opensAt: string | null;
  closesAt: string | null;
  timeLimitMinutes: number;
  maxAttempts: number;
  totalPoints: number;
}
export interface AttemptItem {
  questionId: string;
  prompt: string;
  optionA: string;
  optionB: string;
  optionC: string;
  optionD: string;
  selectedOption: string;
  correctOption?: string;
  explanation: string | null;
  correct?: boolean | null;
  knowledgePoint?: string | null;
  awardedPoints?: number | null;
  teacherComment?: string | null;
  questionType: QuizQuestion["questionType"];
  points: number;
}
export interface QuizAttempt {
  id: string;
  quizId: string;
  courseId: string;
  chapterId: string;
  score: number | null;
  gradingStatus: "PENDING" | "GRADED";
  gradingVersion: number;
  gradedAt?: string | null;
  total: number;
  completedAt: string;
  items: AttemptItem[];
  attemptNo: number;
  quizVersion: number | null;
  answersReleased: boolean;
  status: "SUBMITTED";
  runId: string | null;
}
export interface BankQuestion extends QuizQuestion {
  topic: string | null;
  status: "ACTIVE" | "ARCHIVED";
  version: number;
}
export interface QuizRun {
  id: string;
  status: "ACTIVE" | "EXPIRED" | "SUBMITTED";
  version: number;
  attemptNo: number;
  quizVersion: number;
  startedAt: string;
  deadlineAt: string | null;
  serverTime: string;
  questions?: (QuizQuestion & {selectedOption?: string})[];
}
export interface PracticeState {
  usedAttempts: number;
  maxAttempts: number;
  serverTime: string;
  active: QuizRun | null;
  lastRun: QuizRun | null;
  answersReleased: boolean;
  history: QuizAttempt[];
}
export interface GradeRevision {
  rubricScores?: string[] | null;
  id: string;
  submissionVersion: number;
  teacherName: string;
  score: string;
  comment: string | null;
  gradedAt: string;
}
export interface AnswerDraft {
  answerText: string;
  version: number;
  updatedAt: string;
}
export interface InboxEvent {
  eventKey: string;
  kind: string;
  title: string;
  courseName?: string;
  eventAt: string;
  url: string;
}
export interface AgendaTask extends Task {
  submissionStatus?: string | null;
  submissionId?: string | null;
}
