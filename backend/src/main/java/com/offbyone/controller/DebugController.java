package com.offbyone.controller;

import com.offbyone.judge.JudgeService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Temporary — remove once the judge fallback is confirmed working on the deployed host. */
@RestController
@RequestMapping("/api/debug")
public class DebugController {
    private final JudgeService judgeService;
    public DebugController(JudgeService judgeService) { this.judgeService = judgeService; }

    @GetMapping("/fallback")
    public Map<String, Object> fallback() { return judgeService.diagnoseFallback(); }
}
