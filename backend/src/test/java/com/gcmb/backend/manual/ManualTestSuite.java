package com.gcmb.backend.manual;

import java.util.List;

public record ManualTestSuite(String module, String name, List<ManualTestCase> cases) {
    public ManualTestSuite {
        cases = List.copyOf(cases);
    }
}
