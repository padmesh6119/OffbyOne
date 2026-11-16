package com.offbyone.controller;
import com.offbyone.repository.SubmissionRepository;
import com.offbyone.sql.SqlProblemBank;
import com.offbyone.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class DailyBoardTest {
 @Test void firstClearLocksAttemptsAndEqualScoresShareRank() throws Exception {
  var repo=mock(SubmissionRepository.class);var user=mock(User.class);UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID(),d=UUID.randomUUID();when(user.getId()).thenReturn(a);
  when(repo.dailyAttempts(anyString(),any(),any(),any())).thenReturn(List.of(
   new Object[]{a,"A","wrong_answer"},new Object[]{b,"B","accepted"},new Object[]{a,"A","accepted"},new Object[]{a,"A","error"},new Object[]{a,"A","accepted"},new Object[]{c,"C","accepted"},new Object[]{d,"D","wrong_answer"}));
  var controller=new PracticeHubController(repo,new SqlProblemBank(new ObjectMapper()));
  var result=controller.dailyBoard(user);var players=(List<Map<String,Object>>)result.get("players");
  assertEquals(3,players.size());assertEquals(3,result.get("yourRank"));
  assertEquals(1,players.get(0).get("rank"));assertEquals(1,players.get(1).get("rank"));assertEquals(2,players.get(2).get("attempts"));
 }
}
