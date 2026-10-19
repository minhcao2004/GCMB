/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Builds isolated dependencies for the adjacent manual service tests.
 */

package com.gcmb.backend.manual.administration.branch.service;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.branch.service.BranchAdministrationService;
import com.gcmb.backend.manual.support.ManualFixture;
import com.gcmb.backend.manual.support.ManualJdbc;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class BranchAdministrationFixture extends ManualFixture {
    public final BranchAdministrationService service;

    public BranchAdministrationFixture(Object command) {
        super(command);
        ManualJdbc.attach(this);
        actor=new Identity(U,"hom","HEAD_OFFICE_MANAGER","/",B,"Branch A");
        f.put("branchMap",new LinkedHashMap<>(Map.of("branchId",B,"code","HT_01","branchName","Branch A","status","ACTIVE","managerId",id(99))));
        service=new BranchAdministrationService(db);
    }
}
