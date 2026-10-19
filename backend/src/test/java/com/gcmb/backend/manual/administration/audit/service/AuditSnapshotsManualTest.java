/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 14/10/2026
 * Description: Covers filtering and redaction of audit snapshot data.
 */

package com.gcmb.backend.manual.administration.audit.service;

import com.gcmb.backend.administration.audit.service.AuditSnapshots;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.util.List;

/** One observation per tester-selected case; no assertions or automatic verdict. */
public final class AuditSnapshotsManualTest {
    private AuditSnapshotsManualTest() {}

    public static ManualTestSuite suite() {
        return new ManualTestSuite("Administration", "AuditSnapshots", List.of(
            test(13, "null", "read(null)", null, "Return an empty map; no exception."),
            test(14, "types", "read(approved fields)",
                "{\"name\":\"Room A\",\"capacity\":4,\"active\":true,\"note\":null,\"weekdays\":[1,2]}",
                "Keep name=Room A, capacity=4, active=true, note=null and weekdays=[1,2]; retain value types."),
            test(15, "secrets", "read(root secrets)",
                "{\"username\":\"staff\",\"password\":\"secret\",\"passwordHash\":\"hash\",\"hash\":\"hash\",\"token\":\"token\",\"unknown\":1}",
                "Return only username=staff; remove password, passwordHash, hash, token and unknown."),
            test(16, "nested", "read(nested booking event)",
                "{\"event\":{\"type\":\"BOOKING_CANCELLED\",\"reason\":\"Customer cancelled\",\"password\":\"secret\",\"details\":{\"depositAmount\":50000,\"token\":\"secret\"}}}",
                "Keep event.type, event.reason and details.depositAmount=50000; remove nested password/token."),
            test(17, "lists", "read(nested lists)",
                "{\"combos\":[{\"name\":\"Combo A\",\"hash\":\"secret\"},[{\"price\":100,\"token\":\"secret\"}],\"label\"]}",
                "Keep combos list: {name=Combo A}, [{price=100}], label; remove hash/token at every depth."),
            test(18, "unknown-only", "read(unapproved fields only)",
                "{\"password\":\"secret\",\"resetToken\":\"secret\"}",
                "Return an empty map; neither unapproved field appears in output.")
        ));
    }

    private static ManualTestCase test(int number, String key, String method, String json, String expected) {
        return new ManualTestCase("audit-snapshots/" + key,
                ManualTestCase.identifier("UTC%03d".formatted(number), "UC-08"),
                "AuditSnapshotsManualTest",
                method,
                "Call real AuditSnapshots.read; synthetic JSON only; no database.",
                json == null ? "Java null snapshot" : json,
                expected,
                () -> ManualTestSupport.observe(() -> AuditSnapshots.read(json)));
    }
}
