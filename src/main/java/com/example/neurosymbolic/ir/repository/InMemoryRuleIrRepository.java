package com.example.neurosymbolic.ir.repository;

import com.example.neurosymbolic.ir.model.RuleDocument;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryRuleIrRepository implements RuleIrRepository {

    private final Map<String, RuleDocument> storage = new ConcurrentHashMap<>();

    @Override
    public void save(String id, RuleDocument document) {
        storage.put(id, document);
    }

    @Override
    public Optional<RuleDocument> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }
}

