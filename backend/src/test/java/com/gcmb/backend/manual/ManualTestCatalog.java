package com.gcmb.backend.manual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/** Exports design metadata only. Never calls a case executor or assigns a verdict. */
public final class ManualTestCatalog {
    private ManualTestCatalog() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ManualTestCatalog <output.json>");
        var catalog = new ArrayList<Map<String, Object>>();
        for (var suite : ManualTestRegistry.suites()) {
            for (var test : suite.cases()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("suite", suite.name());
                item.put("key", test.key());
                item.put("id", test.documentTestCaseId());
                item.put("sourceId", test.sourceTestCaseId());
                item.put("sourceClass", test.sourceClass());
                item.put("method", test.sourceMethod());
                item.put("level", test.testType());
                item.put("traceability", test.traceability());
                item.put("preconditions", test.preconditions());
                item.put("input", test.input());
                item.put("expected", test.expectedResult());
                catalog.add(item);
            }
        }
        Files.writeString(Path.of(args[0]), new ObjectMapper().writeValueAsString(catalog));
        System.out.println("Exported metadata for " + catalog.size() + " selectable cases. No test case executed.");
    }
}
