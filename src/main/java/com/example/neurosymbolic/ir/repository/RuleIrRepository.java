package com.example.neurosymbolic.ir.repository;

import com.example.neurosymbolic.ir.model.RuleDocument;

import java.util.Optional;

/**
 * Minimal repository abstraction for storing and retrieving RuleDocument.
 * For now it's in-memory, but can later be backed by a database or file store.
 */
public interface RuleIrRepository {

    void save(String id, RuleDocument document);

    Optional<RuleDocument> findById(String id);
}

