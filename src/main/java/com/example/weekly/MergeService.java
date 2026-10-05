package com.example.weekly;

import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import java.time.LocalDate;
import java.util.*;

@Service
final class MergeService {
    record Input(String name,byte[] bytes,LocalDate date) {}
    record Decision(String id,String title,String section,String chosenFile,String chosenDate,List<String> replacedFiles,String reason) {}
    record Result(byte[] docx,Map<String,Object> audit) {}
    private final AiPlanner planner;
    MergeService(AiPlanner planner) { this.planner=planner; }

    Result merge(List<Input> inputs) {
        if(inputs.size()<2 || inputs.size()>20)throw new IllegalArgumentException("請上傳 2 至 20 份 DOCX 週報");
        var parsed=new ArrayList<WordPackage>();
        for(var i:inputs)parsed.add(new WordPackage("pending",i.name(),i.bytes(),i.date()));
        if(parsed.stream().map(r->r.sha256).distinct().count()!=parsed.size())throw new IllegalArgumentException("請勿重複上傳相同文件");
        parsed.sort(Comparator.comparing((WordPackage r)->r.date).reversed().thenComparing(r->r.name));
        var reports=new ArrayList<WordPackage>();
        for(int i=0;i<parsed.size();i++) {
            var p=parsed.get(i);var in=inputs.stream().filter(x->x.name().equals(p.name)).findFirst().orElseThrow();
            reports.add(new WordPackage("r"+i,p.name,in.bytes(),p.date));
        }
        var plan=planner.plan(reports);return render(reports,plan,planner.mode());
    }
    Result render(List<WordPackage> reports,MergePlan plan,String mode) {
        plan.validate(reports);
        var template=reports.getFirst();var byId=new HashMap<String,WordPackage>();reports.forEach(r->byId.put(r.id,r));
        var sections=new HashMap<String,MergePlan.Section>();plan.sections().forEach(s->sections.put(s.id(),s));
        var winners=new LinkedHashMap<String,MergePlan.Version>();
        var decisions=new ArrayList<Decision>();int oldOnly=0,duplicate=0,inserted=0;
        for(var item:plan.items()) {
            var versions=new ArrayList<>(item.versions());versions.sort(Comparator.comparing((MergePlan.Version v)->byId.get(v.reportId()).date).reversed().thenComparing(MergePlan.Version::reportId));
            var chosen=versions.getFirst();var file=byId.get(chosen.reportId());
            if(versions.size()>1 && byId.get(versions.get(1).reportId()).date.equals(file.date))
                throw new IllegalArgumentException("同一事項有相同日期的不同版本："+item.title()+"；請調整週報日期以指定先後順序");
            winners.put(item.id(),chosen);
            if(item.versions().size()>1)duplicate++;if(!chosen.reportId().equals(template.id))oldOnly++;
            decisions.add(new Decision(item.id(),item.title(),sections.get(item.sectionId()).title(),file.name,file.date.toString(),
                versions.stream().skip(1).map(v->byId.get(v.reportId()).name).toList(),item.reason()));
        }
        // Anchors are original template nodes, cached before insertion so old items remain in input order.
        var anchors=new HashMap<String,Node>();
        for(var section:plan.sections())anchors.put(section.id(),anchor(section,plan,template,sections));
        var importers=new HashMap<String,PartImporter>();
        var orderedSections=new ArrayList<>(plan.sections());
        orderedSections.sort(Comparator.comparingInt(s->depth(s,sections)));
        for(var section:orderedSections) {
            var oldItems=plan.items().stream().filter(i->i.sectionId().equals(section.id()) && !winners.get(i.id()).reportId().equals(template.id)).toList();
            boolean hasOldContent=plan.items().stream().anyMatch(i->descendant(i.sectionId(),section.id(),sections) && !winners.get(i.id()).reportId().equals(template.id));
            if(!hasOldContent)continue;
            Node anchor=anchors.get(section.id());
            if(section.headings().stream().noneMatch(h->h.reportId().equals(template.id)) && !section.headings().isEmpty()) {
                String headingReport=section.headings().stream().map(MergePlan.Ref::reportId).min(Comparator.comparing(id->byId.get(id).date,Comparator.reverseOrder())).orElseThrow();
                for(var h:section.headings())if(h.reportId().equals(headingReport)) {
                    var importer=importers.computeIfAbsent(h.reportId(),id->new PartImporter(template,byId.get(id)));
                    template.body.insertBefore(importer.copy(byId.get(h.reportId()).block(h.block())),anchor);inserted++;
                }
            }
            Element numberingPrototype=plan.items().stream().filter(i->i.sectionId().equals(section.id()))
                .flatMap(i->i.versions().stream()).filter(v->v.reportId().equals(template.id))
                .map(v->template.block(v.blocks().getFirst())).filter(e->Xml.first(e,"numId")!=null).findFirst().orElse(null);
            int ordinal=(int)plan.items().stream().filter(i->i.sectionId().equals(section.id()) && winners.get(i.id()).reportId().equals(template.id)).count();
            for(var item:oldItems) {
                var v=winners.get(item.id());var source=byId.get(v.reportId());
                var importer=importers.computeIfAbsent(v.reportId(),id->new PartImporter(template,source));ordinal++;
                for(int index:v.blocks()) {
                    var original=source.block(index);var copy=importer.copy(original);
                    // Top-level items join the template list. Nested progress lists retain imported definitions.
                    if(index==v.blocks().getFirst() && numberingPrototype!=null && Xml.first(copy,"numId")!=null)
                        Xml.val(Xml.first(copy,"numId"),Xml.val(Xml.first(numberingPrototype,"numId")));
                    if(index==v.blocks().getFirst())renumberLiteral(copy,ordinal);
                    template.body.insertBefore(copy,anchor);inserted++;
                }
            }
        }
        // Word recalculates automatic pagination. Cached breaks describe the old layout and can
        // become phantom blank pages after inserting paragraphs (observed in the supplied sample).
        for(var cached:Xml.all(template.document,Xml.W,"lastRenderedPageBreak"))cached.getParentNode().removeChild(cached);
        int trailingEmptyParagraphs=0;
        var bodyElements=Xml.children(template.body);
        int tail=bodyElements.size()-1;
        if(tail>=0 && "sectPr".equals(bodyElements.get(tail).getLocalName()))tail--;
        while(tail>=0) {
            var p=bodyElements.get(tail);
            // Only an empty formatting-only paragraph: keep manual breaks, fields, bookmarks,
            // section boundaries and all intentional inter-item spacing.
            if(!"p".equals(p.getLocalName()) || Xml.children(p).stream().anyMatch(e->!"pPr".equals(e.getLocalName())) || Xml.first(p,"sectPr")!=null)break;
            template.body.removeChild(p);trailingEmptyParagraphs++;tail--;
        }
        var audit=new LinkedHashMap<String,Object>();
        audit.put("template",Map.of("filename",template.name,"date",template.date.toString(),"sha256",template.sha256));
        audit.put("mode",mode);audit.put("inputFiles",reports.stream().map(r->Map.of("filename",r.name,"date",r.date.toString(),"sha256",r.sha256)).toList());
        audit.put("itemCount",plan.items().size());audit.put("duplicateGroups",duplicate);audit.put("oldOnlyItems",oldOnly);audit.put("insertedBlocks",inserted);
        audit.put("removedTrailingEmptyParagraphs",trailingEmptyParagraphs);
        audit.put("decisions",decisions);audit.put("plan",plan);
        var warnings=new ArrayList<String>();
        warnings.add("保留範本頁面設定、樣式、圖片與頁首頁尾；新增內容會自然改變分頁，需用 Word 確認最終頁面。");
        if(mode.equals("fixture-replay"))warnings.add("目前使用指定範例的語意判讀重播資料，未呼叫外部 AI API。");
        if(reports.stream().filter(r->r.date.equals(template.date)).count()>1)warnings.add("最新日期有多份文件，範本依檔名排序選定；相同事項若日期相同會停止彙整。");
        audit.put("warnings",warnings);return new Result(template.write(),audit);
    }
    private static int depth(MergePlan.Section s,Map<String,MergePlan.Section> sections) { int d=0;while(s.parentId()!=null && !s.parentId().isBlank()){s=sections.get(s.parentId());d++;}return d; }
    private static boolean descendant(String child,String ancestor,Map<String,MergePlan.Section> sections) {
        var c=sections.get(child);while(c!=null) { if(c.id().equals(ancestor))return true;c=sections.get(c.parentId()); }return false;
    }
    private static Node anchor(MergePlan.Section section,MergePlan plan,WordPackage template,Map<String,MergePlan.Section> sections) {
        int max=-1;
        for(var s:plan.sections())if(descendant(s.id(),section.id(),sections))for(var h:s.headings())if(h.reportId().equals(template.id))max=Math.max(max,h.block());
        for(var item:plan.items())if(descendant(item.sectionId(),section.id(),sections))for(var v:item.versions())if(v.reportId().equals(template.id))for(int b:v.blocks())max=Math.max(max,b);
        if(max>=0)return template.block(max).getNextSibling();
        if(section.parentId()!=null && !section.parentId().isBlank())return anchor(sections.get(section.parentId()),plan,template,sections);
        // Missing top-level sections go before the body section properties.
        return Xml.children(template.body).stream().filter(e->"sectPr".equals(e.getLocalName())).findFirst().orElse(null);
    }
    private static void renumberLiteral(Element paragraph,int ordinal) {
        if(!Xml.text(paragraph).matches("^提報事項[：:].*"))return;
        for(var t:Xml.all(paragraph,Xml.W,"t")) {
            String value=t.getTextContent();if(value.matches(".*[（(]\\d+[)）].*")) {t.setTextContent(value.replaceFirst("[（(]\\d+[)）]","("+ordinal+")"));return;}
        }
    }
}
