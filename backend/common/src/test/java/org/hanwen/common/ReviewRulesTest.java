package org.hanwen.common;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class ReviewRulesTest {
 @Test void intervalAndMastery(){var now=LocalDateTime.of(2026,10,11,10,0);var first=ReviewRules.next(0,true,now);assertEquals(now.plusDays(1),first.next());assertFalse(first.mastered());assertEquals(now.plusDays(3),ReviewRules.next(1,true,now).next());assertTrue(ReviewRules.next(2,true,now).mastered());}
 @Test void wrongResets(){var now=LocalDateTime.now();var x=ReviewRules.next(3,false,now);assertEquals(0,x.streak());assertFalse(x.mastered());assertEquals(now.plusMinutes(10),x.next());}
}
