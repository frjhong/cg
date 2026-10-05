package com.example.weekly;

import org.w3c.dom.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

final class WordPackage {
    final String id, name, sha256;
    final LocalDate date;
    final Map<String,byte[]> parts = new LinkedHashMap<>();
    final Document document;
    final Element body;
    final List<Element> blocks;
    static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{3,4})[.年/\\-](\\d{1,2})[.月/\\-](\\d{1,2})(?:日)?(?!\\d)");

    WordPackage(String id, String name, byte[] bytes, LocalDate override) {
        this.id=id; this.name=name;
        try {
            sha256=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            long total=0; int count=0;
            try(var z=new ZipInputStream(new ByteArrayInputStream(bytes))) {
                for(ZipEntry e;(e=z.getNextEntry())!=null;) {
                    if (++count>2000 || e.getName().startsWith("/") || e.getName().contains("..")) throw new IllegalArgumentException("不安全的 Word ZIP 結構");
                    if(e.isDirectory()) continue;
                    var out=new ByteArrayOutputStream(); byte[] buf=new byte[8192];
                    for(int n;(n=z.read(buf))!=-1;) {
                        total+=n; if(total>64L*1024*1024 || out.size()+n>16*1024*1024) throw new IllegalArgumentException("Word 解壓縮大小超過限制");
                        out.write(buf,0,n);
                    }
                    if(parts.put(e.getName(),out.toByteArray())!=null) throw new IllegalArgumentException("Word ZIP 含重複路徑");
                }
            }
        } catch (IOException|java.security.NoSuchAlgorithmException e) { throw new IllegalArgumentException("無法讀取 DOCX",e); }
        if(!parts.containsKey("word/document.xml") || !parts.containsKey("[Content_Types].xml")) throw new IllegalArgumentException("請上傳有效的 .docx 文件");
        if(parts.keySet().stream().anyMatch(p->p.endsWith("vbaProject.bin"))) throw new IllegalArgumentException("不支援含巨集的文件");
        document=Xml.parse(parts.get("word/document.xml")); body=Xml.first(document,"body");
        if(body==null) throw new IllegalArgumentException("Word 缺少正文");
        blocks=Xml.children(body).stream().filter(e->!"sectPr".equals(e.getLocalName())).toList();
        if(blocks.size()>1200) throw new IllegalArgumentException("單份週報區塊數超過 1200");
        date=override!=null ? override : detectDate(name,blocks);
    }
    static LocalDate detectDate(String name,List<Element> blocks) {
        // Only document header, then filename: do not mistake a work item's historical date for report date.
        for(var e:blocks.stream().filter(b->!Xml.text(b).isBlank()).limit(2).toList()) {
            var m=DATE.matcher(Xml.text(e)); if(m.find()) return parsed(m);
        }
        var m=DATE.matcher(name); if(m.find()) return parsed(m);
        var compact=Pattern.compile("(?<!\\d)(20\\d{2})(\\d{2})(\\d{2})(?!\\d)").matcher(name);
        if(compact.find()) return LocalDate.of(Integer.parseInt(compact.group(1)),Integer.parseInt(compact.group(2)),Integer.parseInt(compact.group(3)));
        throw new IllegalArgumentException(name+" 無法判讀完整週報日期，請在日期欄指定 YYYY-MM-DD");
    }
    static LocalDate parsed(Matcher m) {
        int year=Integer.parseInt(m.group(1)); if(m.group(1).length()==3) year+=1911;
        try { return LocalDate.of(year,Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))); }
        catch(DateTimeException e) { throw new IllegalArgumentException("週報日期不合法",e); }
    }
    Element block(int index) {
        if(index<0 || index>=blocks.size()) throw new IllegalArgumentException("AI 引用了不存在的區塊");
        return blocks.get(index);
    }
    Map<String,Object> description() {
        var list=new ArrayList<Map<String,Object>>();
        for(int i=0;i<blocks.size();i++) {
            var e=blocks.get(i); if(!hasContent(e)) continue;
            var b=new LinkedHashMap<String,Object>(); b.put("index",i);b.put("type",e.getLocalName()); b.put("text",Xml.text(e));
            b.put("style",Xml.val(Xml.first(e,"pStyle"))); b.put("numbering",Xml.val(Xml.first(e,"numId")));
            b.put("outline",Xml.val(Xml.first(e,"outlineLvl"))); list.add(b);
        }
        return Map.of("id",id,"name",name,"sha256",sha256,"date",date.toString(),"blocks",list);
    }
    static boolean hasContent(Element e) { return !Xml.text(e).isBlank() || "tbl".equals(e.getLocalName()) || !Xml.all(e,Xml.W,"drawing").isEmpty() || !Xml.all(e,Xml.W,"pict").isEmpty(); }
    byte[] write() {
        parts.put("word/document.xml",Xml.bytes(document));
        try {
            var out=new ByteArrayOutputStream();
            try(var z=new ZipOutputStream(out)) {
                for(var e:parts.entrySet()) { z.putNextEntry(new ZipEntry(e.getKey())); z.write(e.getValue()); z.closeEntry(); }
            } return out.toByteArray();
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
}
