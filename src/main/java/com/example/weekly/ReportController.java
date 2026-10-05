package com.example.weekly;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
final class ReportController {
    record Job(byte[] docx,Map<String,Object> audit,Instant created) {}
    private final MergeService service;private final AiPlanner planner;private final ObjectMapper mapper;private final long retention;
    private final Map<UUID,Job> jobs=new ConcurrentHashMap<>();
    ReportController(MergeService service,AiPlanner planner,ObjectMapper mapper,@Value("${weekly.retention-hours}")long retention) {
        this.service=service;this.planner=planner;this.mapper=mapper;this.retention=retention;
    }
    @GetMapping("/config")Map<String,Object> config() {return Map.of("aiReady",planner.ready(),"mode",planner.mode(),"retentionHours",retention);}
    @PostMapping(value="/merge",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    synchronized Map<String,Object> merge(@RequestParam("files")List<MultipartFile> files,@RequestParam(value="dates",defaultValue="{}")String dates) throws Exception {
        clean();if(jobs.size()>=25)throw new IllegalArgumentException("暫存工作已滿，請刪除先前結果或稍後再試");
        Map<String,String> overrides;
        try {overrides=mapper.readValue(dates,new TypeReference<>(){});}catch(Exception e){throw new IllegalArgumentException("日期設定必須是檔名對應 ISO 日期的 JSON");}
        if(overrides==null)throw new IllegalArgumentException("日期設定不可為 null");
        var inputs=new ArrayList<MergeService.Input>();var names=new HashSet<String>();
        for(var file:files) {
            String name=Optional.ofNullable(file.getOriginalFilename()).orElse("").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);
            if(!name.toLowerCase(Locale.ROOT).endsWith(".docx") || file.isEmpty())throw new IllegalArgumentException("僅接受非空的 .docx 文件");
            if(!names.add(name))throw new IllegalArgumentException("檔名不可重複，請先重新命名");
            LocalDate date=null;String value=overrides.get(name);
            if(value!=null && !value.isBlank())try {date=LocalDate.parse(value);}catch(Exception e){throw new IllegalArgumentException("日期格式須為 YYYY-MM-DD");}
            inputs.add(new MergeService.Input(name,file.getBytes(),date));
        }
        var result=service.merge(inputs);UUID id=UUID.randomUUID();jobs.put(id,new Job(result.docx(),result.audit(),Instant.now()));
        return Map.of("id",id,"downloadUrl","/api/reports/"+id+"/word","auditUrl","/api/reports/"+id+"/audit","summary",result.audit());
    }
    @GetMapping("/reports/{id}/word")ResponseEntity<byte[]> download(@PathVariable UUID id) {
        var job=job(id);return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("彙整週報.docx",StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"no-store").body(job.docx());
    }
    @GetMapping("/reports/{id}/audit")ResponseEntity<Map<String,Object>> audit(@PathVariable UUID id) {return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(job(id).audit());}
    @DeleteMapping("/reports/{id}")void delete(@PathVariable UUID id) {jobs.remove(id);}
    private Job job(UUID id) {clean();var job=jobs.get(id);if(job==null)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"結果不存在或已過期");return job;}
    @Scheduled(fixedDelay=60000)void clean() {Instant cutoff=Instant.now().minus(Duration.ofHours(retention));jobs.entrySet().removeIf(e->e.getValue().created().isBefore(cutoff));}
    @ExceptionHandler(IllegalArgumentException.class)ResponseEntity<Map<String,String>> badRequest(IllegalArgumentException e) {return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));}
    @ExceptionHandler(IllegalStateException.class)ResponseEntity<Map<String,String>> failed(IllegalStateException e) {return ResponseEntity.status(502).body(Map.of("error",e.getMessage()));}
    @ExceptionHandler(MaxUploadSizeExceededException.class)ResponseEntity<Map<String,String>> tooLarge() {return ResponseEntity.status(413).body(Map.of("error","單檔上限 10MB，總上傳上限 50MB"));}
}
