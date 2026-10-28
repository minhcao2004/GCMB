/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: Nghiệp vụ quản lý phòng và lịch sử trạng thái tại cơ sở.
 */

package com.gcmb.backend.branch.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.branch.dto.RoomCommand;
import com.gcmb.backend.branch.dto.RoomStatusCommand;
import com.gcmb.backend.branch.dto.RoomView;
import com.gcmb.backend.branch.repository.BranchRoomRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BranchRoomService {
    private final BranchRoomRepository repo;
    public BranchRoomService(BranchRoomRepository repo) { this.repo=repo; }
    private UUID branch(Identity who) {
        if(who==null||who.branchId()==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tài khoản chưa được gán cơ sở");
        return who.branchId();
    }
    private UUID writableBranch(Identity who) {
        UUID id = branch(who);
        repo.requireActive(id); // BR-177, kiểm tra lại trong transaction.
        return id;
    }
    private RoomCommand validate(RoomCommand c) {
        if(c==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung thông tin phòng.");
        if(c.code()==null||c.code().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung mã phòng.");
        if(c.name()==null||c.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung tên phòng.");
        if(c.roomTypeId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung hạng phòng.");
        if(c.floorNumber()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung tầng.");
        if(c.capacity()==null||c.capacity()<1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sức chứa phải là số nguyên lớn hơn 0.");
        return c;
    }
    public List<RoomView> list(Identity who){return repo.list(branch(who));}
    public List<RoomView> types(Identity who){branch(who);return repo.types();}
    public RoomView create(Identity who,RoomCommand c){try{return repo.create(writableBranch(who),validate(c));}catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã phòng đã tồn tại hoặc loại phòng không còn hoạt động");}}
    public RoomView update(Identity who,UUID id,RoomCommand c){try{RoomView v=repo.update(writableBranch(who),id,validate(c));if(v==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phòng");return v;}catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã phòng đã được sử dụng trong cơ sở");}}
    public void delete(Identity who,UUID id){if(!repo.delete(writableBranch(who),id))throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.");}
    @Transactional
    public RoomView status(Identity who,UUID id,RoomStatusCommand c){
        UUID branch=writableBranch(who);
        if(c==null||!List.of("AVAILABLE","UNDER_MAINTENANCE","INACTIVE").contains(c.status())||c.reason()==null||c.reason().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn trạng thái hợp lệ và nhập lý do thay đổi");
        RoomView v=repo.changeStatus(branch,id,who.id(),c);if(v==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phòng trong cơ sở được phân quyền");return v;
    }
}
