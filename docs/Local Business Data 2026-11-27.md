# Bộ dữ liệu local chuẩn GCMB — 27/11/2026

Phiên bản bàn giao `GCMB-LOCAL-20261127-R2` gồm bộ dữ liệu đã cân đối tài chính và đã thu gọn tình huống test chức năng. Tài liệu này là đầu mối sử dụng và bàn giao của phiên bản hiện tại.

## Bản dùng chung

- Database đang dùng là `localhost:5432/gcmb`, xác định từ `backend/.env`.
- File SQL tổng hợp để gửi nhóm là `backend/secrets/database-backups/gcmb-local-standard-20261127.sql`. File chứa cấu trúc và dữ liệu của phiên bản chuẩn.
- File SQL có 546.660.917 byte, SHA-256 `082CED643C13DE1D88C887D194143B82E51D13E69F8EB78B1957C62D85014919`. Đã kiểm tra 118 bảng, 116 khối COPY có đủ dấu kết thúc và phần kết thúc dump.
- Bản custom archive tương đương là `backend/secrets/database-backups/gcmb-local-standard-20261127.dump`, được dùng làm nguồn xuất SQL.
- Dung lượng 120.999.948 byte. SHA-256 `210273C11C15147FBE8B604F4B398988B96AA471E0F9B6EAB64A31EF8E5F1196`.
- Định dạng PostgreSQL custom archive, xuất bằng `pg_dump -Fc` và kiểm tra bằng `pg_restore --list`, 1.878 dòng danh mục.
- Tài khoản và password hash hiện tại được giữ trong bản chuẩn. Dữ liệu phiên đăng nhập và giới hạn đăng nhập được bỏ khi xuất để đăng nhập lại sau restore.
- File upload thuộc `backend/data/attachments/` và các object lưu trên cloud được tham chiếu qua metadata. Khi chuyển máy cần mang theo file local tương ứng và dùng cấu hình truy cập kho chứng từ phù hợp.
- Bảng trước/sau tài chính nằm tại `output/business-20261127/GCMB-2026.xlsx`, gồm 216 dòng cơ sở–tháng cùng chi tiết các khoản chi. Kết quả tiếp tục đúng sau thu gọn chức năng.

## Phạm vi dữ liệu

Có 18 cơ sở ACTIVE. Các cơ sở, phòng, loại phòng, tài khoản, nhân viên, phân công, ca làm, quỹ, danh mục, giá, combo, tồn kho nền và tài sản tiếp tục phục vụ nghiệp vụ.

Hoa Bằng và Nguyễn Lương Bằng **86–88** là hai cơ sở test tay. Tài khoản quản lý tương ứng là `qlcs.hoabang` và `qlcs.nguyenluongbang`. Hai cơ sở giữ đầy đủ tình huống đề nghị chi, chi định kỳ, lương, chốt quỹ, bàn giao ca, nhập kho, tồn thấp, thiết bị, điều chuyển, hoàn tiền, booking, hóa đơn và F&B.

16 cơ sở còn lại có dữ liệu vận hành và tập tình huống chức năng được phân bố khác nhau theo mã cơ sở. Nhiều cơ sở có hàng chờ bằng 0. Các hoàn cọc tiền mặt gắn với sổ thu ngân và tiền nộp két lịch sử được giữ để tiếp tục đối soát.

Giai đoạn có doanh số là tháng 2–11/2026. Tháng 11 là kỳ chưa đủ tháng. Tháng 1 và 12 không có phát sinh. Các giao dịch bổ sung thuộc dữ liệu mô phỏng.

## Hồ sơ tài chính

Đơn vị bảng dưới là triệu đồng. Nhân sự là số hồ sơ ACTIVE theo phân công chính hiện tại. F&B tính trên hóa đơn ròng.

| Cơ sở | Nhân sự | Thu | Lợi nhuận | Biên | F&B |
|---|---:|---:|---:|---:|---:|
| GenZ Cinema Âu Cơ | 15 | 1.406,4 | 312,3 | 22,2% | 30,0% |
| GenZ Cinema Cách Mạng Tháng 8 | 16 | 2.037,5 | 656,8 | 32,2% | 34,8% |
| Music Box Cửu Long | 15 | 1.079,2 | 174,0 | 16,1% | 28,0% |
| GenZ Cinema Nguyễn Súy | 14 | 1.043,8 | 174,5 | 16,7% | 26,4% |
| GenZ Cinema Nguyễn Thái Bình | 16 | 1.804,0 | 515,4 | 28,6% | 33,2% |
| GenZ Cinema Phan Xích Long | 14 | 1.754,7 | 549,4 | 31,3% | 37,4% |
| Music Box Quận 10 | 16 | 1.255,4 | 256,5 | 20,4% | 32,6% |
| GenZ Box Tân Bình | 16 | 911,2 | 90,7 | 9,9% | 24,0% |
| GenZ Cinema Hoa Bằng | 14 | 1.412,8 | 369,8 | 26,2% | 27,6% |
| GenZ Cinema Hoàng Công Chất | 13 | 921,5 | 145,0 | 15,7% | 24,3% |
| GenZ Cinema Hoàng Ngân | 14 | 1.464,0 | 384,0 | 26,2% | 32,8% |
| GenZ Cinema Nguyễn Lương Bằng | 14 | 1.176,7 | 244,6 | 20,8% | 27,1% |
| GenZ Cinema Nguyễn Lương Bằng 86-88 | 16 | 1.845,0 | 562,4 | 30,5% | 30,9% |
| GenZ Cinema Tân Khai | 13 | 1.062,4 | 199,1 | 18,7% | 25,8% |
| GenZ Music Xuân Thủy | 15 | 1.331,8 | 327,1 | 24,6% | 33,6% |
| GenZ Cinema Nhật Chiêu | 15 | 1.576,8 | 425,8 | 27,0% | 36,0% |
| GenZ Cinema Nguyễn Văn Lộc | 13 | 1.083,3 | 229,8 | 21,2% | 27,3% |
| GenZ Cinema Triều Khúc | 13 | 842,0 | 98,3 | 11,7% | 21,9% |

Thu vận hành toàn chuỗi là **24.008.535.000 đồng**, tổng chi **18.292.922.000 đồng**, lợi nhuận **5.715.613.000 đồng**. Theo số tiền khách trả trên hóa đơn, lợi nhuận là **3.823.933.000 đồng**. Cả 180 tháng cơ sở có vận hành đều dương.

Dashboard và ngữ cảnh trợ lý lấy hóa đơn SETTLED ròng cộng cọc FORFEIT. Tab Báo cáo kinh doanh dùng tiền khách trả trên hóa đơn theo nguồn mà backend chọn. Chi phí gồm OPEX đã ghi nhận và lương/ứng lương. Chi định kỳ nằm trong OPEX. CAPEX hiển thị riêng.

## Số tình huống còn lại

Đây là số đếm thực tế sau thu gọn. Hoàn cọc chờ gồm các khoản gắn giao dịch thu tiền mặt lịch sử. Cột đề nghị chi đếm các trạng thái chưa thanh toán. Cột phiếu nhập đếm DRAFT và CANCELLED.

| Cơ sở | Hoàn cọc chờ | Đề nghị chi | Phiếu nhập | Sự cố | Chốt quỹ | Nhận ca |
|---|---:|---:|---:|---:|---:|---:|
| GenZ Cinema Âu Cơ | 3 | 1 | 1 | 5 | 7 | 17 |
| GenZ Cinema Cách Mạng Tháng 8 | 3 | 0 | 0 | 2 | 10 | 30 |
| Music Box Cửu Long | 3 | 0 | 0 | 0 | 1 | 4 |
| GenZ Cinema Nguyễn Súy | 1 | 0 | 0 | 0 | 1 | 4 |
| GenZ Cinema Nguyễn Thái Bình | 2 | 1 | 0 | 1 | 6 | 16 |
| GenZ Cinema Phan Xích Long | 5 | 0 | 0 | 0 | 1 | 4 |
| Music Box Quận 10 | 6 | 0 | 0 | 1 | 5 | 10 |
| GenZ Box Tân Bình | 4 | 0 | 1 | 1 | 7 | 16 |
| GenZ Cinema Hoa Bằng | 7 | 4 | 2 | 11 | 293 | 1172 |
| GenZ Cinema Hoàng Công Chất | 5 | 1 | 1 | 0 | 8 | 16 |
| GenZ Cinema Hoàng Ngân | 3 | 0 | 0 | 0 | 1 | 4 |
| GenZ Cinema Nguyễn Lương Bằng | 4 | 0 | 0 | 2 | 3 | 12 |
| GenZ Cinema Nguyễn Lương Bằng 86-88 | 8 | 4 | 2 | 11 | 293 | 1172 |
| GenZ Cinema Tân Khai | 2 | 3 | 1 | 5 | 9 | 22 |
| GenZ Music Xuân Thủy | 3 | 1 | 1 | 1 | 8 | 26 |
| GenZ Cinema Nhật Chiêu | 2 | 1 | 2 | 3 | 5 | 17 |
| GenZ Cinema Nguyễn Văn Lộc | 5 | 0 | 2 | 1 | 5 | 18 |
| GenZ Cinema Triều Khúc | 2 | 0 | 0 | 0 | 1 | 4 |

Hai cơ sở test tay đều có đủ 5 trạng thái đề nghị chi, 2 lịch chi định kỳ, 19 kỳ đã trả, 10 phiếu nhập đã ghi sổ, 1 mặt hàng dưới ngưỡng tồn và 6 điều chuyển. Hoa Bằng có 99 bút toán lương và 82 yêu cầu hoàn tiền. Cơ sở 86–88 có 108 bút toán lương và 96 yêu cầu hoàn tiền.

## Nạp bản chuẩn

### Nạp file SQL tổng hợp

File SQL được xuất bằng PostgreSQL 18, dùng `psql` của PostgreSQL 18 để nhập vào database local trống. File sử dụng COPY để nạp lượng dữ liệu lớn. Chạy lệnh dưới đây từ gốc repository, với tên database trống của thành viên.

```powershell
$pg = "C:/Program Files/PostgreSQL/18/bin"
& "$pg/createdb.exe" -h 127.0.0.1 -p 5432 -U postgres -T template0 gcmb_standard_restore
& "$pg/psql.exe" -X -h 127.0.0.1 -p 5432 -U postgres -d gcmb_standard_restore -v ON_ERROR_STOP=1 --single-transaction -f backend/secrets/database-backups/gcmb-local-standard-20261127.sql
```

Nếu nhập thất bại, `ON_ERROR_STOP` và `--single-transaction` giúp hoàn tác toàn bộ lần nhập. Chủ sở hữu các đối tượng là tài khoản thực hiện nhập. Sau khi cấp quyền ứng dụng và cập nhật cấu hình kết nối local, chạy ứng dụng như hướng dẫn bên dưới. Chọn một định dạng SQL hoặc custom archive cho mỗi lần nhập vào database trống.

File SQL nằm trong thư mục bị Git bỏ qua. Gửi file trực tiếp hoặc qua kho chia sẻ nội bộ cùng tài liệu này. Merge mã nguồn cung cấp script và tài liệu. Để xem chứng từ trên máy khác, mang theo thư mục upload và cấu hình kho chứng từ đã nêu ở trên.

### Nạp custom archive

Để dùng lại đúng phiên bản này, nạp bản dump chuẩn vào PostgreSQL local trống. Bản dump đã chứa kết quả cân đối và thu gọn.

Trước khi thay database hiện có, dừng backend và tạo backup trạng thái đang dùng. Thao tác dưới đây dùng database local mới `gcmb_standard_restore` để kiểm tra trước khi chuyển cấu hình backend.

```powershell
$pg = "C:/Program Files/PostgreSQL/18/bin"
& "$pg/createdb.exe" -h 127.0.0.1 -p 5432 -U postgres -T template0 gcmb_standard_restore
& "$pg/pg_restore.exe" -h 127.0.0.1 -p 5432 -U postgres -d gcmb_standard_restore --no-owner --no-privileges --single-transaction --exit-on-error backend/secrets/database-backups/gcmb-local-standard-20261127.dump
```

Tài khoản ứng dụng cần quyền với các schema và đối tượng sau restore. Nếu dùng `postgres` để restore với `--no-owner`, chủ sở hữu là tài khoản restore. Có thể dùng tài khoản ứng dụng local đã cấp đủ quyền tạo schema làm người restore. Sau khi đối soát bản phục hồi, cập nhật `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` trong `backend/.env` theo tài khoản local đó.

Khởi động ứng dụng bằng `npm start` trong `frontend/`. Trang đăng nhập là `http://localhost:3000/dang-nhap`. Cổng 8080 và 3000 cần rảnh trước khi chạy launcher. Database hiện tại trên máy phát triển đã có bộ chuẩn này.

## Lịch sử chuẩn bị dữ liệu

Các script tạo, cân đối và cleanup từng bước đã được xóa theo yêu cầu dọn source. Để nạp lại bộ dữ liệu hiện tại, dùng file SQL hoặc custom archive chuẩn theo hướng dẫn ở trên.

Bước cân đối ban đầu thêm 60.076 lượt bán có booking, phân phòng, hóa đơn, thanh toán và bút toán, rút 70 chiến dịch quảng cáo theo bộ sinh và thêm 24 hồ sơ lễ tân nhận việc sau kỳ bán mẫu. Quy mô, mùa vụ, tỷ lệ F&B và biên lợi nhuận dùng hồ sơ riêng theo mã cơ sở.

Bước thu gọn loại 65.293 dòng trên 29 bảng khỏi các bảng vận hành. Gồm 629 bộ booking hủy–hoàn cọc bằng ngân hàng cùng dữ liệu liên quan, 56 đề nghị chi chưa trả, 23 phiếu nhập nháp/hủy, 29 đơn F&B hủy/từ chối, 154 sự cố, 21 điều chuyển chưa hoàn tất, 13 đề nghị cấp tiền lẻ, 14 giao dịch ngân hàng lỗi và phần lớn báo cáo chốt quỹ/bàn giao ca sao chép.

## Giảm nhẹ lượt bán bổ sung

Bước giảm nhẹ đã chọn cố định theo mã giao dịch để giảm 10% lượt bán BIZ26 ở từng cơ sở–tháng ngoài Hoa Bằng và Nguyễn Lương Bằng 86–88. Tổng cộng giảm 5.283 booking cùng hóa đơn và dòng liên quan, còn 54.793 lượt bán BIZ26. File SQL giảm 18.803.257 byte, từ 565,5 MB xuống 546,7 MB.

Doanh thu của phần giảm được phân bổ vào F&B của hóa đơn mẫu còn giữ. Giá menu và giá phòng được giữ, số phần ăn tối đa sáu trên một đơn. Phần chênh lệch làm tròn được ghi giảm giá trên một hóa đơn mỗi cơ sở–tháng. Các số tiền hóa đơn, thanh toán, yêu cầu thanh toán, giao dịch ngân hàng và sổ cái được đối chiếu bằng nhau. Tổng thu, chi và lợi nhuận từng cơ sở–tháng giữ đúng. Cơ cấu F&B và số giao dịch của 16 cơ sở thay đổi theo bảng hiện tại.

Bước giảm nhẹ đã được thực hiện trong transaction và kiểm tra chữ ký dữ liệu. Marker áp dụng nằm tại `local_biz26.compaction`. Bản dump chuẩn hiện tại dùng để khôi phục trạng thái sau giảm nhẹ.

Đã chạy thử bước giảm nhẹ trong transaction và rollback trước khi áp dụng. Sau áp dụng, chữ ký dữ liệu nền cùng các bảng của hai cơ sở test tay khớp trước/sau. Bộ kiểm tra controller hiện có tiếp tục đối chiếu thành công 54 tổ hợp cơ sở–tháng. Workbook đã được cập nhật số hóa đơn và cơ cấu F&B.

Các backup trung gian, file SQL gốc cũ và thư mục `previous/` đã được xóa theo yêu cầu dọn file ngày 27/11/2026. Thư mục bàn giao hiện giữ ba file được liệt kê bên dưới.

## Nguồn gốc và bản lưu phục hồi

Dữ liệu gốc khớp các script `populate-operating-*.sql` cùng `operating-profile.sql` trong `backend/secrets/operating-data-tools/scripts/`. Bảng nhập nguồn trống và hóa đơn gốc không có marker PV-DASH hoặc TST-GCMB-EXCEL-2026 tại thời điểm chẩn đoán. Lỗ ban đầu chủ yếu do số lượt bán thấp so với lương và chi phí cố định.

Marker BIZ26 nằm trong mã giao dịch và schema `local_biz26`. Schema `local_workflow_thin` giữ hồ sơ đã thu gọn. Việc chọn mẫu dùng mã cơ sở với MD5 cố định.

Ba file hiện giữ trong `backend/secrets/database-backups/`, đã được Git bỏ qua.

- `gcmb-local-standard-20261127.sql` là file SQL chuẩn để gửi nhóm.
- `gcmb-local-standard-20261127.dump` là custom archive của cùng bộ dữ liệu, dùng khôi phục trạng thái chuẩn.
- `local-workflow-thin-20261127.dump` lưu các dòng bị thu gọn, 7.283.267 byte, SHA-256 `A030D26915C3F01D2351874D1E1477C05D7982D8B2F4B4A0FE535E234D4CAFC8`. Đây là bản lưu lịch sử các dòng đã thu gọn. Các lệnh cleanup từng bước đã được dọn khỏi source.

## Kiểm tra và giới hạn đã xác nhận

- Báo cáo 216 dòng và các nhóm chi khớp sau cleanup/reapply BIZ26. Báo cáo tiếp tục khớp sau thu gọn chức năng.
- Hai cơ sở test tay khớp toàn bộ số đếm trước/sau thu gọn. Các cơ sở khác có tập mẫu khác nhau.
- Đã diễn tập phục hồi các dòng thu gọn trong transaction rồi rollback. Apply lặp lại thành công.
- Backend đã khởi động. Controller dashboard cấp cao và ngữ cảnh trợ lý đã đối chiếu với SQL ở 54 tổ hợp cơ sở–tháng, cùng dashboard QLCS và số khách trả của hai cơ sở test tay.
- Sáu tài khoản seed ACTIVE, password hash được giữ. Đăng nhập HTTP bằng sáu mật khẩu cũ và hội thoại Gemini thật còn chưa xác nhận.
- Bản dump chuẩn đã kiểm tra danh mục và giải mã toàn bộ archive thành công, đồng thời xác nhận dữ liệu phiên đăng nhập được loại khỏi bản xuất. Chưa diễn tập restore toàn bộ bản chuẩn sang database mới.

## Ngoài tài liệu hiện có

Phiên bản này chốt kết quả cân đối tài chính và thu gọn chức năng trong cùng một bản nạp. Tài liệu này thay các mô tả theo từng lần thao tác để bàn giao. Marker dữ liệu được mô tả ở trên. Migration và checksum Flyway giữ theo source hiện hành.

Đã xuất thêm định dạng SQL tổng hợp từ custom archive chuẩn bằng `pg_restore --no-owner --no-privileges`. Bước xuất và kiểm tra cấu trúc file đã hoàn tất. Chưa diễn tập nhập toàn bộ file SQL vào database mới.

Bản sửa hiển thị ngày dùng `frontend/src/shared/utils/dateLabel.js` cho chốt quỹ, tồn kho, nhân sự, lịch ca và nhãn so sánh dashboard. Timestamp `2026-11-19T17:00:00.000Z` hiển thị `20/11/2026` theo giờ Việt Nam. Đã kiểm tra 11 trường hợp trước khi xóa file test theo yêu cầu. Đoạn mô tả “Nhật ký chỉ đọc” đã được bỏ khỏi màn nhật ký hệ thống.
