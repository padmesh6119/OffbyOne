package com.offbyone.controller;
import com.offbyone.repository.SubmissionRepository;
import com.offbyone.sql.SqlProblemBank;
import com.offbyone.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PracticeInsightsTest {
 Submission attempt(String slug,String verdict){var s=new Submission();s.setLanguage("sql");s.setSqlSlug(slug);s.setVerdict(verdict);org.springframework.test.util.ReflectionTestUtils.setField(s,"id",UUID.randomUUID());return s;}
 @Test void solvedMistakesLeaveQueueAndGoalsCountDistinctSolves() throws Exception {
  var repo=mock(SubmissionRepository.class);var user=new User();user.setSkillLevel(1);var bank=new SqlProblemBank(new ObjectMapper());var easy=bank.findAll().stream().filter(p->p.difficulty().equals("easy")).toList();
  String a=easy.get(0).slug(),b=easy.get(1).slug();
  when(repo.findByUserId(user.getId())).thenReturn(List.of(attempt(a,"wrong_answer"),attempt(a,"accepted"),attempt(a,"accepted"),attempt(a,"error"),attempt(b,"error"),attempt("missing-slug","error")));
  var out=new PracticeInsightsController(repo,bank).insights(user);
  var mistakes=(List<Map<String,Object>>)out.get("mistakes");assertEquals(1,mistakes.size());assertEquals(b,mistakes.get(0).get("slug"));assertNotNull(mistakes.get(0).get("lastAttempt"));assertEquals(1L,out.get("todaySolved"));
  var recommended=(List<Map<String,Object>>)out.get("recommended");assertEquals(b,recommended.get(0).get("slug"));assertTrue(recommended.stream().allMatch(p->p.get("difficulty").equals("easy")&&!p.get("slug").equals(a)));
  assertEquals(7,((List<?>)out.get("activity")).size());
 }
 @Test void emptyPassShowsCleanQueueAndValidRecommendations() throws Exception {
  var repo=mock(SubmissionRepository.class);var user=new User();user.setSkillLevel(10);when(repo.findByUserId(null)).thenReturn(List.of());
  var out=new PracticeInsightsController(repo,new SqlProblemBank(new ObjectMapper())).insights(user);
  assertEquals(0L,out.get("todaySolved"));assertTrue(((List<?>)out.get("mistakes")).isEmpty());
  assertTrue(((List<Map<String,Object>>)out.get("recommended")).stream().allMatch(p->p.get("difficulty").equals("hard")));
 }
}
