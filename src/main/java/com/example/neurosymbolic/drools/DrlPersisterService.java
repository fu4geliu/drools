package com.example.neurosymbolic.drools;

import com.example.neurosymbolic.ir.model.RuleDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Compiles IR to DRL and writes the result to disk when rules are saved.
 */
@Service
public class DrlPersisterService {

    private static final String DRL_PACKAGE = "com.example.neurosymbolic.rules";

    private final IrToDrlCompiler compiler;
    private final String outputDir;

    public DrlPersisterService(
            @Value("${neurosymbolic.drl.output-dir:target/generated-rules}") String outputDir) {
        this.compiler = new IrToDrlCompiler();
        this.outputDir = outputDir;
    }

    /**
     * Compile the given IR document to DRL and write to a file under the configured output directory.
     * Filename is derived from ruleSetId (e.g. "Demo RuleSet 1" -> "Demo_RuleSet_1.drl").
     */
    public Path persist(String ruleSetId, RuleDocument document) throws IOException {
        String drl = compiler.compile(document, DRL_PACKAGE);
        String fileName = sanitizeFileName(ruleSetId) + ".drl";
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Files.writeString(file, drl);
        return file;
    }

    private static String sanitizeFileName(String ruleSetId) {
        if (ruleSetId == null || ruleSetId.isEmpty()) return "unnamed";
        return ruleSetId.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").trim();
    }
}
