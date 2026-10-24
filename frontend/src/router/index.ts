import { createRouter, createWebHistory } from "vue-router";
import { session, home } from "../stores/session";
const routes = [
  { path: "/paintings", component: () => import("../views/library/PaintingGallery.vue") },
  {
    path: "/teacher/practice-queue",
    component: () => import("../views/assessment/PracticeQueue.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/practice-attempts/:id",
    component: () => import("../views/assessment/PracticeMark.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/team",
    component: () => import("../views/courses/CourseTeam.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/student/review-book",
    component: () => import("../views/learning/ReviewBook.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/teacher/review",
    component: () => import("../views/assignments/ReviewDesk.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/courses/:courseId/gradebook",
    component: () => import("../views/reports/Gradebook.vue"),
    meta: { roles: ["TEACHER", "STUDENT"] },
  },
  {
    path: "/teacher/question-bank",
    component: () => import("../views/assessment/QuestionBank.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/admin/users/import",
    component: () => import("../views/admin/UserImport.vue"),
    meta: { role: "ADMIN" },
  },
  {
    path: "/admin/audit",
    component: () => import("../views/admin/Audit.vue"),
    meta: { role: "ADMIN" },
  },
  {
    path: "/classes",
    component: () => import("../views/community/Classes.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },
  {
    path: "/classes/:id",
    component: () => import("../views/community/ClassDetail.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },
  {
    path: "/courses/:courseId/resources",
    component: () => import("../views/courses/Resources.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },
  {
    path: "/courses/:courseId/quizzes",
    component: () => import("../views/assessment/Quizzes.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },
  {
    path: "/teacher/quizzes/:id",
    component: () => import("../views/assessment/QuizEditor.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/practice/:id",
    component: () => import("../views/assessment/Practice.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/inbox",
    component: () => import("../views/community/Inbox.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },
  {
    path: "/agenda",
    component: () => import("../views/dashboard/Agenda.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },

  { path: "/home", component: () => import("../views/dashboard/Home.vue") },
  { path: "/library", component: () => import("../views/library/Library.vue") },
  {
    path: "/library/:id",
    component: () => import("../views/library/LiteratureDetail.vue"),
  },
  {
    path: "/teacher/literature",
    component: () => import("../views/library/Library.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/literature/new",
    component: () => import("../views/library/LiteratureEditor.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/literature/:id/edit",
    component: () => import("../views/library/LiteratureEditor.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/student/notes",
    component: () => import("../views/learning/Notes.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/favorites",
    component: () => import("../views/learning/Favorites.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/courses/:courseId/community",
    component: () => import("../views/community/Community.vue"),
  },
  {
    path: "/questions/:id",
    component: () => import("../views/assessment/QuestionDetail.vue"),
  },
  {
    path: "/teacher/courses/:courseId/analytics",
    component: () => import("../views/reports/Analytics.vue"),
    meta: { role: "TEACHER" },
  },

  {
    path: "/",
    redirect: () => (session.value ? home() : "/login"),
  },
  { path: "/login", component: () => import("../views/account/Login.vue") },
  { path: "/account", component: () => import("../views/account/Account.vue") },
  {
    path: "/admin/users",
    component: () => import("../views/admin/Users.vue"),
    meta: { role: "ADMIN" },
  },
  {
    path: "/admin/users/new",
    component: () => import("../views/admin/UserForm.vue"),
    meta: { role: "ADMIN" },
  },
  {
    path: "/teacher/courses",
    component: () => import("../views/courses/Courses.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/new",
    component: () => import("../views/courses/CourseForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/edit",
    component: () => import("../views/courses/CourseForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/chapters",
    component: () => import("../views/courses/Chapters.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/chapters/new",
    component: () => import("../views/courses/ChapterForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/chapters/:chapterId/edit",
    component: () => import("../views/courses/ChapterForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/assignments",
    component: () => import("../views/assignments/Tasks.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/assignments/new",
    component: () => import("../views/assignments/TaskForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/assignments/:assignmentId/edit",
    component: () => import("../views/assignments/TaskForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/assignments/:assignmentId/submissions",
    component: () => import("../views/assignments/Submissions.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/submissions/:submissionId/grade",
    component: () => import("../views/assignments/Grade.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/student/courses",
    component: () => import("../views/courses/Courses.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/courses/:courseId",
    component: () => import("../views/courses/CourseDetail.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/enrollments",
    component: () => import("../views/courses/Enrollments.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/enrollments/:enrollmentId/chapters/:chapterId",
    component: () => import("../views/learning/Reading.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/enrollments/:enrollmentId/assignments",
    component: () => import("../views/assignments/Tasks.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/assignments/:assignmentId/submit",
    component: () => import("../views/assessment/Answer.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/grades",
    component: () => import("../views/reports/Grades.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/submissions/:submissionId",
    component: () => import("../views/assignments/Feedback.vue"),
    meta: { role: "STUDENT" },
  },
  { path: "/:pathMatch(.*)*", component: () => import("../views/system/NotFound.vue") },
];
const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});
router.beforeEach((to) => {
  if (to.path === "/login") return true;
  if (!session.value) return { path: "/login", query: { next: to.fullPath } };
  if (to.meta.role && to.meta.role !== session.value.user.role) return home();
  if (to.meta.roles && !(to.meta.roles as string[]).includes(session.value.user.role))
    return home();
  return true;
});
router.afterEach((to) => {
  document.title = (to.path === "/login" ? "登录" : "汉文学") + " · 锦卷书院";
});
export default router;
