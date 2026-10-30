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
@SpringBootTest(properties={"RENDER=false", "spring.datasource.url=jdbc:h2:mem:arena;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "app.jwt.secret=test-only-long-secret-key-for-sql-arena", "supabase.key=test", "spring.data.redis.password="})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class SqlArenaTest {
 @Autowired SqlArenaService arena; @Autowired UserRepository users; @Autowired RoomRepository rooms; @Autowired SqlProblemBank bank;
 @Autowired org.springframework.test.web.servlet.MockMvc http;
 @Autowired com.offbyone.config.JwtUtil jwt;
 @Autowired SubmissionRepository submissions;
 User a,b,outsider;
 @BeforeEach void setup(){a=user();b=user();outsider=user();}
 User user(){var u=new User();u.setUsername(UUID.randomUUID().toString());u.setEmail(u.getUsername()+"@test");u.setPasswordHash("unused");return users.save(u);}
 UUID id(Map<String,Object> m){return (UUID)m.get("id");}
 Map<String,Object> match(){var m=arena.create(a);arena.join((String)m.get("code"),b);return arena.start(id(m),a);}
 String solution(Map<String,Object> m){return bank.findBySlug((String)((Map<?,?>)m.get("problem")).get("slug")).orElseThrow().referenceQuery();}
 void expire(UUID id){var r=rooms.findById(id).orElseThrow();r.setEndTime(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));rooms.save(r);}
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
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sql-problems/sql-second-highest/run").header("Authorization",auth).contentType("application/json").content("{\"query\":\"SELECT 88000\"}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
   assertTrue(submissions.findByUserId(a.getId()).isEmpty());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sql-problems/sql-second-highest/submit").header("Authorization",auth).contentType("application/json").content("{\"query\":null}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
   http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sql-problems/sql-second-highest/submit").header("Authorization",auth).contentType("application/json").content("{\"query\":\"SELECT DISTINCT salary FROM employees ORDER BY salary DESC LIMIT 1 OFFSET 1\"}"))
     .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
   assertEquals("accepted",submissions.findByUserId(a.getId()).get(0).getVerdict());
 }

}
