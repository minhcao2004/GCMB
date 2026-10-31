/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Runs executable Combo Package Management manual unit cases without a live database.
 */
package com.gcmb.backend.manual.combopackagemanagement;

import com.gcmb.backend.pricing.dto.*;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public final class ComboPackageManagementManualRunner {
    static final String SERVICE="backend/src/main/java/com/gcmb/backend/pricing/service/RoomCatalogService.java";
    static final String CONTROLLER="backend/src/main/java/com/gcmb/backend/pricing/controller/RoomCatalogController.java";
    static final String UI="frontend/src/features/pricing/pages/RoomCatalogPage.js";
    static final String API="frontend/src/features/pricing/services/roomCatalogApi.js";
    static final String SECURITY="backend/src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java";
    static final String SESSION="backend/src/main/java/com/gcmb/backend/auth/security/SessionFilter.java";
    static final String AUDIT="backend/src/main/java/com/gcmb/backend/audit/service/MutationAudit.java";
    static final String V1="backend/src/main/resources/db/migration/V1__initial_schema.sql";
    static final String V3="backend/src/main/resources/db/migration/V3__room_price_catalog.sql";
    // Offline adapter keys for seed codes; these are not asserted real database UUIDs.
    static final UUID QUEEN=UUID.nameUUIDFromBytes("RT-QN".getBytes(StandardCharsets.UTF_8));
    static final UUID KING=UUID.nameUUIDFromBytes("RT-KG".getBytes(StandardCharsets.UTF_8));
    static final UUID COMBO=UUID.nameUUIDFromBytes("CIN_C1_QN".getBytes(StandardCharsets.UTF_8));
    static final UUID ABSENT=new UUID(0,0);
    private ComboPackageManagementManualRunner() {}
    public static void main(String[] args) {
        List<ManualTest> tests=new ArrayList<>();
        tests.addAll(CreateComboPackageManualCases.cases());
        tests.addAll(UpdateComboPackageManualCases.cases());
        tests.addAll(DeleteComboPackageManualCases.cases());
        tests.sort(Comparator.comparing(ManualTest::id));
        if(tests.size()!=66)throw new IllegalStateException("Expected 66 cases, got "+tests.size());
        Set<String> ids=new HashSet<>();
        for(ManualTest test:tests)
            if(!test.id().matches("CPM-\\d{3}")||!ids.add(test.id()))
                throw new IllegalStateException("Invalid/duplicate ID: "+test.id());
        if(args.length==0){menu(tests);return;}
        switch(args[0]){
            case "--all" -> run(tests);
            case "--list" -> list(tests);
            case "--case" -> {
                if(args.length!=2)throw new IllegalArgumentException("--case <ID>");
                List<ManualTest> selected=tests.stream().filter(t->t.id().equalsIgnoreCase(args[1])).toList();
                if(selected.isEmpty())throw new IllegalArgumentException("Unknown case: "+args[1]);
                run(selected);
            }
            default -> throw new IllegalArgumentException("Options: --all | --list | --case <ID>");
        }
    }
    private static void menu(List<ManualTest> tests){
        try(Scanner scanner=new Scanner(System.in)){
            while(true){
                System.out.println("\nCOMBO PACKAGE MANAGEMENT\n1. List test cases\n2. Run all test cases\n3. Run one test case theo ID\n0. Exit");
                if(!scanner.hasNextLine())return;
                switch(scanner.nextLine().trim()){
                    case "0" -> {return;}
                    case "1" -> list(tests);
                    case "2" -> run(tests);
                    case "3" -> {
                        System.out.print("Test Case ID: ");
                        if(!scanner.hasNextLine())return;
                        String id=scanner.nextLine().trim();
                        run(tests.stream().filter(t->t.id().equalsIgnoreCase(id)).toList());
                    }
                    default -> System.out.println("Select 0, 1, 2 or 3.");
                }
            }
        }
    }
    private static void list(List<ManualTest> tests){
        tests.forEach(t->System.out.println(t.id()+" | "+t.uc()+" | "+t.input()));
        System.out.println("Total: "+tests.size());
    }
    private static void run(List<ManualTest> tests){
        System.out.println("MODE: production calls + recording JDBC; NO LIVE DATABASE.");
        if(tests.isEmpty()){System.out.println("No matching case.");return;}
        int passed=0;
        for(ManualTest t:tests){
            TestResult r;
            try{r=t.action().run();}
            catch(Throwable e){r=check(false,"Unexpected test/environment exception: "+e);}
            if(r.passed())passed++;
            System.out.println("\n["+t.id()+"] "+t.uc());
            System.out.println("Source      : "+t.source());
            System.out.println("Precondition: "+t.pre());
            System.out.println("Input       : "+t.input());
            System.out.println("Expected    : "+t.expected());
            System.out.println("Actual      : "+r.actual());
            System.out.println("VERDICT     : "+(r.passed()?"PASSED":"FAILED"));
        }
        System.out.printf("%nSUMMARY: total=%d, passed=%d, failed=%d%n",tests.size(),passed,tests.size()-passed);
    }
    static ManualTest test(String id,String uc,String source,String pre,String input,String expected,Action action){
        return new ManualTest(id,uc,source,pre,input,expected,action);
    }
    @FunctionalInterface interface Action{TestResult run()throws Exception;}
    record ManualTest(String id,String uc,String source,String pre,String input,String expected,Action action){}
    record TestResult(boolean passed,String actual){}
    static TestResult check(boolean passed,String actual){return new TestResult(passed,actual);}
    static TestResult all(TestResult... results){
        return check(Arrays.stream(results).allMatch(TestResult::passed),
            String.join("; ",Arrays.stream(results).map(TestResult::actual).toList()));
    }
    static String source(String relative)throws Exception{
        Path root=Path.of("").toAbsolutePath();
        while(root!=null&&!Files.exists(root.resolve("backend/src/main")))root=root.getParent();
        if(root==null)throw new IllegalStateException("Run inside GCMB workspace");
        return Files.readString(root.resolve(relative),StandardCharsets.UTF_8);
    }
    static TestResult contains(String path,String... fragments)throws Exception{
        String s=source(path);
        List<String> missing=Arrays.stream(fragments).filter(f->!s.contains(f)).toList();
        return check(missing.isEmpty(),"Source inspection "+path+": "+(missing.isEmpty()?"required fragments found":"missing "+missing));
    }
    static String between(String s,String first,String next){
        int a=s.indexOf(first),b=a<0?-1:s.indexOf(next,a+first.length());
        if(a<0||b<0)throw new IllegalStateException("Source adapter cannot locate "+first+" / "+next);
        return s.substring(a,b);
    }
    static PriceCommand command(String code,String name,UUID room,Integer duration,Integer from,Integer to,String price){
        return new PriceCommand(room,code,name,"2 giờ xem phim; 1 khô gà; 2 đồ uống tự chọn",
            duration,from,to,price==null?null:new BigDecimal(price));
    }
    static PriceCommand valid(){return command("CPM_NEW_QN","Combo Queen mới",QUEEN,120,0,1440,"236000");}
    static PriceCommand change(String field,Object value){
        return changeFrom(valid(),field,value);
    }
    static PriceCommand edited(String field,Object value)throws Exception{
        Map<String,Object> r=new RecordingJdbc().records.get(COMBO);
        PriceCommand current=new PriceCommand(QUEEN,"CIN_C1_QN",(String)r.get("name"),(String)r.get("description"),
            (Integer)r.get("duration_minutes"),(Integer)r.get("minute_from"),(Integer)r.get("minute_to"),(BigDecimal)r.get("price"));
        return changeFrom(current,field,value);
    }
    private static PriceCommand changeFrom(PriceCommand c,String field,Object value){
        return new PriceCommand(field.equals("room")?(UUID)value:c.roomTypeId(),
            field.equals("code")?(String)value:c.code(),field.equals("name")?(String)value:c.name(),
            field.equals("description")?(String)value:c.description(),
            field.equals("duration")?(Integer)value:c.durationMinutes(),
            field.equals("from")?(Integer)value:c.minuteFrom(),field.equals("to")?(Integer)value:c.minuteTo(),
            field.equals("price")?(value==null?null:new BigDecimal(value.toString())):c.price());
    }
    static void validate(PriceCommand c)throws Exception{
        Method m=RoomCatalogService.class.getDeclaredMethod("validatePrice",String.class,PriceCommand.class);
        m.setAccessible(true);
        try{m.invoke(null,"COMBO",c);}
        catch(InvocationTargetException e){
            if(e.getCause() instanceof RuntimeException cause)throw cause;
            throw e;
        }
    }
    static TestResult reject(PriceCommand c,String message)throws Exception{
        try{validate(c);return check(false,"Production validator ACCEPTED invalid input; expected rejection: "+message);}
        catch(ResponseStatusException e){
            return check(e.getStatusCode().value()==400&&Objects.equals(message,e.getReason()),
                "Production validator: status="+e.getStatusCode().value()+", message="+e.getReason()+"; expected="+message);
        }
    }
    static TestResult invalidTime(PriceCommand c)throws Exception{
        try{validate(c);return check(false,"Production validator accepted out-of-day minute");}
        catch(ResponseStatusException e){return check(e.getStatusCode().value()==400,"Validator rejected invalid minute: "+e.getReason());}
    }
    static TestResult accept(PriceCommand c,boolean update)throws Exception{
        RecordingJdbc jdbc=new RecordingJdbc();
        RoomCatalogService service=new RoomCatalogService(jdbc);
        try{
            PriceCatalogItem got=update?service.updatePrice("COMBO",COMBO,c):service.createPrice("COMBO",c.roomTypeId(),c);
            String code=update?"CIN_C1_QN":c.code().trim().toUpperCase(Locale.ROOT);
            UUID room=update&&c.roomTypeId()==null?QUEEN:c.roomTypeId();
            boolean ok=got.code().equals(code)&&got.roomTypeId().equals(room)&&got.name().equals(c.name().trim())
                &&got.description().equals(c.description()==null?"":c.description().trim())
                &&got.durationMinutes().equals(c.durationMinutes())&&got.minuteFrom().equals(c.minuteFrom())
                &&got.minuteTo().equals(c.minuteTo())&&got.price().compareTo(c.price())==0&&got.active()
                &&jdbc.insertCount==1&&(!update||!Boolean.TRUE.equals(jdbc.records.get(COMBO).get("active")));
            return check(ok,"Production "+(update?"updatePrice":"createPrice")+" + RowMapper, recording JDBC: "+got
                +"; INSERT count="+jdbc.insertCount+"; no live DB");
        }catch(ResponseStatusException e){
            return check(false,"Production service rejected valid input: status="+e.getStatusCode().value()+", message="+e.getReason());
        }
    }
    static TestResult toast(String expected,boolean deletion)throws Exception{
        String body=deletion?between(source(UI),"const remove =","// Chỉ gửi yêu cầu")
            :between(source(UI),"const submit =","const remove =");
        return check(body.contains(expected),"UI source expected “"+expected+"”; current combo messages="
            +body.lines().filter(l->l.contains("combo thành công")||l.contains("Xóa ${noun}")).toList());
    }
    static TestResult missingRoom(boolean nonexistent)throws Exception{
        RecordingJdbc jdbc=new RecordingJdbc();
        try{new RoomCatalogService(jdbc).createPrice("COMBO",nonexistent?ABSENT:null,valid());
            return check(false,"Missing room accepted; inserts="+jdbc.insertCount);}
        catch(ResponseStatusException e){
            boolean ok=nonexistent?e.getStatusCode().value()==404
                :e.getStatusCode().value()==400&&"Vui lòng bổ sung Hạng áp dụng.".equals(e.getReason());
            return check(ok&&jdbc.insertCount==0,"Room lookup: "+e.getStatusCode()+" "+e.getReason()+"; inserts="+jdbc.insertCount);
        }
    }
    static TestResult duplicate()throws Exception{
        RecordingJdbc jdbc=new RecordingJdbc();
        try{new RoomCatalogService(jdbc).createPrice("COMBO",QUEEN,change("code","CIN_C1_QN"));}
        catch(ResponseStatusException e){
            String expected="Mã combo “CIN_C1_QN” đã tồn tại trong danh sách combo đang hoạt động. Vui lòng nhập mã khác.";
            return check(e.getStatusCode().value()==409&&expected.equals(e.getReason())&&jdbc.insertCount==0,
                "Production duplicate check: "+e.getReason()+"; inserts="+jdbc.insertCount);
        }
        return check(false,"Duplicate not rejected");
    }
    static TestResult confirmation(String action)throws Exception{
        String body=between(source(UI),"const submit =","const remove =");
        return check(body.contains("Xác nhận "+action+" combo?")
            &&Pattern.compile("confirm|pendingConfirm|review",Pattern.CASE_INSENSITIVE).matcher(body).find(),
            "UI submit source: direct create/update; exact MSG14 prefix="+body.contains("Xác nhận "+action+" combo?"));
    }
    static TestResult createIdempotency()throws Exception{
        String migration=source(V1)+source(V3);
        String create=between(source(SERVICE),"public PriceCatalogItem createPrice(","public PriceCatalogItem updatePrice(");
        boolean unique=Pattern.compile("(?is)CREATE\\s+UNIQUE\\s+INDEX[^;]*ON\\s+(?:gcmb\\.)?combo\\s*\\([^)]*code[^)]*\\)[^;]*WHERE[^;]*active").matcher(migration).find();
        boolean serialized=create.contains("pg_advisory_xact_lock")||create.contains("SERIALIZABLE")||create.contains("idempotency_key");
        return check(unique||serialized,"Source/schema: active-code unique index="+unique+", serialization/idempotency guard="+serialized
            +"; COUNT(active) then MAX(version)+INSERT and UNIQUE(code,version) alone do not establish concurrent idempotency");
    }
    static TestResult security(boolean wrongRole)throws Exception{
        TestResult role=contains(SECURITY,".requestMatchers(\"/api/v1/head-office/**\").hasRole(\"HEAD_OFFICE_MANAGER\")",".anyRequest().denyAll()");
        return wrongRole?all(role,contains(SECURITY,"Bạn không có quyền"))
            :all(role,contains(SESSION,"auth.authenticate(token(request))","if (identity != null)","SecurityContextHolder"),
                contains("backend/src/main/java/com/gcmb/backend/auth/service/AuthService.java","if (token == null","repository.findSession(AuthCrypto.hash(token))"),
                contains("backend/src/main/java/com/gcmb/backend/auth/repository/AuthRepository.java",
                    "t.revoked_at IS NULL AND t.expires_at>CURRENT_TIMESTAMP AND u.status='ACTIVE'","return users.isEmpty() ? null"));
    }
    static TestResult csrf()throws Exception{
        String s=source(SECURITY);
        return all(check(s.contains(".csrf(config -> config.csrfTokenRepository(csrf)")&&!s.contains("csrf.disable")
            &&!s.contains("ignoringRequestMatchers(\"/api/v1/head-office"),
            "Security source: CSRF enabled; head-office mutations not exempt"),
            contains(API,"/auth/csrf","csrf.headerName","csrf.token"));
    }
    static TestResult network(boolean uncertain)throws Exception{
        return all(contains(API,uncertain?"Chưa thể xác nhận kết quả":"Không thể kết nối đến GCMB. Vui lòng kiểm tra kết nối mạng và thử lại."),
            contains(UI,"catch (e)","catalogError(e)"));
    }
    static TestResult statusFeature()throws Exception{
        boolean mapping=Pattern.compile("(?is)@(Post|Put|Patch)Mapping\\([^\\n]*(?:combo|room-prices)[^\\n]*(?:status|activate)").matcher(source(CONTROLLER)).find();
        boolean method=Arrays.stream(RoomCatalogService.class.getDeclaredMethods())
            .anyMatch(m->m.getName().toLowerCase(Locale.ROOT).matches(".*combo.*(status|activat).*|.*(status|activat).*combo.*"));
        String ui=source(UI);
        boolean control=ui.contains("deactivateCombo")||ui.contains("changeComboStatus")||ui.contains("activateCombo");
        return check(mapping&&method&&control,"UC-26 source: combo-status mapping="+mapping+", method="+method+", UI action="+control
            +"; deactivateRoomType handles room types, not combos");
    }
    static TestResult notFound(boolean deletion)throws Exception{
        RecordingJdbc jdbc=new RecordingJdbc();
        try{
            RoomCatalogService s=new RoomCatalogService(jdbc);
            if(deletion)s.deletePrice("COMBO",ABSENT);else s.updatePrice("COMBO",ABSENT,valid());
        }catch(ResponseStatusException e){
            return check(e.getStatusCode().value()==404&&jdbc.insertCount==0&&jdbc.deleteCount==0,
                "Production lookup: "+e.getStatusCode()+" "+e.getReason()+"; no mutation");
        }
        return check(false,"Nonexistent combo not rejected");
    }
    static TestResult history(boolean items)throws Exception{
        String update=between(source(SERVICE),"public PriceCatalogItem updatePrice(","public void deletePrice(");
        return all(contains(SERVICE,"MAX(version)","active=false,valid_to=","old.code()"),
            contains(V1,items?"CREATE TABLE booking_combo_item":"pricing_snapshot jsonb NOT NULL","ON DELETE RESTRICT"),
            check(!update.contains("DELETE FROM")&&!update.contains("UPDATE gcmb.booking"),
                "Update source retains old row and does not mutate booking snapshots; static check only"));
    }
    static TestResult includedItemsCreate()throws Exception{
        String ui=source(UI);
        String service=between(source(SERVICE),"public PriceCatalogItem createPrice(","public PriceCatalogItem updatePrice(");
        boolean structuredInput=ui.contains("comboItemId")||ui.contains("includedItems")||ui.contains("itemId");
        boolean quantityInput=ui.contains("quantity")&&structuredInput;
        boolean itemWrite=service.contains("INSERT INTO gcmb.combo_item");
        return all(
            contains(V1,"CREATE TABLE combo_item","menu_item_id uuid NOT NULL","quantity numeric(18,3) NOT NULL"),
            check(structuredInput&&quantityInput,
                "Create UI structured included-item selection="+structuredInput+", quantity input="+quantityInput
                    +"; a free-text description does not persist item identities and quantities"),
            check(itemWrite,"createPrice writes gcmb.combo_item="+itemWrite));
    }
    static TestResult includedItemsUpdate()throws Exception{
        String ui=source(UI);
        String service=between(source(SERVICE),"public PriceCatalogItem updatePrice(","public void deletePrice(");
        boolean structuredInput=ui.contains("comboItemId")||ui.contains("includedItems")||ui.contains("itemId");
        boolean itemWrite=service.contains("INSERT INTO gcmb.combo_item")||service.contains("UPDATE gcmb.combo_item");
        return all(
            contains(V1,"CREATE TABLE combo_item","ALTER TABLE combo_item ADD FOREIGN KEY"),
            check(structuredInput,"Update UI supports structured included items="+structuredInput),
            check(itemWrite,"updatePrice creates a new combo-item version="+itemWrite));
    }
    static TestResult transaction(String method)throws Exception{
        Class<?>[] args=method.equals("updatePrice")?new Class<?>[]{String.class,UUID.class,PriceCommand.class}:new Class<?>[]{String.class,UUID.class};
        boolean tx=RoomCatalogService.class.getMethod(method,args).isAnnotationPresent(Transactional.class);
        return all(check(tx,method+" @Transactional="+tx+"; static wiring check, not live rollback"),
            contains(AUDIT,"transaction.execute","T result = operation.get()","before","after"));
    }
    static TestResult delete(boolean inactive)throws Exception{
        RecordingJdbc jdbc=new RecordingJdbc("CIN_4H_QN");
        if(inactive)jdbc.records.get(COMBO).put("active",false);
        new RoomCatalogService(jdbc).deletePrice("COMBO",COMBO);
        return check(jdbc.deleteCount==1&&!jdbc.records.containsKey(COMBO),"Production deletePrice removed selected "
            +(inactive?"inactive":"active")+" row in recording JDBC adapter; no live DB");
    }
    static TestResult deleteReview()throws Exception{
        String dialog=between(source(UI),"function DeleteDialog(","function Empty(");
        return check(dialog.contains("Xác nhận xóa combo?")&&dialog.contains("blockers"),
            "DeleteDialog has no pre-confirmation reference-check result or exact MSG14 prefix; DB references checked after confirmation");
    }
    static TestResult deleteCancel()throws Exception{
        return contains(UI,"onCancel={() => setPendingDelete(null)}","onConfirm={() => remove(pendingDelete.type, pendingDelete.item)}",
            "onClick={onCancel}>Hủy</button>");
    }
    static TestResult deleteReferences(boolean items)throws Exception{
        return all(foreignKeys(),contains(SERVICE,"Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên."),
            contains(V1,items?"REFERENCES combo_item(id) ON DELETE RESTRICT":"REFERENCES combo(id) ON DELETE RESTRICT"));
    }
    static TestResult foreignKeys()throws Exception{
        return contains(V1,"ALTER TABLE booking_charge ADD FOREIGN KEY (combo_id) REFERENCES combo(id) ON DELETE RESTRICT",
            "ALTER TABLE combo_item ADD FOREIGN KEY (combo_id) REFERENCES combo(id) ON DELETE RESTRICT",
            "ALTER TABLE booking_combo_item ADD FOREIGN KEY (combo_item_id) REFERENCES combo_item(id) ON DELETE RESTRICT");
    }
    static TestResult updateRoomRequired()throws Exception{
        RecordingJdbc jdbc=new RecordingJdbc();
        try{
            PriceCatalogItem result=new RoomCatalogService(jdbc).updatePrice("COMBO",COMBO,edited("room",null));
            return check(false,"Production updatePrice accepted missing room and fell back to "+result.roomTypeId()+"; inserts="+jdbc.insertCount);
        }catch(ResponseStatusException e){
            return check(e.getStatusCode().value()==400&&"Vui lòng bổ sung Hạng áp dụng.".equals(e.getReason())&&jdbc.insertCount==0,
                "Production missing-room rejection: "+e.getReason()+"; inserts="+jdbc.insertCount);
        }
    }
    static TestResult listFields()throws Exception{
        String rows=between(source(UI),"{visibleCombos.map(","{visibleCombos.length");
        boolean hidden=rows.contains("combo.description ||")&&!rows.contains("combo.durationMinutes}</");
        return all(contains(UI,"combo.code","combo.name","combo.roomTypeName","combo.description","combo.price"),
            check(!hidden,"Combo table renders description OR duration; populated description hides duration="+hidden));
    }
    static TestResult details()throws Exception{
        String editor=between(source(UI),"function EditorPage(","function Status(");
        String open=between(source(UI),"const openCombo =","const submit =");
        return all(contains(UI,"const openCombo","durationMinutes: item.durationMinutes","minuteFrom: item.minuteFrom","minuteTo: item.minuteTo","description: item.description"),
            check(editor.contains("item.active")||editor.contains("form.active"),"Detail/edit form populates terms but does not display combo status"),
            check(!open.contains("roomCatalogApi."),"Opening combo form has no business write"));
    }
    static TestResult readOnly()throws Exception{
        String body=between(source(SERVICE),"public List<PriceCatalogItem> prices(","@Transactional");
        return check(body.contains("db.query")&&!Pattern.compile("(?i)\\b(INSERT|UPDATE|DELETE)\\b").matcher(body).find(),
            "Production prices() only SELECT/query; no business DML");
    }
    static TestResult emptyMessage()throws Exception{
        return contains(UI,"Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.");
    }

    // Evaluate the unchanged production filter expression using existing seed business data.
    static TestResult filter(String query,String room,String status,String service,String wanted)throws Exception{
        String ui=source(UI);
        String expression=between(ui,"const visibleCombos =","\n  useEffect(").trim();
        String matches=between(ui,"function matchesStatus(","function timeLabel(");
        Pattern tuple=Pattern.compile("\\('([^']+)','(RT-QN|RT-KG)','([^']+)',(\\d+),(\\d+)::numeric,'([^']*)',(\\d+),(\\d+)\\)");
        Matcher m=tuple.matcher(source(V3));
        List<String> prices=new ArrayList<>();
        while(m.find())prices.add("{kind:'COMBO',id:"+js(m.group(1))+",code:"+js(m.group(1))+",name:"+js(m.group(3))
            +",roomTypeId:"+js(m.group(2))+",roomTypeName:"+js(m.group(2).equals("RT-QN")?"Queen":"King")
            +",description:"+js(m.group(6))+",durationMinutes:"+m.group(4)+",price:"+m.group(5)+",active:true}");
        if(prices.size()!=16)throw new IllegalStateException("V3 parse expected 16 rows, got "+prices.size());
        String demo=source("backend/scripts/seed_uc34_37_demo.sql");
        if(!demo.contains("'DEMO-UC3437-MB-2H'")||!source("backend/src/main/resources/db/migration/V14__seed_genz_branches_and_accounts.sql").contains("UPDATE gcmb.combo"))
            throw new IllegalStateException("Inactive seed definition changed");
        prices.add("{kind:'COMBO',id:'DEMO-UC3437-MB-2H',code:'DEMO-UC3437-MB-2H',name:'Music Box demo 2 giờ',roomTypeId:'DEMO-UC3437-MUSIC',roomTypeName:'Music Box demo',description:'',durationMinutes:120,price:450000,active:false}");
        String script="const useMemo=f=>f();const prices=["+String.join(",",prices)+"];"
            +"const rooms=[{id:'RT-QN',serviceType:'CINEMA'},{id:'RT-KG',serviceType:'CINEMA'},{id:'DEMO-UC3437-MUSIC',serviceType:'MUSIC_BOX'}];"
            +"const query="+js(query)+",roomFilterId="+js(room)+",statusFilter="+js(status)+",service="+js(service)+";"
            +matches+expression+"\nconsole.log(visibleCombos.map(p=>p.code).join(','));";
        Process process=new ProcessBuilder(System.getProperty("gcmb.node","node"),"--input-type=commonjs").redirectErrorStream(true).start();
        // stdin avoids Windows command-line quoting changing the production expression.
        try(var input=process.getOutputStream()){input.write(script.getBytes(StandardCharsets.UTF_8));}
        if(!process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Node adapter timed out");}
        String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();
        if(process.exitValue()!=0)throw new IllegalStateException("Production JS filter failed: "+output);
        List<String> codes=output.isEmpty()?List.of():Arrays.asList(output.split(","));
        boolean ok=switch(wanted){
            case "CIN_C1_QN" -> codes.equals(List.of("CIN_C1_QN"));
            case "name" -> codes.size()==2&&codes.contains("CIN_C1_QN")&&codes.contains("CIN_C1_KG");
            case "included" -> codes.size()==4&&codes.contains("CIN_C1_QN")&&codes.contains("CIN_C3_KG");
            case "queen" -> codes.size()==8&&codes.stream().allMatch(c->c.endsWith("_QN"));
            case "active" -> codes.size()==16&&!codes.contains("DEMO-UC3437-MB-2H");
            case "inactive" -> codes.equals(List.of("DEMO-UC3437-MB-2H"));
            case "all" -> codes.size()==17&&codes.contains("CIN_C1_QN");
            case "empty" -> codes.isEmpty();
            default -> throw new IllegalArgumentException(wanted);
        };
        return check(ok,"Executed production visibleCombos/matchesStatus in Node on seed definitions: "+codes);
    }
    static String js(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}

    // Recording adapter for actual production SQL and RowMapper calls. No Connection is opened.
    // This is not a database engine: constraints and rollback are checked separately in source.
    static final class RecordingJdbc extends JdbcTemplate{
        final Map<UUID,Map<String,Object>> records=new LinkedHashMap<>();
        int insertCount,deleteCount;
        RecordingJdbc()throws Exception{this("CIN_C1_QN");}
        RecordingJdbc(String seedCode){
            if("CIN_C1_QN".equals(seedCode)){
                records.put(COMBO,row(COMBO,seedCode,1L,QUEEN,"Combo 1 · 2 giờ",
                    "2 giờ xem phim; 1 khô gà; 2 đồ uống tự chọn",120,0,1440,new BigDecimal("236000"),true));
            }else if("CIN_4H_QN".equals(seedCode)){
                records.put(COMBO,row(COMBO,seedCode,1L,QUEEN,"Combo Queen · 4 giờ",
                    "4 giờ xem phim; 2 khô gà; 4 đồ uống tự chọn",240,0,1440,new BigDecimal("430000"),true));
            }else{
                throw new IllegalArgumentException("Unsupported demo combo: "+seedCode);
            }
        }
        static Map<String,Object> row(UUID id,String code,Object version,UUID room,String name,String description,Integer duration,Integer from,Integer to,BigDecimal price,boolean active){
            Map<String,Object> r=new HashMap<>();
            r.put("id",id);r.put("kind","COMBO");r.put("code",code);r.put("version",version);
            r.put("room_type_id",room);r.put("room_type_name",KING.equals(room)?"King":"Queen");
            r.put("name",name);r.put("description",description);r.put("duration_minutes",duration);
            r.put("minute_from",from);r.put("minute_to",to);r.put("price",price);r.put("active",active);
            return r;
        }
        @Override public <T>T queryForObject(String sql,Class<T> type,Object... args){
            long value;
            if(sql.contains("COUNT(*)")&&sql.contains("gcmb.combo"))
                value=records.values().stream().filter(r->Objects.equals(r.get("code"),args[0])&&Boolean.TRUE.equals(r.get("active"))).count();
            else if(sql.contains("MAX(version)"))
                value=records.values().stream().filter(r->Objects.equals(r.get("code"),args[0])).mapToLong(r->((Number)r.get("version")).longValue()).max().orElse(0)+1;
            else throw new IllegalStateException("Unexpected production SQL: "+sql);
            if(type.equals(Integer.class))return type.cast(Integer.valueOf((int)value));
            return type.cast(Long.valueOf(value));
        }
        @Override public int update(String sql,Object... args){
            if(sql.contains("INSERT INTO gcmb.combo")){
                UUID id=(UUID)args[0];
                records.put(id,row(id,(String)args[1],args[2],(UUID)args[3],(String)args[5],(String)args[10],
                    (Integer)args[6],(Integer)args[8],(Integer)args[9],(BigDecimal)args[7],true));
                insertCount++;return 1;
            }
            if(sql.startsWith("UPDATE gcmb.combo SET active=false")){
                Map<String,Object> r=records.get((UUID)args[0]);if(r==null)return 0;r.put("active",false);return 1;
            }
            if(sql.startsWith("DELETE FROM gcmb.combo")){
                if(records.remove((UUID)args[0])==null)return 0;deleteCount++;return 1;
            }
            throw new IllegalStateException("Unexpected production DML: "+sql);
        }
        @Override public <T>List<T> query(String sql,RowMapper<T> mapper,Object... args){
            List<Map<String,Object>> rows=new ArrayList<>();
            if(sql.startsWith("SELECT id,code,name,mode,capacity,description,active FROM gcmb.room_type")){
                UUID room=(UUID)args[0];
                if(QUEEN.equals(room)||KING.equals(room)){
                    Map<String,Object> r=new HashMap<>();
                    r.put("id",room);r.put("code",QUEEN.equals(room)?"RT-QN":"RT-KG");r.put("name",QUEEN.equals(room)?"Queen":"King");
                    r.put("mode","CINEMA");r.put("capacity",QUEEN.equals(room)?4:6);
                    r.put("description",QUEEN.equals(room)?"Không gian tiêu chuẩn · Sức chứa 2–4 khách":"Không gian rộng · Sức chứa 4–6 khách");
                    r.put("active",true);rows.add(r);
                }
            }else if(sql.contains("WHERE c.id=?")){
                Map<String,Object> r=records.get((UUID)args[0]);if(r!=null)rows.add(r);
            }else throw new IllegalStateException("Unexpected production query: "+sql);
            List<T> result=new ArrayList<>();
            try{for(int i=0;i<rows.size();i++)result.add(mapper.mapRow(resultSet(rows.get(i)),i));}
            catch(java.sql.SQLException e){throw new IllegalStateException(e);}
            return result;
        }
        private static ResultSet resultSet(Map<String,Object> row){
            return (ResultSet)Proxy.newProxyInstance(ComboPackageManagementManualRunner.class.getClassLoader(),new Class<?>[]{ResultSet.class},
                (proxy,method,args)->{
                    if(method.getName().equals("toString"))return "RecordingResultSet"+row;
                    Object value=args!=null&&args.length>0?row.get(String.valueOf(args[0])):null;
                    return switch(method.getName()){
                        case "getObject","getBigDecimal" -> value;
                        case "getString" -> value==null?null:value.toString();
                        case "getInt" -> value==null?0:((Number)value).intValue();
                        case "getBoolean" -> Boolean.TRUE.equals(value);
                        case "wasNull","isClosed" -> false;
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException("ResultSet adapter: "+method);
                    };
                });
        }
    }
}


