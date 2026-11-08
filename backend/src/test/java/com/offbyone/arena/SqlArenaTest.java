package com.offbyone.arena;
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
@SpringBootTest(properties={"RENDER=false", "spring.sql.init.mode=never", "spring.datasource.url=jdbc:h2:mem:arena;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "app.jwt.secret=test-only-long-secret-key-for-sql-arena", "supabase.key=test", "spring.data.redis.password="})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class SqlArenaTest {
 @Autowired SqlArenaService arena; @Autowired UserRepository users; @Autowired RoomRepository rooms; @Autowired SqlProblemBank bank;
 @Autowired org.springframework.test.web.servlet.MockMvc http;
 @Autowired com.offbyone.config.JwtUtil jwt;
 @Autowired SubmissionRepository submissions;
 @Autowired RoomProblemRepository assignments;
 User a,b,outsider;
 @BeforeEach void setup(){a=user();b=user();outsider=user();}
 User user(){var u=new User();u.setUsername(UUID.randomUUID().toString());u.setEmail(u.getUsername()+"@test");u.setPasswordHash("unused");return users.save(u);}
 UUID id(Map<String,Object> m){return (UUID)m.get("id");}
 Map<String,Object> match(){var m=arena.create(a);arena.join((String)m.get("code"),b);return arena.start(id(m),a);}
 String solution(Map<String,Object> m){return bank.findBySlug((String)((Map<?,?>)m.get("problem")).get("slug")).orElseThrow().referenceQuery();}
 void expire(UUID id){var r=rooms.findById(id).orElseThrow();r.setEndTime(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));rooms.save(r);}
 @Test void skillChoosesSharedQuestionsAndLocksAtStart(){
   a.setSkillLevel(1);users.save(a);b.setSkillLevel(3);users.save(b);
   var m=match();assertEquals(2,m.get("skillLevel"));
   assertEquals("easy",((Map<?,?>)m.get("problem")).get("difficulty"));
   assertRoundDifficulty(id(m),"easy");
   assertEquals(m.get("problem"),arena.state(id(m),b).get("problem"));
   assertThrows(ResponseStatusException.class,()->arena.skill(id(m),a,10));
   var high=arena.create(a);arena.skill(id(high),a,10);arena.join((String)high.get("code"),b);arena.skill(id(high),b,10);
   var started=arena.start(id(high),a);assertEquals(10,started.get("skillLevel"));
   assertEquals("hard",((Map<?,?>)started.get("problem")).get("difficulty"));
   assertRoundDifficulty(id(high),"hard");
 }
 void assertRoundDifficulty(UUID roomId,String difficulty){
   var rounds=assignments.findByRoomIdOrderBySortOrderAsc(roomId);
   assertEquals(3,rounds.size());
   assertEquals(3,rounds.stream().map(RoomProblem::getSqlSlug).distinct().count());
   for(var round:rounds) assertEquals(difficulty,bank.findBySlug(round.getSqlSlug()).orElseThrow().difficulty());
 }
 @Test void expiredGuestNameIsReusableWithoutTakingOverHistory() throws Exception {
   a.setUsername("reuse_"+UUID.randomUUID().toString().substring(0,8));
   a.setSessionExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));users.save(a);
   String originalEmail=a.getEmail();String name=a.getUsername();
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/join").contentType("application/json").content("{\"username\":\""+name+"\",\"skillLevel\":\"7\"}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
   assertNotEquals(a.getId(),users.findByUsername(name).orElseThrow().getId());
   assertEquals(7,users.findByUsername(name).orElseThrow().getSkillLevel());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/sql-problems").header("Authorization","Bearer "+jwt.generate(originalEmail)))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
 }
 @Test void invalidSkillIsRejected() throws Exception {
   for(String level:List.of("0","11","bad")) http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/join").contentType("application/json").content("{\"username\":\"newguest\",\"skillLevel\":\""+level+"\"}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
 }
 @Test void membershipCapacityAndStartAreEnforced(){var m=arena.create(a);assertThrows(ResponseStatusException.class,()->arena.state(id(m),outsider));assertThrows(ResponseStatusException.class,()->arena.start(id(m),a));arena.join((String)m.get("code"),b);assertThrows(ResponseStatusException.class,()->arena.join((String)m.get("code"),outsider));assertThrows(ResponseStatusException.class,()->arena.start(id(m),b));}
 @Test void bestOfThreeEndsAtTwoAndRematchIsShared(){var m=match();UUID id=id(m);arena.submit(id,a,1,solution(m));expire(id);m=arena.state(id,a);assertEquals(2,m.get("round"));arena.submit(id,a,2,solution(m));m=arena.state(id,a);assertEquals("finished",m.get("status"));assertEquals(a.getUsername(),m.get("winner"));var next=arena.rematch(id,a);assertEquals(id(next),id(arena.rematch(id,b)));assertEquals(2,((List<?>)arena.state(id(next),a).get("players")).size());}
 @Test void finishedOrStaleRoundCannotScore(){var m=match();UUID id=id(m);String sql=solution(m);arena.submit(id,a,1,sql);assertThrows(ResponseStatusException.class,()->arena.submit(id,b,1,sql));expire(id);arena.state(id,a);assertThrows(ResponseStatusException.class,()->arena.submit(id,b,1,sql));arena.leave(id,a);assertThrows(ResponseStatusException.class,()->arena.submit(id,b,2,sql));}
 @Test void threeTimeoutsProduceDraw(){var m=match();UUID id=id(m);for(int i=0;i<3;i++){expire(id);m=arena.state(id,a);if(i<2){assertEquals("intermission",m.get("status"));expire(id);arena.state(id,a);}}assertEquals("finished",m.get("status"));assertNull(m.get("winner"));}
 @Test void reviewIsOnlyVisibleAfterMatch(){var m=match();var h=(List<Map<String,Object>>)m.get("history");assertFalse(h.get(0).containsKey("solution"));m=arena.leave(id(m),a);h=(List<Map<String,Object>>)m.get("history");assertNotNull(h.get(0).get("solution"));assertEquals(b.getUsername(),m.get("winner"));}
 @Test void simultaneousCorrectSubmissionsAwardOnePoint() throws Exception {var m=match();UUID id=id(m);String sql=solution(m);var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{List<Future<Boolean>> futures=new ArrayList<>();for(User u:List.of(a,b))futures.add(pool.submit(()->{gate.await();try{arena.submit(id,u,1,sql);return true;}catch(ResponseStatusException e){return false;}}));gate.countDown();int successes=0;for(var f:futures)if(f.get(10,TimeUnit.SECONDS))successes++;assertEquals(1,successes);var state=arena.state(id,a);var list=(List<Map<String,Object>>)state.get("players");assertEquals(1,list.stream().mapToInt(x->(Integer)x.get("score")).sum());}finally{pool.shutdownNow();}}
 @Test void outsidersCannotReadMatchOverHttp() throws Exception {
   var m=arena.create(a);
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/sql-arena/"+id(m)).header("Authorization","Bearer "+jwt.generate(outsider.getEmail())))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
 }
 @Test void missingInvalidAndOrphanedSessionsRequireLogin() throws Exception {
   var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/sql-problems");
   http.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
   for (String token : List.of("invalid-session", "", jwt.generate("deleted-account@test"))) {
     http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/sql-problems").header("Authorization", "Bearer " + token))
       .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
   }
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/sql-problems").header("Authorization", "Bearer " + jwt.generate(a.getEmail())))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
 }
 @Test void existingGuestCannotBeClaimed() throws Exception {
   String name="guest_"+UUID.randomUUID().toString().substring(0,8);
   String body="{\"username\":\""+name+"\"}";
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/join").contentType("application/json").content(body))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/join").contentType("application/json").content(body))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
 }
 @Test void practicePersistsOnlySubmissionsAndRejectsNullInput() throws Exception {
   var auth="Bearer "+jwt.generate(a.getEmail());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/sql-problems/sql-student-names").header("Authorization",auth))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.hint").isNotEmpty())
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.expected.rows[0][0]").value("Anu"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.referenceQuery").doesNotExist())
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.hiddenDatasets").doesNotExist());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sql-problems/sql-second-highest/run").header("Authorization",auth).contentType("application/json").content("{\"query\":\"SELECT 88000\"}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
   assertTrue(submissions.findByUserId(a.getId()).isEmpty());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sql-problems/sql-second-highest/submit").header("Authorization",auth).contentType("application/json").content("{\"query\":\"SELECT 88000\"}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.verdict").value("wrong_answer"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.review").doesNotExist());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sql-problems/sql-second-highest/submit").header("Authorization",auth).contentType("application/json").content("{\"query\":null}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sql-problems/sql-second-highest/submit").header("Authorization",auth).contentType("application/json").content("{\"query\":\"SELECT DISTINCT salary FROM employees ORDER BY salary DESC LIMIT 1 OFFSET 1\"}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.review.solution").value(bank.findBySlug("sql-second-highest").orElseThrow().referenceQuery()))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.review.explanation").isNotEmpty());
   assertTrue(submissions.findByUserId(a.getId()).stream().anyMatch(s -> "accepted".equals(s.getVerdict())));
 }

 @Test void practiceHubRewardsDistinctSolvesAndProtectsQueryHistory() throws Exception {
   a.setSessionExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));users.save(a);
   b.setSessionExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));users.save(b);
   for(int i=0;i<3;i++){var s=new Submission();s.setUser(a);s.setLanguage("sql");s.setSqlSlug("sql-student-names");s.setCode("SELECT name FROM students");s.setVerdict("accepted");submissions.save(s);}
   var expired=new Submission();expired.setUser(b);expired.setLanguage("sql");expired.setSqlSlug("sql-student-cities");expired.setCode("SELECT name,city FROM students");expired.setVerdict("accepted");submissions.save(expired);
   String auth="Bearer "+jwt.generate(a.getEmail());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/practice-hub").header("Authorization",auth))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.xp").value(100))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.firstTry").value(1))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.solved.length()").value(1))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.daily.difficulty").value("easy"));
   var standings=http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/practice-hub/leaderboard").header("Authorization",auth))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse().getContentAsString();
   assertTrue(standings.contains(a.getUsername())); assertFalse(standings.contains(b.getUsername()));
   var own=submissions.findByUserId(a.getId()).get(0);
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/submissions/"+own.getId()).header("Authorization",auth))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("SELECT name FROM students"));
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/submissions/"+own.getId()).header("Authorization","Bearer "+jwt.generate(outsider.getEmail())))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
 }

 @Test void logoutRevokesPassReleasesNameAndForfeitsDuel() throws Exception {
   String name="logout_"+UUID.randomUUID().toString().substring(0,8);
   a.setUsername(name);a.setSessionExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));users.save(a);
   String auth="Bearer "+jwt.generate(a.getEmail());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/auth/session").header("Authorization",auth).contentType("application/json").content("{\"skillLevel\":\"7\"}"))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
   assertEquals(7,users.findById(a.getId()).orElseThrow().getSkillLevel());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/auth/session").header("Authorization",auth).contentType("application/json").content("{\"skillLevel\":\"11\"}"))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
   var m=match();
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout").header("Authorization",auth))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
   assertTrue(users.findById(a.getId()).orElseThrow().sessionExpired());
   assertEquals(b.getId(),rooms.findById(id(m)).orElseThrow().getWinner().getId());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/sql-problems").header("Authorization",auth))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/join").contentType("application/json").content("{\"username\":\""+name+"\"}"))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout"))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
 }

}
