package com.example.weekly;

import java.util.*;

public record MergePlan(List<Section> sections, List<Item> items, List<Ref> metadata) {
    public record Ref(String reportId, int block) {}
    public record Section(String id, String title, String parentId, List<Ref> headings) {}
    public record Version(String reportId, List<Integer> blocks) {}
    public record Item(String id, String title, String sectionId, String reason, List<Version> versions) {}

    void validate(List<WordPackage> reports) {
        if(sections==null || items==null || metadata==null || sections.isEmpty() || items.isEmpty()) throw new IllegalArgumentException("AI 回傳的彙整計畫不完整");
        var documents=new HashMap<String,WordPackage>(); reports.forEach(r->documents.put(r.id,r));
        var ids=new HashSet<String>();
        for(int n=0;n<sections.size();n++) {
            var s=sections.get(n);
            if(s==null || blank(s.id()) || blank(s.title()) || s.headings()==null) throw new IllegalArgumentException("AI 章節欄位缺漏：sections["+n+"] 需要 id、title、headings");
            if(!ids.add(s.id())) throw new IllegalArgumentException("AI 章節 ID 重複："+s.id());
        }
        for(var s:sections) {
            var seen=new HashSet<String>(); var cursor=s;
            while(cursor.parentId()!=null && !cursor.parentId().isBlank()) {
                if(!seen.add(cursor.id())) throw new IllegalArgumentException("AI 章節階層循環");
                String p=cursor.parentId(); cursor=sections.stream().filter(x->x.id().equals(p)).findFirst().orElseThrow(()->new IllegalArgumentException("AI 引用了不存在的上層章節"));
            }
        }
        var used=new HashSet<Ref>();
        for(var s:sections) for(var ref:s.headings()) use(ref,used,documents);
        var itemIds=new HashSet<String>();
        for(int n=0;n<items.size();n++) {
            var i=items.get(n); String location="items["+n+"]";
            if(i==null) throw new IllegalArgumentException("AI 工作事項為空："+location);
            if(blank(i.id())) throw new IllegalArgumentException("AI 工作事項 ID 缺漏："+location);
            if(!itemIds.add(i.id())) throw new IllegalArgumentException("AI 工作事項 ID 重複："+location+" id="+i.id());
            if(blank(i.title()) || blank(i.reason())) throw new IllegalArgumentException("AI 工作事項欄位缺漏："+location+" 需要 title、reason");
            if(!ids.contains(i.sectionId())) throw new IllegalArgumentException("AI 工作事項引用不存在的章節："+location+" sectionId="+i.sectionId()+"；必須引用 sections[].id");
            if(i.versions()==null || i.versions().isEmpty()) throw new IllegalArgumentException("AI 工作事項版本缺漏："+location+" versions 不可為空");
            var versions=new HashSet<String>();
            for(var v:i.versions()) {
                if(v==null || blank(v.reportId()) || v.blocks()==null || v.blocks().isEmpty()) throw new IllegalArgumentException("AI 工作事項版本欄位缺漏："+location+" 需要 reportId、非空 blocks");
                if(!versions.add(v.reportId())) throw new IllegalArgumentException("同一事項在同一週報只能有一個版本："+location+" reportId="+v.reportId());
                int previous=-1;
                for(Integer b:v.blocks()) { if(b==null || b<=previous) throw new IllegalArgumentException("AI 區塊順序不合法："+location+" blocks 必須為遞增整數"); previous=b; use(new Ref(v.reportId(),b),used,documents); }
            }
        }
        for(var ref:metadata) {
            if(ref==null) throw new IllegalArgumentException("AI metadata 含空引用");
            if(ref.block()>1) throw new IllegalArgumentException("AI 不可把工作內容排除為標題");
            use(ref,used,documents);
        }
        for(var r:reports) for(int b=0;b<r.blocks.size();b++)
            if(WordPackage.hasContent(r.block(b)) && !used.contains(new Ref(r.id,b))) throw new IllegalArgumentException("AI 遺漏內容："+r.name+" 區塊 "+b+"，已停止產檔以避免遺失事項");
    }
    private static boolean blank(String value) { return value==null || value.isBlank(); }
    private static void use(Ref ref,Set<Ref> used,Map<String,WordPackage> documents) {
        if(ref==null) throw new IllegalArgumentException("AI 含空區塊引用");
        var d=documents.get(ref.reportId()); if(d==null) throw new IllegalArgumentException("AI 引用了不存在的週報"); d.block(ref.block());
        if(!used.add(ref)) throw new IllegalArgumentException("AI 重複使用同一區塊：reportId="+ref.reportId()+" block="+ref.block());
    }
}
