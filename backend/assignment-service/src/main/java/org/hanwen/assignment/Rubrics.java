package org.hanwen.assignment;
import org.hanwen.common.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
final class Rubrics {
 static final JsonMapper JSON=JsonMapper.builder().build();
 static String write(Object value){return JSON.writeValueAsString(value);}
 static List<RubricRules.Criterion> read(String value){return value==null?List.of():Arrays.asList(JSON.readValue(value,RubricRules.Criterion[].class));}
 static Object scores(String value){return value==null?null:JSON.readValue(value,String[].class);}
}
