package com.offbyone.seed;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offbyone.model.Problem;
import com.offbyone.model.TestCase;
import com.offbyone.repository.ProblemRepository;
import com.offbyone.repository.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProblemSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProblemSeeder.class);

    private final ProblemRepository problemRepo;
    private final TestCaseRepository testCaseRepo;
    private final ObjectMapper objectMapper;

    public ProblemSeeder(ProblemRepository problemRepo, TestCaseRepository testCaseRepo, ObjectMapper objectMapper) {
        this.problemRepo = problemRepo; this.testCaseRepo = testCaseRepo; this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<ProblemSeed> seeds = objectMapper.readValue(
                new ClassPathResource("problems.json").getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ProblemSeed.class));

        int added = 0;
        for (ProblemSeed seed : seeds) {
            if (problemRepo.findBySlug(seed.slug()).isPresent()) continue;

            Problem problem = new Problem();
            problem.setSlug(seed.slug());
            problem.setTitle(seed.title());
            problem.setDifficulty(seed.difficulty());
            problem.setStatement(seed.statement());
            problem.setTimeLimitMs(seed.timeLimitMs());
            problem.setMemoryLimitMb(seed.memoryLimitMb());
            problem = problemRepo.save(problem);

            for (TestCaseSeed tcSeed : seed.testCases()) {
                TestCase tc = new TestCase();
                tc.setProblem(problem);
                tc.setInput(tcSeed.input());
                tc.setExpectedOutput(tcSeed.expectedOutput());
                tc.setSample(tcSeed.isSample());
                testCaseRepo.save(tc);
            }
            added++;
        }
        log.info("problem seeder: {} new problems added, {} already present", added, seeds.size() - added);
    }

    record ProblemSeed(String slug, String title, String difficulty, String statement,
                        int timeLimitMs, int memoryLimitMb, List<TestCaseSeed> testCases) {}

    record TestCaseSeed(String input,
                         @JsonProperty("expected_output") String expectedOutput,
                         @JsonProperty("is_sample") boolean isSample) {}
}
