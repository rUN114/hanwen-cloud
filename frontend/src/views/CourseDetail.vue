<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { api, ApiError } from '../api';
import { useTask, time } from '../useTask';
import { nextChapter, orderTasks, taskState, workspacePages } from '../course-workspace';
import type { Course, Enrollment, Chapter, GradeBrief, Notice, Page, ProgressSummary, Quiz, Task } from '../models';
import PageHeading from '../components/PageHeading.vue';
import StatePanel from '../components/StatePanel.vue';
import StatusTag from '../components/StatusTag.vue';
import CourseLinks from '../components/CourseLinks.vue';

const route = useRoute(), id = computed(() => String(route.params.courseId));
const course = ref<Course | null>(null), enrollment = ref<Enrollment | null>(null), chapters = ref<Chapter[]>([]);
const progress = ref<ProgressSummary | null>(null), tasks = ref<Task[] | null>(null), grades = ref<GradeBrief[] | null>(null);
const quizzes = ref<Quiz[] | null>(null), notices = ref<Page<Notice> | null>(null), busy = ref(false), error = ref<ApiError | null>(null);
const now = ref(Date.now());
type Block = 'progress' | 'tasks' | 'grades' | 'quizzes' | 'notices';
const blocks = reactive<Record<Block, { busy: boolean; error: string }>>({
  progress: { busy: false, error: '' }, tasks: { busy: false, error: '' }, grades: { busy: false, error: '' },
  quizzes: { busy: false, error: '' }, notices: { busy: false, error: '' },
});
const joinAction = useTask();
let generation = 0, timer: ReturnType<typeof setInterval> | undefined;
const next = computed(() => nextChapter(chapters.value, progress.value));
const completed = computed(() => new Set(progress.value?.items.filter(p => p.completed).map(p => p.chapterId) || []));
const sortedTasks = computed(() => orderTasks(tasks.value || [], grades.value, course.value?.status || '', now.value));
const pending = computed(() => tasks.value !== null && grades.value !== null
  ? sortedTasks.value.filter(t => state(t).writable) : null);
const urgent = computed(() => pending.value?.find(t => t.deadlineAt && new Date(t.deadlineAt).getTime() <= now.value + 86400000));
const cover = computed(() => ['bamboo', 'plum', 'lotus', 'mountain'][Number(BigInt(course.value?.id || '0') % 4n)]);
function gradeFor(t: Task) { return grades.value?.find(g => g.assignmentId === t.id); }
function state(t: Task) { return taskState(t, gradeFor(t), grades.value !== null, course.value?.status || '', now.value); }
function target(t: Task) {
  const g = gradeFor(t);
  return g ? '/student/submissions/' + g.submissionId : '/student/assignments/' + t.id + '/submit';
}
function chapterTarget(chapter: Chapter) { return '/student/enrollments/' + enrollment.value!.id + '/chapters/' + chapter.id; }
function practiceCount(chapter: Chapter) { return quizzes.value?.filter(q => q.chapterId === chapter.id).length; }
function asError(e: unknown) { return e instanceof ApiError ? e : new ApiError(503, 'WORKSPACE_LOAD_FAILED', e instanceof Error ? e.message : '暂时无法读取，请重试'); }

async function loadBlock(name: Block, version = generation) {
  if (!enrollment.value || blocks[name].busy) return;
  const courseId = id.value, enrollmentId = enrollment.value.id;
  blocks[name] = { busy: true, error: '' };
  try {
    let data: unknown;
    if (name === 'progress') data = await api<ProgressSummary>('/api/enrollments/' + enrollmentId + '/progress');
    else if (name === 'tasks') data = await workspacePages<Task>('/api/courses/' + courseId + '/assignments', api);
    else if (name === 'grades') data = await workspacePages<GradeBrief>('/api/grades/me?courseId=' + courseId, api);
    else if (name === 'quizzes') data = await workspacePages<Quiz>('/api/courses/' + courseId + '/quizzes', api);
    else data = await api<Page<Notice>>('/api/courses/' + courseId + '/notices?size=3');
    if (generation !== version) return;
    if (name === 'progress') progress.value = data as ProgressSummary;
    else if (name === 'tasks') tasks.value = data as Task[];
    else if (name === 'grades') grades.value = data as GradeBrief[];
    else if (name === 'quizzes') quizzes.value = data as Quiz[];
    else notices.value = data as Page<Notice>;
  } catch (e) {
    if (generation === version) blocks[name].error = asError(e).message;
  } finally {
    if (generation === version) blocks[name].busy = false;
  }
}
async function load() {
  const version = ++generation, courseId = id.value;
  busy.value = true; error.value = null;
  course.value = null; enrollment.value = null; chapters.value = [];
  progress.value = null; tasks.value = null; grades.value = null; quizzes.value = null; notices.value = null;
  for (const name of Object.keys(blocks) as Block[]) blocks[name] = { busy: false, error: '' };
  try {
    const [c, e] = await Promise.all([api<Course>('/api/courses/' + courseId), api<Page<Enrollment>>('/api/enrollments/me?courseId=' + courseId)]);
    if (generation !== version) return;
    course.value = c; enrollment.value = e.items[0] || null;
    if (enrollment.value) {
      const contents = await api<Chapter[]>('/api/courses/' + courseId + '/chapters');
      if (generation !== version) return;
      chapters.value = contents;
      await Promise.all((Object.keys(blocks) as Block[]).map(name => loadBlock(name, version)));
    }
  } catch (e) { if (generation === version) error.value = asError(e); }
  finally { if (generation === version) busy.value = false; }
}
function join() { joinAction.run(async () => { await api<Enrollment>('/api/enrollments', { method: 'POST', body: { courseId: id.value } }); await load(); }); }
watch(id, load, { immediate: true });
onMounted(() => { timer = setInterval(() => { now.value = Date.now(); }, 15000); window.addEventListener('hw:session-resumed', load); });
onUnmounted(() => { generation++; clearInterval(timer); window.removeEventListener('hw:session-resumed', load); });
</script>

<template>
  <PageHeading :title="course?.name || '课程书案'" back="/student/enrollments" description="在同一张书案上，读文章、做练习、写习作。" />
  <StatePanel :busy="busy && !course" :error="error" @retry="load" />
  <template v-if="course && !error">
    <CourseLinks v-if="enrollment" :course-id="id" active="workspace" />
    <section class="workspace-book" :class="'workspace-' + cover">
      <div class="workspace-spine" aria-hidden="true">锦卷书院</div>
      <div class="workspace-opening">
        <div class="workspace-meta"><StatusTag :status="course.status" /><span>{{ course.category }} · {{ course.teacherName || '授课教师' }}</span></div>
        <h2>{{ course.name }}</h2>
        <p class="workspace-intro">{{ course.summary || '教师尚未填写课程简介。' }}</p>
        <template v-if="enrollment">
          <p v-if="progress" class="workspace-progress-text">章节完成记录 <strong>{{ progress.completedCount }} / {{ progress.total }}</strong></p>
          <el-progress v-if="progress" :percentage="Number(progress.percent)" :stroke-width="7" :show-text="false" color="#b6d1b4" />
          <p class="workspace-progress-note">完成记录由你确认；练习和习作分别记录，不代表已掌握程度。</p>
        </template>
        <template v-else>
          <p>加入课程后，即可展开章节、练习与习作。</p>
          <el-button v-if="course.status === 'PUBLISHED'" :loading="joinAction.busy.value" @click="join">加入课程</el-button>
          <StatePanel :error="joinAction.error.value" @retry="join" />
        </template>
      </div>
      <div v-if="enrollment" class="workspace-bookmark">
        <span class="workspace-stamp">今日书签</span>
        <template v-if="progress && next">
          <small>下一未完成章节 · 第 {{ next.sortNo }} 章</small><h3>{{ next.title }}</h3>
          <RouterLink :to="chapterTarget(next)" class="workspace-main-link">继续阅读</RouterLink>
        </template>
        <template v-else-if="progress && chapters.length">
          <h3>章节已全部完成</h3><p>还可以复习原文，查看练习解析与习作反馈。</p>
          <RouterLink :to="chapterTarget(chapters[0]!)" class="workspace-main-link">复习首章</RouterLink>
        </template>
        <template v-else-if="!chapters.length"><h3>卷页待编</h3><p>教师尚未发布章节，请留意课程公告。</p></template>
        <template v-else><h3>展开卷中目录</h3><p>进度尚未读取，暂不推荐下一章。</p><a href="#workspace-chapters" class="workspace-main-link">查看章节</a></template>
      </div>
    </section>

    <template v-if="enrollment">
      <div v-if="course.status === 'ARCHIVED'" class="read-only-note">课程已归档，保留原文、练习结果与习作反馈；不能新增完成记录或提交。</div>
      <div v-if="urgent" class="workspace-urgent" role="status"><span>习作临近截止</span><div><strong>{{ urgent.title }}</strong><small>北京时间 {{ time(urgent.deadlineAt) }}</small></div><RouterLink :to="target(urgent)">前往习作</RouterLink></div>

      <div class="workspace-layout">
        <div class="workspace-main">
          <section id="workspace-chapters" class="workspace-volume" aria-labelledby="workspace-chapters-title">
            <div class="workspace-section-heading"><div><span class="workspace-section-name">读</span><h2 id="workspace-chapters-title">卷中学习路径</h2></div><span>{{ chapters.length }} 章</span></div>
            <div v-if="blocks.progress.error" class="workspace-block-error" role="alert"><p>进度暂不可用：{{ blocks.progress.error }}</p><el-button :loading="blocks.progress.busy" @click="loadBlock('progress')">重试进度</el-button></div>
            <div v-if="blocks.quizzes.error" class="workspace-block-error" role="alert"><p>练习目录暂不可用：{{ blocks.quizzes.error }}</p><el-button :loading="blocks.quizzes.busy" @click="loadBlock('quizzes')">重试练习目录</el-button></div>
            <ol v-if="chapters.length" class="workspace-path">
              <li v-for="chapter in chapters" :key="chapter.id" :class="{ 'is-complete': completed.has(chapter.id), 'is-next': next?.id === chapter.id }">
                <span class="workspace-chapter-number">{{ chapter.sortNo }}</span>
                <div><RouterLink :to="chapterTarget(chapter)" class="workspace-chapter-title">{{ chapter.title }}</RouterLink><div class="workspace-chapter-meta"><span>{{ !progress ? '进度未加载' : completed.has(chapter.id) ? '已完成' : next?.id === chapter.id ? '下一未完成章节' : '未完成' }}</span><RouterLink v-if="practiceCount(chapter)" :to="'/courses/' + id + '/quizzes?chapterId=' + chapter.id">本章练习 {{ practiceCount(chapter) }} 份</RouterLink></div></div>
                <RouterLink :to="chapterTarget(chapter)" class="workspace-chapter-action">{{ completed.has(chapter.id) ? '复习' : '阅读' }}</RouterLink>
              </li>
            </ol>
            <p v-else class="workspace-empty">尚无章节，教师发布后会出现在这里。</p>
          </section>

          <section class="workspace-volume" aria-labelledby="workspace-tasks-title">
            <div class="workspace-section-heading"><div><span class="workspace-section-name">写</span><h2 id="workspace-tasks-title">习作与反馈</h2></div><RouterLink :to="'/student/enrollments/' + enrollment.id + '/assignments'">全部习作</RouterLink></div>
            <p class="workspace-summary">{{ pending === null ? '待办数量尚未读取' : '本课有 ' + pending.length + ' 项尚可提交的习作' }}<span v-if="tasks"> · 共 {{ tasks.length }} 项可见习作</span></p>
            <div v-for="name in (['tasks', 'grades'] as const)" :key="name"><div v-if="blocks[name].error" class="workspace-block-error" role="alert"><p>{{ name === 'tasks' ? '习作' : '提交与成绩' }}暂不可用：{{ blocks[name].error }}</p><el-button :loading="blocks[name].busy" @click="loadBlock(name)">重试{{ name === 'tasks' ? '习作' : '提交记录' }}</el-button></div></div>
            <div v-if="!tasks && blocks.tasks.busy" class="workspace-empty" role="status">正在读取习作…</div>
            <p v-else-if="tasks?.length === 0" class="workspace-empty">教师尚未发布习作。</p>
            <article v-for="task in sortedTasks.slice(0, 5)" :key="task.id" class="workspace-task">
              <div><span class="workspace-task-state" :class="state(task).kind">{{ state(task).label }}</span><h3>{{ task.title }}</h3><small>满分 {{ task.maxScore }} · {{ task.deadlineAt ? '截止（北京时间）' + time(task.deadlineAt) : '未设置截止' }}</small><p v-if="gradeFor(task)?.status === 'GRADED'">当前成绩 {{ gradeFor(task)!.score }} / {{ task.maxScore }}</p></div>
              <RouterLink :to="target(task)">{{ state(task).action }}</RouterLink>
            </article>
            <p v-if="tasks && tasks.length > 5" class="workspace-summary">书案展示前 5 项，未提交且仍开放的习作优先。全部 {{ tasks.length }} 项见习作列表。</p>
          </section>
        </div>

        <aside class="workspace-side" aria-label="课程资料与公告">
          <section class="workspace-shelf"><h2>案上资料</h2><p>原文配套的文本、图册和朗读，在资源书架中查阅。</p><RouterLink :to="'/courses/' + id + '/resources'">展开资源书架</RouterLink><RouterLink :to="'/courses/' + id + '/quizzes'">{{ quizzes ? '全部章节练习（' + quizzes.length + '份）' : '展开章节练习' }}</RouterLink><RouterLink to="/student/notes">我的札记</RouterLink></section>
          <section class="workspace-notices"><div class="workspace-section-heading"><h2>先生来笺</h2><RouterLink :to="'/courses/' + id + '/community'">全部</RouterLink></div>
            <div v-if="blocks.notices.error" class="workspace-block-error" role="alert"><p>{{ blocks.notices.error }}</p><el-button :loading="blocks.notices.busy" @click="loadBlock('notices')">重试公告</el-button></div>
            <p v-else-if="blocks.notices.busy" role="status">正在读取公告…</p>
            <p v-else-if="notices?.total === 0" class="workspace-empty">暂无课程公告。</p>
            <article v-for="notice in notices?.items" :key="notice.id"><small>{{ notice.pinned ? '置顶 · ' : '' }}{{ time(notice.updatedAt) }}</small><h3>{{ notice.title }}</h3><p>{{ notice.content }}</p></article>
            <RouterLink :to="'/courses/' + id + '/community?tab=questions'" class="workspace-ask">带着疑问，去课堂交流</RouterLink>
          </section>
        </aside>
      </div>
    </template>
  </template>
</template>
