package com.gcmb.backend.administration.branch.service;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.branch.dto.RoomCommand;
import com.gcmb.backend.administration.branch.dto.RoomStatusCommand;
import com.gcmb.backend.administration.branch.dto.RoomView;
import com.gcmb.backend.administration.branch.repository.BranchRoomRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BranchRoomService {
    private final BranchRoomRepository repo;
    public BranchRoomService(BranchRoomRepository repo) { this.repo=repo; }
    private UUID branch(Identity who) {
        if(who==null||who.branchId()==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tài khoản chưa được gán cơ sở");
        return who.branchId();
    }
    private RoomCommand validate(RoomCommand c) {
        if(c==null||c.code()==null||c.code().isBlank()||c.name()==null||c.name().isBlank()||c.roomTypeId()==null||c.floorNumber()==null||c.capacity()==null||c.capacity()<1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng nhập mã, tên, tầng, loại phòng và sức chứa hợp lệ");
        return c;
    }
    public List<RoomView> list(Identity who){return repo.list(branch(who));}
    public List<RoomView> types(Identity who){branch(who);return repo.types();}
    public RoomView create(Identity who,RoomCommand c){try{return repo.create(branch(who),validate(c));}catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã phòng đã tồn tại hoặc loại phòng không còn hoạt động");}}
    public RoomView update(Identity who,UUID id,RoomCommand c){try{RoomView v=repo.update(branch(who),id,validate(c));if(v==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phòng");return v;}catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã phòng đã được sử dụng trong cơ sở");}}
    public void delete(Identity who,UUID id){if(!repo.delete(branch(who),id))throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể xóa phòng đã có booking hoặc thiết bị; hãy chuyển phòng sang Ngừng hoạt động");}
    @Transactional
    public RoomView status(Identity who,UUID id,RoomStatusCommand c){
        UUID branch=branch(who);
        if(c==null||!List.of("AVAILABLE","CLEANING","UNDER_MAINTENANCE","INACTIVE").contains(c.status())||c.reason()==null||c.reason().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn trạng thái hợp lệ và nhập lý do thay đổi");
        RoomView v=repo.changeStatus(branch,id,who.id(),c);if(v==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phòng trong cơ sở được phân quyền");return v;
    }
}
