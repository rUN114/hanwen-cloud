package org.hanwen.common;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;

class QuestionRulesTest {
    Contracts.QuizSnapshot quiz() {
        return new Contracts.QuizSnapshot(1,2,3,4,"PUBLISHED","PUBLISHED",null,null,20,2,List.of(
                new Contracts.QuizQuestionSnapshot(11,"单选","甲","乙","丙","丁","A","", "SINGLE",2),
                new Contracts.QuizQuestionSnapshot(12,"多选","甲","乙","丙","丁","AC","", "MULTIPLE",5),
                new Contracts.QuizQuestionSnapshot(13,"判断","正确","错误","","","B","", "TRUE_FALSE",3)));
    }
    @Test void exactSetWeightedGradeAndOrdering() {
        var in=new Contracts.QuizAnswers(List.of(new Contracts.QuizChoice(11L,"A"),
                new Contracts.QuizChoice(12L,"CA"),new Contracts.QuizChoice(13L,"B")),null);
        var result=QuestionRules.grade(quiz(),in);
        assertEquals(10,result.score()); assertEquals(10,result.total());
        assertEquals("AC",result.items().get(1).selectedOption());
        assertEquals(5,result.items().get(1).points());
        var partial=new Contracts.QuizAnswers(List.of(new Contracts.QuizChoice(11L,"A"),
                new Contracts.QuizChoice(12L,"A"),new Contracts.QuizChoice(13L,"A")),null);
        assertEquals(2,QuestionRules.grade(quiz(),partial).score());
    }
    @Test void invalidAndDuplicateChoicesRejected() {
        for(var choice:List.of("","AA","AE","A A","abc","ABCDE"))
            assertThrows(ApiException.class,()->QuestionRules.choice("MULTIPLE",choice,false));
        assertThrows(ApiException.class,()->QuestionRules.choice("SINGLE","AB",false));
        assertThrows(ApiException.class,()->QuestionRules.choice("TRUE_FALSE","C",false));
        assertThrows(ApiException.class,()->QuestionRules.choice("MULTIPLE","A",true));
        assertThrows(ApiException.class,()->QuestionRules.type("ESSAY"));
        for(int value:List.of(0,-1,101)) assertThrows(ApiException.class,()->QuestionRules.points(value));
    }
    @Test void wrongQuestionMissingAndRepeatedAnswersRejected() {
        for(var list:List.of(List.of(new Contracts.QuizChoice(11L,"A")),
                List.of(new Contracts.QuizChoice(11L,"A"),new Contracts.QuizChoice(11L,"B"),new Contracts.QuizChoice(13L,"B")),
                List.of(new Contracts.QuizChoice(11L,"A"),new Contracts.QuizChoice(99L,"AC"),new Contracts.QuizChoice(13L,"B"))))
            assertThrows(ApiException.class,()->QuestionRules.grade(quiz(),new Contracts.QuizAnswers(list,null)));
    }
    @Test void serverDeadlineAndOpenBoundaries() {
        var now=LocalDateTime.of(2026,10,11,12,0);
        assertEquals(now.plusMinutes(20),PracticeRules.deadline(quiz(),now));
        assertFalse(PracticeRules.expired(now.plusMinutes(20),now));
        assertTrue(PracticeRules.expired(now,now));
        var q=new Contracts.QuizSnapshot(1,2,3,4,"PUBLISHED","PUBLISHED",now,now.plusMinutes(5),20,2,quiz().questions());
        PracticeRules.available(q,now);
        assertEquals(now.plusMinutes(5),PracticeRules.deadline(q,now));
        assertThrows(ApiException.class,()->PracticeRules.available(q,now.minusNanos(1)));
        assertThrows(ApiException.class,()->PracticeRules.available(q,now.plusMinutes(5)));
        assertThrows(ApiException.class,()->PracticeRules.available(new Contracts.QuizSnapshot(
                1,2,3,4,"ARCHIVED","PUBLISHED",null,null,0,1,quiz().questions()),now));
    }
}
