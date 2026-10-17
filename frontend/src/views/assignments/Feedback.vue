<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { api } from "../../api/client";
import { useTask, time } from "../../composables/useTask";
import type { Submission, Task } from "../../types/models";
import PageHeading from "../../components/common/PageHeading.vue";
import StatePanel from "../../components/common/StatePanel.vue";
import GradeHistoryPanel from "../../components/assignments/GradeHistoryPanel.vue";
import StatusTag from "../../components/common/StatusTag.vue";
import CourseLinks from "../../components/courses/CourseLinks.vue";
import AttachmentPanel from "../../components/assignments/AttachmentPanel.vue";
import RevisionPanel from "../../components/assignments/RevisionPanel.vue";
const id = String(useRoute().params.submissionId);
const submission = ref<Submission | null>(null);
const courseId = ref<string | null>(null);
const { busy, error, run } = useTask();
function load() {
  run(async () => {
    submission.value = await api<Submission>("/api/submissions/" + id);
    courseId.value = (
      await api<Task>("/api/assignments/" + submission.value.assignmentId)
    ).courseId;
  });
}
onMounted(load);
</script>
<template>
  <PageHeading
    :title="submission?.taskTitle || '批改反馈'"
    back="/student/grades"
    description="仅显示当前评分与评语，保留评分与更正记录。"
  />
  <StatePanel
    :busy="busy && !submission"
    :error="error"
    @retry="load"
  />
  <CourseLinks
    v-if="courseId"
    :course-id="courseId"
  />
  <div
    v-if="submission"
    class="grade-layout"
  >
    <section class="paper">
      <h2>我的答卷</h2>
      <p class="field-hint">提交于 {{ time(submission.submittedAt) }}</p>
      <div class="answer-copy">{{ submission.answerText }}</div>
      <AttachmentPanel :submission-id="id" />
      <RouterLink
        v-if="submission.status === 'SUBMITTED'"
        :to="'/student/assignments/' + submission.assignmentId + '/submit'"
      >
        <el-button style="margin-top: 22px">查看提交与修改条件</el-button>
      </RouterLink>
    </section>
    <section class="paper red-paper">
      <h2>先生批语</h2>
      <StatusTag :status="submission.status" />
      <template v-if="submission.grade">
        <p class="score-large">
          {{ submission.grade.score }}
          <small>/ {{ submission.maxScore }}</small>
        </p>
        <ul
          v-if="submission.rubric?.length"
          class="rubric-feedback"
        >
          <li
            v-for="(item, index) in submission.rubric"
            :key="item.name"
          >
            {{ item.name }} {{ submission.grade.rubricScores?.[index] ?? "未记录" }} /
            {{ item.maxScore }}
          </li>
        </ul>
        <div class="feedback-quote">
          {{ submission.grade.comment || "本次未填写评语。" }}
        </div>
        <p class="field-hint">
          评分于 {{ time(submission.grade.gradedAt) }} · 教师编号
          {{ submission.grade.teacherId }}
        </p>
        <span class="completed-seal">已批阅</span>
      </template>
      <div
        v-else
        class="empty-state"
      >
        <img
          src="/assets/desk.svg"
          alt=""
          aria-hidden="true"
        />
        <p>答案已提交，等待教师批改。</p>
      </div>
    </section>
  </div>
  <GradeHistoryPanel
    v-if="submission"
    :submission-id="id"
    :version="submission.version"
  />
  <RevisionPanel
    v-if="submission"
    :key="id"
    :submission-id="id"
    :submission-version="submission.version"
    :status="submission.status"
  />
</template>
