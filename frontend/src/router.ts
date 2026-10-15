import { createRouter, createWebHistory } from "vue-router";
import { session, home } from "./session";
const routes = [
  { path: '/paintings', component: () => import('./views/PaintingGallery.vue') },
  {path:'/teacher/practice-queue',component:()=>import('./views/PracticeQueue.vue'),meta:{role:'TEACHER'}},
  {path:'/teacher/practice-attempts/:id',component:()=>import('./views/PracticeMark.vue'),meta:{role:'TEACHER'}},
  {path:'/teacher/courses/:courseId/team',component:()=>import('./views/CourseTeam.vue'),meta:{role:'TEACHER'}},
  {path:'/student/review-book',component:()=>import('./views/ReviewBook.vue'),meta:{role:'STUDENT'}},
  {path:'/teacher/review',component:()=>import('./views/ReviewDesk.vue'),meta:{role:'TEACHER'}},
  {path:'/courses/:courseId/gradebook',component:()=>import('./views/Gradebook.vue'),meta:{roles:['TEACHER','STUDENT']}},
  {path:'/teacher/question-bank',component:()=>import('./views/QuestionBank.vue'),meta:{role:'TEACHER'}},
  {
    path: "/admin/users/import",
    component: () => import("./views/UserImport.vue"),
    meta: { role: "ADMIN" },
  },
  {
    path: "/admin/audit",
    component: () => import("./views/Audit.vue"),
    meta: { role: "ADMIN" },
  },
  {
    path: "/classes",
    component: () => import("./views/Classes.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },
  {
    path: "/classes/:id",
    component: () => import("./views/ClassDetail.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },
  {
    path: "/courses/:courseId/resources",
    component: () => import("./views/Resources.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },
  {
    path: "/courses/:courseId/quizzes",
    component: () => import("./views/Quizzes.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },
  {
    path: "/teacher/quizzes/:id",
    component: () => import("./views/QuizEditor.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/practice/:id",
    component: () => import("./views/Practice.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/inbox",
    component: () => import("./views/Inbox.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },
  {
    path: "/agenda",
    component: () => import("./views/Agenda.vue"),
    meta: { roles: ["STUDENT", "TEACHER"] },
  },

  { path: "/home", component: () => import("./views/Home.vue") },
  { path: "/library", component: () => import("./views/Library.vue") },
  {
    path: "/library/:id",
    component: () => import("./views/LiteratureDetail.vue"),
  },
  {
    path: "/teacher/literature",
    component: () => import("./views/Library.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/literature/new",
    component: () => import("./views/LiteratureEditor.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/literature/:id/edit",
    component: () => import("./views/LiteratureEditor.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/student/notes",
    component: () => import("./views/Notes.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/favorites",
    component: () => import("./views/Favorites.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/courses/:courseId/community",
    component: () => import("./views/Community.vue"),
  },
  {
    path: "/questions/:id",
    component: () => import("./views/QuestionDetail.vue"),
  },
  {
    path: "/teacher/courses/:courseId/analytics",
    component: () => import("./views/Analytics.vue"),
    meta: { role: "TEACHER" },
  },

  {
    path: "/",
    redirect: () => (session.value ? home(session.value.user.role) : "/login"),
  },
  { path: "/login", component: () => import("./views/Login.vue") },
  { path: "/account", component: () => import("./views/Account.vue") },
  {
    path: "/admin/users",
    component: () => import("./views/Users.vue"),
    meta: { role: "ADMIN" },
  },
  {
    path: "/admin/users/new",
    component: () => import("./views/UserForm.vue"),
    meta: { role: "ADMIN" },
  },
  {
    path: "/teacher/courses",
    component: () => import("./views/Courses.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/new",
    component: () => import("./views/CourseForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/edit",
    component: () => import("./views/CourseForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/chapters",
    component: () => import("./views/Chapters.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/chapters/new",
    component: () => import("./views/ChapterForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/chapters/:chapterId/edit",
    component: () => import("./views/ChapterForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/assignments",
    component: () => import("./views/Tasks.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/courses/:courseId/assignments/new",
    component: () => import("./views/TaskForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/assignments/:assignmentId/edit",
    component: () => import("./views/TaskForm.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/assignments/:assignmentId/submissions",
    component: () => import("./views/Submissions.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/teacher/submissions/:submissionId/grade",
    component: () => import("./views/Grade.vue"),
    meta: { role: "TEACHER" },
  },
  {
    path: "/student/courses",
    component: () => import("./views/Courses.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/courses/:courseId",
    component: () => import("./views/CourseDetail.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/enrollments",
    component: () => import("./views/Enrollments.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/enrollments/:enrollmentId/chapters/:chapterId",
    component: () => import("./views/Reading.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/enrollments/:enrollmentId/assignments",
    component: () => import("./views/Tasks.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/assignments/:assignmentId/submit",
    component: () => import("./views/Answer.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/grades",
    component: () => import("./views/Grades.vue"),
    meta: { role: "STUDENT" },
  },
  {
    path: "/student/submissions/:submissionId",
    component: () => import("./views/Feedback.vue"),
    meta: { role: "STUDENT" },
  },
  { path: "/:pathMatch(.*)*", component: () => import("./views/NotFound.vue") },
];
const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});
router.beforeEach((to) => {
  if (to.path === "/login") return true;
  if (!session.value) return { path: "/login", query: { next: to.fullPath } };
  if (to.meta.role && to.meta.role !== session.value.user.role)
    return home(session.value.user.role);
  if (
    to.meta.roles &&
    !(to.meta.roles as string[]).includes(session.value.user.role)
  )
    return home(session.value.user.role);
  return true;
});
router.afterEach((to) => {
  document.title = (to.path === "/login" ? "登录" : "汉文学") + " · 锦卷书院";
});
export default router;
