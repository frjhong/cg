package com.example.weekly;

import org.w3c.dom.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Imports dependent OOXML resources without overwriting template definitions. */
final class PartImporter {
    private final WordPackage target, source;
    private final Map<String,String> styles=new HashMap<>(), nums=new HashMap<>(), abstracts=new HashMap<>(), relationships=new HashMap<>(), copiedParts=new HashMap<>();
    PartImporter(WordPackage target,WordPackage source) { this.target=target;this.source=source; }
    Element copy(Element block) {
        var copy=(Element)target.document.importNode(block,true);
        if(!Xml.all(copy,Xml.W,"footnoteReference").isEmpty() || !Xml.all(copy,Xml.W,"endnoteReference").isEmpty() || !Xml.all(copy,Xml.W,"commentReference").isEmpty())
            throw new IllegalArgumentException("舊檔獨有事項含註腳、尾註或評論，需先移除或轉成正文再彙整");
        for(String tag:List.of("pStyle","rStyle","tblStyle")) for(var s:Xml.all(copy,Xml.W,tag)) Xml.val(s,style(Xml.val(s)));
        for(var n:Xml.all(copy,Xml.W,"numId")) if(!Xml.val(n).equals("0")) Xml.val(n,number(Xml.val(n)));
        walkRelationships(copy);
        // Bookmark and drawing identifiers are document-scoped, unlike relationship IDs.
        var bookmarks=new HashMap<String,String>();int bookmarkId=Integer.parseInt(nextBookmark());
        for(var b:Xml.all(copy,Xml.W,"bookmarkStart")) { String old=b.getAttributeNS(Xml.W,"id"); String fresh=Integer.toString(bookmarkId++); bookmarks.put(old,fresh); b.setAttributeNS(Xml.W,"w:id",fresh); b.setAttributeNS(Xml.W,"w:name","import_"+source.id+"_"+fresh); }
        for(var b:Xml.all(copy,Xml.W,"bookmarkEnd")) {
            String fresh=bookmarks.get(b.getAttributeNS(Xml.W,"id")); if(fresh==null) throw new IllegalArgumentException("跨區塊書籤暫不支援，請移除書籤後重試"); b.setAttributeNS(Xml.W,"w:id",fresh);
        }
        int drawingId=Integer.parseInt(nextDrawing());
        for(var d:Xml.all(copy,"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing","docPr")) d.setAttribute("id",Integer.toString(drawingId++));
        return copy;
    }
    private String nextBookmark() { return Integer.toString(1+Xml.all(target.document,Xml.W,"bookmarkStart").stream().mapToInt(e->Integer.parseInt(e.getAttributeNS(Xml.W,"id"))).max().orElse(0)); }
    private String nextDrawing() { return Integer.toString(1+Xml.all(target.document,"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing","docPr").stream().mapToInt(e->Integer.parseInt(e.getAttribute("id"))).max().orElse(0)); }
    private String style(String id) {
        if(id.isBlank() || styles.containsKey(id)) return styles.getOrDefault(id,id);
        if(!source.parts.containsKey("word/styles.xml")) return id;
        var src=Xml.parse(source.parts.get("word/styles.xml")); var dst=xmlPart("word/styles.xml",Xml.W,"w:styles");
        var def=Xml.all(src,Xml.W,"style").stream().filter(e->id.equals(e.getAttributeNS(Xml.W,"styleId"))).findFirst().orElse(null);
        var existing=Xml.all(dst,Xml.W,"style").stream().filter(e->id.equals(e.getAttributeNS(Xml.W,"styleId"))).findFirst().orElse(null);
        if(def==null || existing!=null && def.isEqualNode(existing)) { styles.put(id,id);return id; }
        String fresh=existing==null?id:"import_"+source.id+"_"+id; styles.put(id,fresh);
        var clone=(Element)dst.importNode(def,true); clone.setAttributeNS(Xml.W,"w:styleId",fresh);
        // Persist before recursion, then re-read to retain recursively imported dependencies.
        dst.getDocumentElement().appendChild(clone);target.parts.put("word/styles.xml",Xml.bytes(dst));
        for(String tag:List.of("basedOn","next","link")) for(var ref:Xml.all(clone,Xml.W,tag)) {
            String replacement=style(Xml.val(ref));
            dst=Xml.parse(target.parts.get("word/styles.xml"));
            var current=Xml.all(dst,Xml.W,"style").stream().filter(e->fresh.equals(e.getAttributeNS(Xml.W,"styleId"))).findFirst().orElseThrow();
            for(var field:Xml.all(current,Xml.W,tag)) if(Xml.val(field).equals(Xml.val(ref))) Xml.val(field,replacement);
            target.parts.put("word/styles.xml",Xml.bytes(dst));
        }
        ensureContentType("word/styles.xml",source); return fresh;
    }
    private String number(String id) {
        if(nums.containsKey(id)) return nums.get(id);
        if(!source.parts.containsKey("word/numbering.xml")) throw new IllegalArgumentException("舊檔編號參照缺少定義");
        var src=Xml.parse(source.parts.get("word/numbering.xml"));var dst=xmlPart("word/numbering.xml",Xml.W,"w:numbering");
        var def=Xml.all(src,Xml.W,"num").stream().filter(e->id.equals(e.getAttributeNS(Xml.W,"numId"))).findFirst().orElseThrow(()->new IllegalArgumentException("舊檔編號定義不存在"));
        String abs=Xml.val(Xml.first(def,"abstractNumId"));
        if(!abstracts.containsKey(abs)) {
            String freshAbs=nextId(dst,"abstractNum","abstractNumId"); abstracts.put(abs,freshAbs);
            var ad=Xml.all(src,Xml.W,"abstractNum").stream().filter(e->abs.equals(e.getAttributeNS(Xml.W,"abstractNumId"))).findFirst().orElseThrow();
            if(!Xml.all(ad,Xml.W,"lvlPicBulletId").isEmpty()) throw new IllegalArgumentException("舊檔圖片項目符號暫不支援");
            var clone=(Element)dst.importNode(ad,true);clone.setAttributeNS(Xml.W,"w:abstractNumId",freshAbs);
            for(String tag:List.of("pStyle","styleLink","numStyleLink")) for(var s:Xml.all(clone,Xml.W,tag)) Xml.val(s,style(Xml.val(s)));
            dst.getDocumentElement().insertBefore(clone,Xml.all(dst,Xml.W,"num").stream().findFirst().orElse(null));
        }
        String fresh=nextId(dst,"num","numId"); nums.put(id,fresh);
        var clone=(Element)dst.importNode(def,true);clone.setAttributeNS(Xml.W,"w:numId",fresh);Xml.val(Xml.first(clone,"abstractNumId"),abstracts.get(abs));dst.getDocumentElement().appendChild(clone);
        target.parts.put("word/numbering.xml",Xml.bytes(dst));ensureContentType("word/numbering.xml",source);
        ensureDocumentRelation("numbering","numbering.xml");return fresh;
    }
    private static String nextId(Document d,String tag,String attr) { return Integer.toString(1+Xml.all(d,Xml.W,tag).stream().mapToInt(e->Integer.parseInt(e.getAttributeNS(Xml.W,attr))).max().orElse(0)); }
    private Document xmlPart(String path,String ns,String root) {
        return Xml.parse(target.parts.getOrDefault(path,("<"+root+" xmlns:w=\""+ns+"\"/>").getBytes(StandardCharsets.UTF_8)));
    }
    private void walkRelationships(Element el) {
        var attrs=el.getAttributes();
        for(int i=0;i<attrs.getLength();i++) { var a=attrs.item(i); if(Xml.R.equals(a.getNamespaceURI())) a.setNodeValue(relationship(a.getNodeValue())); }
        for(var child:Xml.children(el)) walkRelationships(child);
    }
    private String relationship(String id) {
        if(relationships.containsKey(id)) return relationships.get(id);
        var src=Xml.parse(source.parts.get("word/_rels/document.xml.rels"));
        var rel=Xml.children(src.getDocumentElement()).stream().filter(e->id.equals(e.getAttribute("Id"))).findFirst().orElseThrow(()->new IllegalArgumentException("舊檔關聯不存在"));
        var dst=relations();var clone=(Element)dst.importNode(rel,true);String fresh="import_"+source.id+"_"+id;
        clone.setAttribute("Id",fresh);relationships.put(id,fresh);
        if(!"External".equals(rel.getAttribute("TargetMode"))) {
            String part=URI.create("word/").resolve(rel.getAttribute("Target")).normalize().getPath();
            clone.setAttribute("Target",URI.create("word/").relativize(URI.create(copyPart(part))).toString());
        }
        // copyPart may update content types but not document relationships.
        dst.getDocumentElement().appendChild(clone);target.parts.put("word/_rels/document.xml.rels",Xml.bytes(dst));return fresh;
    }
    private String copyPart(String path) {
        if(copiedParts.containsKey(path)) return copiedParts.get(path);
        byte[] bytes=source.parts.get(path);if(bytes==null) throw new IllegalArgumentException("舊檔附件不存在："+path);
        String fresh="word/imported/"+source.id+"/"+path;copiedParts.put(path,fresh);target.parts.put(fresh,bytes);ensureContentTypeAs(path,fresh);
        String relPath=relPath(path);
        if(source.parts.containsKey(relPath)) {
            var rels=Xml.parse(source.parts.get(relPath));
            for(var r:Xml.children(rels.getDocumentElement())) if(!"External".equals(r.getAttribute("TargetMode"))) {
                String oldDir=path.substring(0,path.lastIndexOf('/')+1);
                String dep=URI.create(oldDir).resolve(r.getAttribute("Target")).normalize().getPath();
                String copied=copyPart(dep);String newDir=fresh.substring(0,fresh.lastIndexOf('/')+1);
                // java Path relativize handles sibling directories (URI.relativize does not).
                r.setAttribute("Target",java.nio.file.Path.of(newDir).relativize(java.nio.file.Path.of(copied)).toString());
            }
            target.parts.put(relPath(fresh),Xml.bytes(rels));
        }
        return fresh;
    }
    private static String relPath(String part) { int slash=part.lastIndexOf('/');return part.substring(0,slash+1)+"_rels/"+part.substring(slash+1)+".rels"; }
    private Document relations() { return Xml.parse(target.parts.getOrDefault("word/_rels/document.xml.rels",("<Relationships xmlns=\""+Xml.REL+"\"/>").getBytes(StandardCharsets.UTF_8))); }
    private void ensureDocumentRelation(String type,String path) {
        var d=relations();if(Xml.children(d.getDocumentElement()).stream().anyMatch(e->e.getAttribute("Type").endsWith("/"+type)))return;
        var e=d.createElementNS(Xml.REL,"Relationship");e.setAttribute("Id","import_"+type);e.setAttribute("Type",Xml.R+"/"+type);e.setAttribute("Target",path);d.getDocumentElement().appendChild(e);target.parts.put("word/_rels/document.xml.rels",Xml.bytes(d));
    }
    private void ensureContentType(String path,WordPackage ignored) { ensureContentTypeAs(path,path); }
    private void ensureContentTypeAs(String old,String fresh) {
        var src=Xml.parse(source.parts.get("[Content_Types].xml"));var dst=Xml.parse(target.parts.get("[Content_Types].xml"));
        for(var e:Xml.children(src.getDocumentElement())) {
            boolean override="Override".equals(e.getLocalName());String attr=override?"PartName":"Extension";
            String value=override?"/"+fresh:e.getAttribute(attr);
            if(override && !e.getAttribute(attr).equals("/"+old))continue;
            if(!override && !old.endsWith("."+value))continue;
            if(Xml.children(dst.getDocumentElement()).stream().anyMatch(x->x.getLocalName().equals(e.getLocalName()) && x.getAttribute(attr).equals(value)))continue;
            var copy=(Element)dst.importNode(e,true);copy.setAttribute(attr,value);dst.getDocumentElement().appendChild(copy);
        }
        // Preserve original bytes when no content type was added.
        if(!dst.isEqualNode(Xml.parse(target.parts.get("[Content_Types].xml"))))target.parts.put("[Content_Types].xml",Xml.bytes(dst));
    }
}
