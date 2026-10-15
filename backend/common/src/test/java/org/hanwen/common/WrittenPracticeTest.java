package org.hanwen.common;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class WrittenPracticeTest {
 @Test void writtenResponsesAwaitMarkingAndKeepReferenceFrozen(){
  var snapshot=new Contracts.QuizSnapshot(1,2,3,4,"PUBLISHED","PUBLISHED",null,null,0,1,List.of(
   new Contracts.QuizQuestionSnapshot(11,"作者","李白","杜甫","王维","白居易","A","","SINGLE",2,"作者"),
   new Contracts.QuizQuestionSnapshot(12,"填空","","","","","思故乡","","FILL_BLANK",3,"诗句"),
   new Contracts.QuizQuestionSnapshot(13,"解释乡思","","","","","月光与故乡关联","","SHORT_ANSWER",5,"意象")));
  var result=QuestionRules.grade(snapshot,new Contracts.QuizAnswers(List.of(new Contracts.QuizChoice(11L,"A"),new Contracts.QuizChoice(12L,"思故乡"),new Contracts.QuizChoice(13L," 从月光想起故乡。 ")),7L));
  assertEquals(2,result.score());assertEquals(10,result.total());assertNull(result.items().get(1).correct());assertNull(result.items().get(2).awardedPoints());assertEquals("从月光想起故乡。",result.items().get(2).selectedOption());assertEquals("意象",result.items().get(2).knowledgePoint());
 }
 @Test void writtenBoundsAndGradeRanges(){
  assertNull(QuestionRules.earned("SHORT_ANSWER",null,null,5));
  assertEquals(0,QuestionRules.earned("SHORT_ANSWER",false,0,5));
  assertEquals(2,QuestionRules.earned("SINGLE",true,null,2));
  for(String text:Arrays.asList(null,"","   ","字".repeat(4001)))assertThrows(ApiException.class,()->QuestionRules.choice("SHORT_ANSWER",text,false));
  assertEquals(0,QuestionRules.awarded(0,5));assertEquals(5,QuestionRules.awarded(5,5));
  for(Integer score:Arrays.asList(null,-1,6))assertThrows(ApiException.class,()->QuestionRules.awarded(score,5));
 }
 @Test void courseCapabilitiesDoNotGrantAssistantEditing(){
  for(String role:List.of("OWNER","CO_TEACHER","ASSISTANT","NONE")){
   var c=new Contracts.CourseValidation(1,2,"PUBLISHED",3,"课",role);
   assertEquals(Set.of("OWNER","CO_TEACHER").contains(role),c.canTeach());
   assertEquals(!role.equals("NONE"),c.canGrade());assertEquals(role.equals("OWNER"),c.principal());
  }
 }
}
