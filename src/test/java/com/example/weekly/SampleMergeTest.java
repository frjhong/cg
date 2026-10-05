package com.example.weekly;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SampleMergeTest {
    static final Path FIXTURE=Path.of("src/test/resources/sample-plan.json");
    static byte[] sample(String name)throws Exception {return Files.readAllBytes(Path.of("src/test/resources/samples",name));}
    static List<WordPackage> reports()throws Exception {return List.of(new WordPackage("r0","0930.docx",sample("0930.docx"),null),new WordPackage("r1","0916.docx",sample("0916.docx"),null));}
    static MergePlan plan()throws Exception {var m=new ObjectMapper();return m.treeToValue(m.readTree(Files.readAllBytes(FIXTURE)).path("plan"),MergePlan.class);}
    static MergeService service() {return new MergeService(new AiPlanner(new ObjectMapper(),"http://localhost","","test",FIXTURE.toString()));}
    static MergeService.Result merge()throws Exception {return service().merge(List.of(new MergeService.Input("0916.docx",sample("0916.docx"),null),new MergeService.Input("0930.docx",sample("0930.docx"),null)));}

    @Test void realSamplesRetainOldOnlyAndChooseLatestProgress()throws Exception {
        var result=merge();var out=new WordPackage("out","output.docx",result.docx(),null);String text=Xml.text(out.body);
        assertEquals(27,result.audit().get("itemCount"));assertEquals(2,result.audit().get("duplicateGroups"));assertEquals(14,result.audit().get("oldOnlyItems"));assertEquals(22,result.audit().get("insertedBlocks"));
        assertTrue(text.contains("115.09.30"));assertTrue(text.contains("applications manager軟體更新"));assertFalse(text.contains("廠商協助升級失敗"));
        assertEquals(1,count(text,"115.09 AP人員驗證中。"));assertTrue(text.contains("建置異質資料庫即時移轉系統"));assertTrue(text.contains("115.09 T2系統移轉中。"));
        assertTrue(text.contains("提報事項：(2)防毒軟體"));assertTrue(text.contains("Adware.Win64.ChinAd.A"));assertTrue(text.contains("GitLab"));assertTrue(text.contains("4,670,754"));assertTrue(text.contains("1,044,832"));
        var originals=reports();var p=plan();
        for(var item:p.items()) {
            var v=item.versions().stream().min(Comparator.comparing(MergePlan.Version::reportId)).orElseThrow();
            var r=originals.stream().filter(d->d.id.equals(v.reportId())).findFirst().orElseThrow();
            for(int b:v.blocks()) {
                String expected=Xml.text(r.block(b));if(expected.startsWith("提報事項：(1)防毒"))expected=expected.replace("(1)","(2)");
                assertTrue(text.contains(expected),"Missing winner: "+item.id()+" block "+b);
            }
        }
        Files.createDirectories(Path.of("artifacts"));Files.write(Path.of("artifacts/彙整週報_1150930.docx"),result.docx());
        Files.writeString(Path.of("artifacts/彙整比對紀錄.json"),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result.audit()));
    }
    @Test void allTemplateParagraphsAndResourcesSurvive()throws Exception {
        var result=merge();var out=new WordPackage("out","output.docx",result.docx(),null);var original=reports().getFirst();
        for(var block:original.blocks) {
            if(block==original.blocks.getLast() && Xml.children(block).stream().allMatch(e->"pPr".equals(e.getLocalName())))continue;
            var normalized=block.cloneNode(true);
            for(var cached:Xml.all(normalized,Xml.W,"lastRenderedPageBreak"))cached.getParentNode().removeChild(cached);
            assertTrue(out.blocks.stream().anyMatch(normalized::isEqualNode),"Template visible content/format changed: "+Xml.text(block));
        }
        assertTrue(Xml.all(out.document,Xml.W,"lastRenderedPageBreak").isEmpty(),"Old automatic pagination cache must not create blank pages");
        assertEquals(1,result.audit().get("removedTrailingEmptyParagraphs"));
        assertTrue(Xml.first(original.body,"sectPr").isEqualNode(Xml.first(out.body,"sectPr")));
        for(var part:original.parts.entrySet())if(!Set.of("word/document.xml","word/numbering.xml").contains(part.getKey()))assertArrayEquals(part.getValue(),out.parts.get(part.getKey()),"Changed template resource: "+part.getKey());
        var numbering=Xml.parse(out.parts.get("word/numbering.xml"));
        for(String tag:List.of("num","abstractNum"))for(var def:Xml.all(Xml.parse(original.parts.get("word/numbering.xml")),Xml.W,tag))assertTrue(Xml.all(numbering,Xml.W,tag).stream().anyMatch(def::isEqualNode));
        Set<String> valid=new HashSet<>();for(var e:Xml.all(numbering,Xml.W,"num"))valid.add(e.getAttributeNS(Xml.W,"numId"));
        for(var ref:Xml.all(out.document,Xml.W,"numId"))assertTrue(valid.contains(Xml.val(ref)));
        var dbCopies=out.blocks.stream().filter(e->Xml.text(e).startsWith("09.07 ~ 09.11 協助AA")).toList();assertEquals("36",Xml.val(Xml.first(dbCopies.getFirst(),"numId")));
    }
    @Test void invalidAiPlansNeverSilentlyDropContent()throws Exception {
        var p=plan();var items=new ArrayList<>(p.items());items.removeLast();
        assertThrows(IllegalArgumentException.class,()->new MergePlan(p.sections(),items,p.metadata()).validate(reports()));
        var duplicateItems=new ArrayList<>(p.items());duplicateItems.add(duplicateItems.getFirst());
        assertThrows(IllegalArgumentException.class,()->new MergePlan(p.sections(),duplicateItems,p.metadata()).validate(reports()));
        var metadata=new ArrayList<>(p.metadata());metadata.add(new MergePlan.Ref("r0",14));
        assertThrows(IllegalArgumentException.class,()->new MergePlan(p.sections(),p.items(),metadata).validate(reports()));
    }
    @Test void reportDateWinsOverFilenameAndUploadOrder()throws Exception {
        var r=new WordPackage("r","9999.docx",sample("0916.docx"),null);assertEquals("2026-09-16",r.date.toString());
        assertEquals("2026-09-30",reports().getFirst().date.toString());
        assertThrows(IllegalArgumentException.class,()->WordPackage.detectDate("0930.docx",List.of()));
        var a=merge();var b=service().merge(List.of(new MergeService.Input("0930.docx",sample("0930.docx"),null),new MergeService.Input("0916.docx",sample("0916.docx"),null)));
        assertEquals(Xml.text(new WordPackage("a","a.docx",a.docx(),null).body),Xml.text(new WordPackage("b","b.docx",b.docx(),null).body));
    }
    @Test void fixtureCannotProcessOtherBytesAndMaliciousXmlIsRejected()throws Exception {
        assertThrows(IllegalArgumentException.class,()->service().merge(List.of(new MergeService.Input("0916.docx",sample("0916.docx"),java.time.LocalDate.of(2026,10,1)),new MergeService.Input("0930.docx",sample("0930.docx"),null))));
        assertThrows(IllegalArgumentException.class,()->Xml.parse("<!DOCTYPE foo [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><foo>&x;</foo>".getBytes()));
        assertThrows(IllegalArgumentException.class,()->new WordPackage("r","bad.docx","bad".getBytes(),null));
    }
    private static int count(String text,String part) {return (text.length()-text.replace(part,"").length())/part.length();}
}
