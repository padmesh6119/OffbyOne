package com.offbyone.sprint;
import com.offbyone.model.*;
import com.offbyone.repository.*;
import com.offbyone.sql.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"RENDER=false","spring.sql.init.mode=never","spring.datasource.url=jdbc:h2:mem:sprint;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","app.jwt.secret=test-only-long-secret-key-for-sql-arena","supabase.key=test","spring.data.redis.password="})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class SqlSprintTest {
 @Autowired SqlSprintService sprints;@Autowired UserRepository users;@Autowired RoomRepository rooms;
 @Autowired RoomProblemRepository assignments;@Autowired SubmissionRepository submissions;@Autowired SqlProblemBank bank;
 @Autowired org.springframework.test.web.servlet.MockMvc http;@Autowired com.offbyone.config.JwtUtil jwt;
 User player,other;
 @BeforeEach void setup(){player=user();other=user();}
 User user(){var u=new User();u.setUsername(UUID.randomUUID().toString());u.setEmail(u.getUsername()+"@test");u.setPasswordHash("unused");return users.save(u);}
 UUID id(Map<String,Object> m){return (UUID)m.get("id");}
 String solution(Map<String,Object> m){return bank.findBySlug((String)((Map<?,?>)m.get("problem")).get("slug")).orElseThrow().referenceQuery();}
 @Test void formatIsStrictAndActiveRunResumesWithSameDeadline(){
  for(String difficulty:List.of("easy","medium","hard")){
   var m=sprints.create(player,10,difficulty);UUID id=id(m);
   assertEquals(0, Math.abs(java.time.Duration.between(
     java.time.Instant.parse((String)m.get("deadline")),
     java.time.Instant.parse((String)sprints.state(id,player).get("deadline"))).toNanos() / 1_000));
   assertEquals(id,id(sprints.create(player,5,"easy")));
   var selected=assignments.findByRoomIdOrderBySortOrderAsc(id);assertEquals(selected.size(),selected.stream().map(RoomProblem::getSqlSlug).distinct().count());
   assertTrue(selected.stream().allMatch(p->difficulty.equals(bank.findBySlug(p.getSqlSlug()).orElseThrow().difficulty())));
   assertThrows(ResponseStatusException.class,()->sprints.state(id,other));sprints.finish(id,player);
  }
  assertThrows(ResponseStatusException.class,()->sprints.create(player,2,"easy"));assertThrows(ResponseStatusException.class,()->sprints.create(player,5,"mixed"));
 }
 @Test void scoringCountsFirstTryBonusAndRejectsRepeatedPosition(){
  var m=sprints.create(player,5,"easy");UUID id=id(m);String query=solution(m);
  var result=sprints.submit(id,player,0,query);m=(Map<String,Object>)result.get("sprint");assertEquals(150,m.get("score"));assertEquals(1,m.get("solved"));
  assertThrows(ResponseStatusException.class,()->sprints.submit(id,player,0,query));
  sprints.submit(id,player,1,"SELECT 'definitely not the result'");
  m=(Map<String,Object>)sprints.submit(id,player,1,solution(m)).get("sprint");assertEquals(250,m.get("score"));assertEquals(1,m.get("firstTry"));assertEquals(3,m.get("attempts"));
  m=sprints.skip(id,player,2);assertEquals(3,m.get("position"));assertEquals(250,m.get("score"));
  m=sprints.finish(id,player);assertEquals("finished",m.get("status"));assertNotNull(m.get("review"));assertFalse(m.containsKey("problem"));
 }
 @Test void expiredRunsNeverScoreOrResetAndNewRunCanStart(){
  var m=sprints.create(player,5,"easy");UUID id=id(m);String query=solution(m);var room=rooms.findById(id).orElseThrow();room.setEndTime(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));rooms.save(room);
  assertThrows(ResponseStatusException.class,()->sprints.submit(id,player,0,query));
  var done=sprints.state(id,player);assertEquals("finished",done.get("status"));assertEquals(0,done.get("score"));assertTrue(submissions.findByRoomId(id).isEmpty());
  assertNotEquals(id,id(sprints.create(player,15,"hard")));
 }
 @Test void concurrentStartAndSubmissionDoNotDuplicateRunsOrPoints() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)){
   var starts=pool.invokeAll(List.<Callable<Map<String,Object>>>of(()->sprints.create(player,5,"easy"),()->sprints.create(player,5,"easy")));
   var m=starts.get(0).get();UUID id=id(m);assertEquals(id,id(starts.get(1).get()));String query=solution(m);
   var attempts=pool.invokeAll(List.<Callable<Boolean>>of(()->submit(id,query),()->submit(id,query)));
   assertEquals(1,attempts.stream().filter(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).count());
   assertEquals(150,sprints.state(id,player).get("score"));assertEquals(1,submissions.findByRoomId(id).size());
  }
 }
 boolean submit(UUID id,String query){try{sprints.submit(id,player,0,query);return true;}catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}}
 @Test void skippingFinalChallengeFinishesAndRunIsPrivate(){
  var m=sprints.create(player,15,"hard");UUID id=id(m);
  for(int i=0;i<(Integer)m.get("total");i++)m=sprints.skip(id,player,i);
  assertEquals("finished",m.get("status"));assertEquals(0,m.get("score"));assertEquals(1,sprints.history(player).size());assertTrue(sprints.history(other).isEmpty());
 }
 @Test void legacyEndpointsCannotModifySprint() throws Exception {
  var m=sprints.create(player,5,"easy");UUID id=id(m);var r=rooms.findById(id).orElseThrow();String auth="Bearer "+jwt.generate(player.getEmail());
  http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/rooms/"+r.getJoinCode()+"/join").header("Authorization",auth)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
  http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/rooms/"+id+"/start").header("Authorization",auth)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
  http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/sql-sprints/"+id).header("Authorization","Bearer "+jwt.generate(other.getEmail()))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
 }
}
