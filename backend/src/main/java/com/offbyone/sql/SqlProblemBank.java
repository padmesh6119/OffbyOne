package com.offbyone.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class SqlProblemBank {
    private final Map<String, SqlProblem> bySlug;

    public SqlProblemBank(ObjectMapper objectMapper) throws Exception {
        List<SqlProblem> all = objectMapper.readValue(
                new ClassPathResource("sql-problems.json").getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SqlProblem.class));
        this.bySlug = all.stream().collect(Collectors.toMap(SqlProblem::slug, p -> p));
    }

    public List<SqlProblem> findAll() { return List.copyOf(bySlug.values()); }
    public Optional<SqlProblem> findBySlug(String slug) { return Optional.ofNullable(bySlug.get(slug)); }
}
