package com.example.weekly;

import org.junit.jupiter.api.Test;
import org.w3c.dom.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceImportTest {
    @Test void importedImagesAndHyperlinksCannotOverwriteTemplateRelationships()throws Exception {
        var target=SampleMergeTest.reports().getFirst();var source=SampleMergeTest.reports().get(1);
        var originalRels=Xml.parse(target.parts.get("word/_rels/document.xml.rels"));
        var sourceRels=Xml.parse(source.parts.get("word/_rels/document.xml.rels"));
        var image=sourceRels.createElementNS(Xml.REL,"Relationship");image.setAttribute("Id","testImage");image.setAttribute("Type",Xml.R+"/image");image.setAttribute("Target","media/test.png");sourceRels.getDocumentElement().appendChild(image);
        var link=sourceRels.createElementNS(Xml.REL,"Relationship");link.setAttribute("Id","testLink");link.setAttribute("Type",Xml.R+"/hyperlink");link.setAttribute("Target","https://example.com");link.setAttribute("TargetMode","External");sourceRels.getDocumentElement().appendChild(link);
        source.parts.put("word/_rels/document.xml.rels",Xml.bytes(sourceRels));
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aKWQAAAAASUVORK5CYII=");source.parts.put("word/media/test.png",png);
        var types=Xml.parse(source.parts.get("[Content_Types].xml"));var type=types.createElementNS(types.getDocumentElement().getNamespaceURI(),"Default");type.setAttribute("Extension","png");type.setAttribute("ContentType","image/png");types.getDocumentElement().appendChild(type);source.parts.put("[Content_Types].xml",Xml.bytes(types));
        String xml="<w:p xmlns:w=\""+Xml.W+"\" xmlns:r=\""+Xml.R+"\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><w:hyperlink r:id=\"testLink\"><w:r><w:t>附件說明</w:t></w:r></w:hyperlink><w:r><w:drawing><a:blip r:embed=\"testImage\"/></w:drawing></w:r></w:p>";
        var copied=new PartImporter(target,source).copy(Xml.parse(xml.getBytes(StandardCharsets.UTF_8)).getDocumentElement());target.body.insertBefore(copied,Xml.first(target.body,"sectPr"));
        var rels=Xml.parse(target.parts.get("word/_rels/document.xml.rels"));
        for(var old:Xml.children(originalRels.getDocumentElement()))assertTrue(Xml.children(rels.getDocumentElement()).stream().anyMatch(old::isEqualNode));
        var importedImage=Xml.children(rels.getDocumentElement()).stream().filter(e->e.getAttribute("Id").equals("import_r1_testImage")).findFirst().orElseThrow();
        assertArrayEquals(png,target.parts.get("word/"+importedImage.getAttribute("Target")));
        assertEquals("import_r1_testLink",Xml.all(copied,Xml.W,"hyperlink").getFirst().getAttributeNS(Xml.R,"id"));
    }
    @Test void conflictingStyleDefinitionsAreRenamedAndTemplateStyleIsPreserved()throws Exception {
        var target=SampleMergeTest.reports().getFirst();var source=SampleMergeTest.reports().get(1);
        var src=Xml.parse(source.parts.get("word/styles.xml"));var style=Xml.all(src,Xml.W,"style").stream().filter(e->e.getAttributeNS(Xml.W,"styleId").equals("a4")).findFirst().orElseThrow();
        var name=Xml.first(style,"name");Xml.val(name,"Different source definition");source.parts.put("word/styles.xml",Xml.bytes(src));
        var old=Xml.parse(target.parts.get("word/styles.xml"));
        var copied=new PartImporter(target,source).copy(source.block(20));assertEquals("import_r1_a4",Xml.val(Xml.first(copied,"pStyle")));
        var styles=Xml.parse(target.parts.get("word/styles.xml"));for(var original:Xml.all(old,Xml.W,"style"))assertTrue(Xml.all(styles,Xml.W,"style").stream().anyMatch(original::isEqualNode));
        assertTrue(Xml.all(styles,Xml.W,"style").stream().anyMatch(e->"import_r1_a4".equals(e.getAttributeNS(Xml.W,"styleId"))));
    }
    @Test void sameDateProgressConflictFailsInsteadOfPickingArbitrarily()throws Exception {
        var reports=List.of(new WordPackage("r0","0930.docx",SampleMergeTest.sample("0930.docx"),java.time.LocalDate.of(2026,9,30)),new WordPackage("r1","0916.docx",SampleMergeTest.sample("0916.docx"),java.time.LocalDate.of(2026,9,30)));
        assertThrows(IllegalArgumentException.class,()->SampleMergeTest.service().render(reports,SampleMergeTest.plan(),"test"));
    }
    @Test void oldOnlyParentAndChildHeadingsAreBothRetained()throws Exception {
        var p=SampleMergeTest.plan();var sections=new ArrayList<>(p.sections());
        sections.add(new MergePlan.Section("old-parent","舊檔父章節",null,List.of(new MergePlan.Ref("r1",34))));
        sections.add(new MergePlan.Section("old-child","舊檔子章節","old-parent",List.of(new MergePlan.Ref("r1",35))));
        var items=new ArrayList<>(p.items().stream().filter(i->!i.id().equals("cdc-project")).toList());
        items.add(new MergePlan.Item("cdc-project","CDC 進度","old-child","舊檔獨有",List.of(new MergePlan.Version("r1",List.of(36)))));
        var result=SampleMergeTest.service().render(SampleMergeTest.reports(),new MergePlan(sections,items,p.metadata()),"test");
        String text=Xml.text(new WordPackage("out","out.docx",result.docx(),null).body);
        assertTrue(text.contains("建置異質資料庫即時移轉系統"));assertTrue(text.contains("因應目前視覺化動態報表"));assertTrue(text.contains("T2系統移轉中"));
        assertTrue(text.indexOf("建置異質資料庫即時移轉系統")<text.indexOf("因應目前視覺化動態報表"));
    }
}
