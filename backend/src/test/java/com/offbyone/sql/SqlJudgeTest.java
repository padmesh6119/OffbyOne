package com.offbyone.sql;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SqlJudgeTest {
    final SqlJudge judge=new SqlJudge();
    SqlProblemBank bank() throws Exception {return new SqlProblemBank(new ObjectMapper());}
    @Test void everyReferencePassesAllHiddenDatasets() throws Exception {
        for(SqlProblem p:bank().findAll()) assertEquals("accepted",judge.judge(p,p.referenceQuery()).status(),p.slug());
    }
    @Test void hardcodedSampleCannotPassHiddenTests() throws Exception {
        var p=bank().findBySlug("sql-second-highest").orElseThrow();
        var v=judge.judge(p,"SELECT 88000");assertEquals("wrong_answer",v.status());assertNull(v.expectedRows());
    }
    @Test void incorrectCountFailsEmptyDepartment() throws Exception {
        var p=bank().findBySlug("sql-dept-headcount").orElseThrow();
        assertEquals("wrong_answer",judge.judge(p,"SELECT d.name,COUNT(*) FROM departments d LEFT JOIN employees e ON e.dept_id=d.id GROUP BY d.id,d.name ORDER BY d.name").status());
    }
    @Test void sampleRunDoesNotClaimFullAcceptance() throws Exception {
        var p=bank().findBySlug("sql-second-highest").orElseThrow();assertEquals("sample_passed",judge.runSample(p,"SELECT 88000").status());
    }
    @Test void commentsStringsAndQuotedIdentifiersDoNotTriggerFalseRejection() {
        assertDoesNotThrow(()->SqlJudge.validate("-- comment\n SELECT 'CREATE; DELETE', 1 AS \"UPDATE\"; -- end"));
        assertThrows(IllegalArgumentException.class,()->SqlJudge.validate("SELECT 1; DELETE FROM employees"));
        assertThrows(IllegalArgumentException.class,()->SqlJudge.validate("WITH a AS (SELECT 1) DELETE FROM employees"));
    }
    SqlJudge.Table table(List<List<Object>> rows) {return new SqlJudge.Table(List.of("a","b"),rows);}
    @Test void valuesAreComparedWithoutDelimiterCollisions() {
        assertFalse(SqlJudge.compare(table(List.of(List.of("a|b","c"))),table(List.of(List.of("a","b|c"))),false));
    }
    @Test void integersStayExactAndTextStaysText() {
        assertFalse(SqlJudge.compare(table(List.of(List.of(9007199254740992L,1))),table(List.of(List.of(9007199254740993L,1))),false));
        assertFalse(SqlJudge.compare(table(List.of(List.of("01",1))),table(List.of(List.of(1,1))),false));
        assertTrue(SqlJudge.compare(table(List.of(List.of(1,2.0))),table(List.of(List.of(1.0,2))),false));
    }
    @Test void duplicatesNullsAndOrderingAreRespected() {
        var a=table(List.of(Arrays.asList(null,"x"),List.of(1,"y")));
        var b=table(List.of(List.of(1,"y"),Arrays.asList(null,"x")));
        assertTrue(SqlJudge.compare(a,b,false));assertFalse(SqlJudge.compare(a,b,true));
        assertFalse(SqlJudge.compare(a,table(List.of(List.of(1,"y"),List.of(1,"y"))),false));
        assertFalse(SqlJudge.compare(new SqlJudge.Table(List.of("a"),List.of()),new SqlJudge.Table(List.of("a","b"),List.of()),false));
    }
    @Test void runawayQueriesAreInterrupted() throws Exception {
        var p=bank().findAll().get(0);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(4),()->assertEquals("tle",judge.judge(p,"WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n) SELECT sum(x) FROM n").status()));
    }
    @Test void resultLimitRejectsInsteadOfSilentlyTruncating() throws Exception {
        var p=bank().findAll().get(0);
        assertEquals("error",judge.runSample(p,"WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<1001) SELECT x FROM n").status());
    }
}
