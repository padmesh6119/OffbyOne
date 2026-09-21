package com.offbyone.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "test_cases")
public class TestCase {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false) private Problem problem;
    @Column(nullable = false, columnDefinition = "TEXT") private String input;
    @Column(name = "expected_output", nullable = false, columnDefinition = "TEXT") private String expectedOutput;
    @Column(name = "is_sample") private boolean isSample = false;

    public UUID getId() { return id; }
    public Problem getProblem() { return problem; }
    public void setProblem(Problem v) { this.problem = v; }
    public String getInput() { return input; }
    public void setInput(String v) { this.input = v; }
    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String v) { this.expectedOutput = v; }
    public boolean isSample() { return isSample; }
    public void setSample(boolean v) { this.isSample = v; }
}
