/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Groups manual test cases by module and suite name.
 */

package com.gcmb.backend.manual;

import java.util.List;

public record ManualTestSuite(String module, String name, List<ManualTestCase> cases) {
    public ManualTestSuite {
        cases = List.copyOf(cases);
    }
}
