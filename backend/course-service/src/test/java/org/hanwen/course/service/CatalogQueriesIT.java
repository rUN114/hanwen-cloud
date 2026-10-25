package org.hanwen.course.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.hanwen.common.*;
import org.hanwen.course.mapper.*;
import org.hanwen.course.model.Course;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.*;

@SpringJUnitConfig(ResourcePurgeIT.Config.class)
class CatalogQueriesIT {
    @Autowired JdbcTemplate db;
    @Autowired QuizMapper quizzes;
    @Autowired QuizQuestionMapper quizQuestions;
    @Autowired QuestionMapper questions;
    @Autowired ReplyMapper replies;
    CourseService course;
    DirectoryClient directory;

    @BeforeEach
    void setup() {
        cleanup();
        Actor.set(new Actor(3, "STUDENT", 0));
        course = mock(CourseService.class);
        var c = new Course();
        c.id = 9001L;
        c.teacherId = 1L;
        c.status = "PUBLISHED";
        when(course.required(9001)).thenReturn(c);
        directory = mock(DirectoryClient.class);
        when(directory.names(anyString())).thenReturn(List.of());
        db.update(
                "INSERT INTO course(id,teacher_id,name,category,status,created_at,updated_at) VALUES(9001,1,'fixture','古文','PUBLISHED',NOW(3),NOW(3))");
        db.update(
                "INSERT INTO course_chapter(id,course_id,title,content,sort_no,created_at,updated_at) VALUES(9001,9001,'章节','正文',1,NOW(3),NOW(3))");
    }

    @Test
    void mysqlAggregatesQuestionCountsAndPointsIncludingEmptyQuiz() {
        db.update(
                "INSERT INTO chapter_quiz(id,course_id,chapter_id,title,status,created_at,updated_at) VALUES(9001,9001,9001,'有题练习','PUBLISHED',NOW(3),NOW(3)),(9002,9001,9001,'空练习','PUBLISHED',NOW(3),NOW(3))");
        db.update(
                "INSERT INTO quiz_question(quiz_id,prompt,option_a,option_b,option_c,option_d,correct_option,sort_no,points) VALUES(9001,'题一','甲','乙','丙','丁','A',1,3),(9001,'题二','甲','乙','丙','丁','B',2,7)");
        var service =
                new QuizService(course, quizzes, quizQuestions, mock(QuestionBankService.class));
        var page = (Map<?, ?>) service.list(9001, null, 1, 10);
        var rows = (List<Map<?, ?>>) page.get("items");
        assertEquals(0L, rows.getFirst().get("questionCount"));
        assertEquals(0, rows.getFirst().get("totalPoints"));
        assertEquals(2L, rows.get(1).get("questionCount"));
        assertEquals(10, rows.get(1).get("totalPoints"));
    }

    @Test
    void mysqlReplyCountsKeepNewestFirstAndZeroReplies() {
        db.update(
                "INSERT INTO course_question(id,course_id,student_id,title,content,created_at,updated_at) VALUES(9001,9001,3,'旧问题','内容',NOW(3),NOW(3)),(9002,9001,3,'新问题','内容',NOW(3),NOW(3))");
        db.update(
                "INSERT INTO course_reply(question_id,author_id,role,content,created_at) VALUES(9001,1,'TEACHER','答复一',NOW(3)),(9001,1,'TEACHER','答复二',NOW(3))");
        var service =
                new CommunityService(
                        course,
                        mock(NoticeMapper.class),
                        questions,
                        replies,
                        directory,
                        new Remote());
        var page = (Map<?, ?>) service.questions(9001, 1, 10, null, null);
        var rows = (List<Map<?, ?>>) page.get("items");
        assertEquals(List.of(0, 2), rows.stream().map(q -> q.get("replyCount")).toList());
        assertEquals(List.of("9002", "9001"), rows.stream().map(q -> q.get("id")).toList());
    }

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM course_reply WHERE question_id IN(9001,9002)");
        db.update("DELETE FROM course_question WHERE id IN(9001,9002)");
        db.update("DELETE FROM quiz_question WHERE quiz_id IN(9001,9002)");
        db.update("DELETE FROM chapter_quiz WHERE id IN(9001,9002)");
        db.update("DELETE FROM course_chapter WHERE id=9001");
        db.update("DELETE FROM course WHERE id=9001");
        Actor.clear();
    }
}
