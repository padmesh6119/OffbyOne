package com.offbyone.repository;

import com.offbyone.model.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {
    List<TestCase> findByProblemId(UUID problemId);
    List<TestCase> findByProblemIdAndIsSampleTrue(UUID problemId);
}
