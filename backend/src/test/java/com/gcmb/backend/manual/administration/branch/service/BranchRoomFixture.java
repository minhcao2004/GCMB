/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Builds isolated dependencies for the adjacent manual service tests.
 */

package com.gcmb.backend.manual.administration.branch.service;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.branch.dto.RoomView;
import com.gcmb.backend.administration.branch.repository.BranchRoomRepository;
import com.gcmb.backend.administration.branch.service.BranchRoomService;
import com.gcmb.backend.manual.support.ManualFixture;
import java.util.List;
import org.mockito.invocation.InvocationOnMock;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class BranchRoomFixture extends ManualFixture {
    public final BranchRoomRepository rooms;
    public final BranchRoomService service;

    public BranchRoomFixture(Object command) {
        super(command);
        actor=new Identity(U,"manager","BRANCH_MANAGER","/",B,"Branch A");
        f.put("roomView",new RoomView(R,"P01","Room A",1,RT,"QUEEN","Queen","CINEMA",4,"","AVAILABLE","",null,"READY"));
        rooms=dependency(BranchRoomRepository.class,this::roomRepository);
        service=new BranchRoomService(rooms);
    }

    private Object roomRepository(InvocationOnMock i)throws Throwable {
        return configured("room."+i.getMethod().getName(),()->switch(i.getMethod().getName()) {
            case "list","types"->List.of(f.get("roomView"));
            case "create","update","changeStatus"->f.get("roomView");
            case "delete"->true;
            default->null;
        });
    }
}
