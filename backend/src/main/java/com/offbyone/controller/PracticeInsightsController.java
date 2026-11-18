package com.offbyone.controller;

import com.offbyone.model.*;
import com.offbyone.repository.SubmissionRepository;
import com.offbyone.sql.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/practice-insights")
public class PracticeInsightsController {
    private final SubmissionRepository submissions;private final SqlProblemBank bank;
    public PracticeInsightsController(SubmissionRepository submissions,SqlProblemBank bank) {this.submissions=submissions;this.bank=bank;}
    private Map<String,Object> card(SqlProblem p) {return new LinkedHashMap<>(Map.of("slug",p.slug(),"title",p.title(),"difficulty",p.difficulty(),"pattern",p.pattern()));}
    @GetMapping
    @Transactional(readOnly=true)
    public Map<String,Object> insights(@AuthenticationPrincipal User user) {
        var attempts=submissions.findByUserId(user.getId()).stream()
            .filter(s->"sql".equals(s.getLanguage()) && bank.findBySlug(s.getSqlSlug()).isPresent() && !List.of("pending","queued","busy").contains(s.getVerdict()))
            .sorted(Comparator.comparing(Submission::getSubmittedAt)).toList();
        var grouped=attempts.stream().collect(Collectors.groupingBy(Submission::getSqlSlug));
        var solved=attempts.stream().filter(s->"accepted".equals(s.getVerdict())).map(Submission::getSqlSlug).collect(Collectors.toSet());
        var mistakes=grouped.entrySet().stream().filter(e->!solved.contains(e.getKey()))
            .sorted(Comparator.<Map.Entry<String,List<Submission>>>comparingInt(e->e.getValue().size()).reversed().thenComparing(Map.Entry::getKey))
            .map(e->{var p=card(bank.findBySlug(e.getKey()).orElseThrow());var last=e.getValue().get(e.getValue().size()-1);
                p.put("attempts",e.getValue().size());p.put("lastVerdict",last.getVerdict());p.put("lastAttempt",last.getId());return p;}).toList();
        var topics=bank.findAll().stream().collect(Collectors.groupingBy(SqlProblem::pattern)).entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->{
            var slugs=e.getValue().stream().map(SqlProblem::slug).collect(Collectors.toSet());
            var topicAttempts=attempts.stream().filter(s->slugs.contains(s.getSqlSlug())).toList();
            long accepted=topicAttempts.stream().filter(s->"accepted".equals(s.getVerdict())).count();
            return Map.<String,Object>of("pattern",e.getKey(),"total",slugs.size(),"solved",slugs.stream().filter(solved::contains).count(),
                "attempts",topicAttempts.size(),"accuracy",topicAttempts.isEmpty() ? 0 : Math.round(100.0*accepted/topicAttempts.size()));
        }).toList();
        String difficulty=user.getSkillLevel()<=3 ? "easy" : user.getSkillLevel()<=7 ? "medium" : "hard";
        Set<String> weakPatterns=mistakes.stream().map(p->(String)p.get("pattern")).collect(Collectors.toSet());
        var recommended=bank.findAll().stream().filter(p->!solved.contains(p.slug()) && difficulty.equals(p.difficulty()))
            .sorted(Comparator.<SqlProblem>comparingInt(p->grouped.containsKey(p.slug()) ? 0 : weakPatterns.contains(p.pattern()) ? 1 : 2)
                .thenComparingInt(SqlProblem::rating).thenComparing(SqlProblem::slug)).limit(3)
            .map(p->{var c=card(p);c.put("reason",grouped.containsKey(p.slug()) ? "Finish what you started" : weakPatterns.contains(p.pattern()) ? "Strengthen a tricky topic" : "At your selected level");return c;}).toList();
        var today=LocalDate.now(ZoneOffset.UTC);
        var activity=new ArrayList<Map<String,Object>>();
        for(int i=6;i>=0;i--) {
            var day=today.minusDays(i);var daily=attempts.stream().filter(s->s.getSubmittedAt().toLocalDate().equals(day)).toList();
            long count=daily.stream().filter(s->"accepted".equals(s.getVerdict())).map(Submission::getSqlSlug).distinct().count();
            activity.add(Map.of("date",day.toString(),"solved",count,"attempts",daily.size()));
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("mistakes",mistakes);out.put("topics",topics);out.put("recommended",recommended);
        out.put("activity",activity);out.put("todaySolved",activity.get(6).get("solved"));out.put("date",today.toString());
        out.put("resetsAt",today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString());return out;
    }
}
