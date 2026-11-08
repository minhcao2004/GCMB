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
    private UUID branch(Identity who, String action) {
        if(who==null||who.branchId()==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền "+action+".");
        return who.branchId();
    }
    private void requireWritable(UUID branch) { repo.requireActive(branch); } // BR-177, kiểm tra lại trong transaction.
    private RoomCommand validate(RoomCommand c) {
        if(c==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung Thông tin phòng.");
        if(c.code()==null||c.code().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung Mã phòng.");
        if(c.name()==null||c.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung Tên phòng.");
        if(c.name().trim().length()>200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tên phòng chỉ được chứa tối đa 200 ký tự.");
        if(c.roomTypeId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung Loại phòng.");
        if(c.floorNumber()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung Tầng.");
        if(c.capacity()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung Sức chứa.");
        if(c.capacity()<1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sức chứa phải là số nguyên lớn hơn 0.");
        return c;
    }
    public List<RoomView> list(Identity who){return repo.list(branch(who, "xem đối với phòng"));}
    public List<RoomView> types(Identity who){branch(who, "xem đối với danh mục loại phòng");return repo.types();}
    private ResponseStatusException duplicateCode(UUID branch, String code, Identity who) {
        String branchName=who==null?null:who.branchName();
        if(branchName==null||branchName.isBlank()) branchName=repo.branchName(branch);
        return new ResponseStatusException(HttpStatus.CONFLICT,"Mã phòng “"+code.trim().toUpperCase(java.util.Locale.ROOT)+"” đã tồn tại trong cơ sở "+branchName+". Vui lòng nhập mã khác.");
    }
    public RoomView create(Identity who,RoomCommand c){UUID branch=branch(who,"tạo đối với phòng");RoomCommand valid=validate(c);requireWritable(branch);if(repo.codeExists(branch,valid.code(),null))throw duplicateCode(branch,valid.code(),who);String duplicateReason=duplicateCode(branch,valid.code(),who).getReason();try{return repo.create(branch,valid);}catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,duplicateReason,e);}}
    public RoomView update(Identity who,UUID id,RoomCommand c){UUID branch=branch(who,"cập nhật đối với phòng");RoomCommand valid=validate(c);requireWritable(branch);if(repo.codeExists(branch,valid.code(),id))throw duplicateCode(branch,valid.code(),who);String duplicateReason=duplicateCode(branch,valid.code(),who).getReason();try{RoomView v=repo.update(branch,id,valid);if(v==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phòng");return v;}catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,duplicateReason,e);}}
    public void delete(Identity who,UUID id){UUID branch=branch(who,"xóa đối với phòng");requireWritable(branch);if(!repo.delete(branch,id))throw new ResponseStatusException(HttpStatus.CONFLICT,"Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.");}
    @Transactional
    public RoomView status(Identity who,UUID id,RoomStatusCommand c){
        UUID branch=branch(who,"thay đổi trạng thái đối với phòng");
        if(c==null||c.status()==null||c.status().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung Trạng thái phòng.");
        if(!List.of("AVAILABLE","UNDER_MAINTENANCE","INACTIVE").contains(c.status())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trạng thái phòng không hợp lệ.");
        if(c.reason()==null||c.reason().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vui lòng bổ sung Lý do thay đổi.");
        requireWritable(branch);
        RoomView v=repo.changeStatus(branch,id,who.id(),c);if(v==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy phòng trong cơ sở được phân quyền");return v;
    }
}
