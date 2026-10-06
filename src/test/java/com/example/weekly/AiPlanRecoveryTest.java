package com.example.weekly;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class AiPlanRecoveryTest {
    private final ObjectMapper mapper=new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings={"duplicate-id","unknown-section","missing-reason","empty-versions",
        "missing-block","duplicate-block","invalid-json","null-plan","truncated"})
    void rejectsInvalidPlanAndUsesCorrectedPlanWithRealWordSamples(String fault)throws Exception {
        String valid=mapper.writeValueAsString(SampleMergeTest.plan());
        try(var api=new MockApi(200,List.of(reply(invalid(valid,fault),fault.equals("truncated")?"length":"stop"),reply(valid,"stop")))) {
            var result=new MergeService(api.planner()).merge(List.of(
                new MergeService.Input("0916.docx",SampleMergeTest.sample("0916.docx"),null),
                new MergeService.Input("0930.docx",SampleMergeTest.sample("0930.docx"),null)));
            assertEquals(2,api.requests.size());
            var repair=api.requests.get(1).path("messages");
            assertEquals(4,repair.size());
            assertEquals("assistant",repair.path(2).path("role").asText());
            String feedback=repair.path(3).path("content").asText();
            assertTrue(feedback.contains("伺服器驗證未通過"));
            assertTrue(feedback.contains("完整 JSON"));
            String expected=switch(fault) {
                case "duplicate-id" -> "ID 重複";
                case "unknown-section" -> "不存在的章節";
                case "missing-reason" -> "需要 title、reason";
                case "empty-versions" -> "versions 不可為空";
                case "missing-block" -> "遺漏內容";
                case "duplicate-block" -> "重複使用同一區塊";
                case "truncated" -> "輸出被截斷";
                case "null-plan" -> "空計畫";
                default -> "指定結構的 JSON";
            };
            assertTrue(feedback.contains(expected),feedback);
            assertEquals(27,result.audit().get("itemCount"));
            assertEquals(2,result.audit().get("duplicateGroups"));
            assertEquals(14,result.audit().get("oldOnlyItems"));
            var out=new WordPackage("out","output.docx",result.docx(),null);
            String text=Xml.text(out.body);
            assertTrue(text.contains("applications manager軟體更新"));
            assertFalse(text.contains("廠商協助升級失敗"));
            assertTrue(text.contains("GitLab"));
            assertTrue(text.contains("4,670,754"));
            assertTrue(text.contains("1,044,832"));
        }
    }

    @Test void stopsAfterThreeInvalidPlansAndDoesNotProduceWord()throws Exception {
        String invalid=invalid(mapper.writeValueAsString(SampleMergeTest.plan()),"duplicate-id");
        try(var api=new MockApi(200,List.of(reply(invalid,"stop")))) {
            var error=assertThrows(IllegalArgumentException.class,()->new MergeService(api.planner()).merge(List.of(
                new MergeService.Input("0916.docx",SampleMergeTest.sample("0916.docx"),null),
                new MergeService.Input("0930.docx",SampleMergeTest.sample("0930.docx"),null))));
            assertEquals(3,api.requests.size());
            assertTrue(error.getMessage().contains("3 次嘗試"));
            assertTrue(error.getMessage().contains("ID 重複"));
            assertTrue(error.getMessage().contains("未產生彙整文件"));
            assertEquals(4,api.requests.get(2).path("messages").size());
        }
    }

    @Test void doesNotRetryAuthenticationErrors()throws Exception {
        try(var api=new MockApi(401,List.of("{}"))) {
            var error=assertThrows(IllegalStateException.class,()->api.planner().plan(SampleMergeTest.reports()));
            assertTrue(error.getMessage().contains("HTTP 401"));
            assertEquals(1,api.requests.size());
        }
    }

    private String invalid(String valid,String fault)throws Exception {
        if(fault.equals("invalid-json")) return "{\"sections\":[";
        if(fault.equals("null-plan")) return "null";
        ObjectNode plan=(ObjectNode)mapper.readTree(valid);
        ArrayNode items=(ArrayNode)plan.get("items");
        ObjectNode first=(ObjectNode)items.get(0);
        switch(fault) {
            case "duplicate-id" -> ((ObjectNode)items.get(1)).put("id",first.path("id").asText());
            case "unknown-section" -> first.put("sectionId","undeclared-section");
            case "missing-reason" -> first.remove("reason");
            case "empty-versions" -> first.putArray("versions");
            case "missing-block" -> items.remove(0);
            case "duplicate-block" -> {var duplicate=first.deepCopy();duplicate.put("id","unique-but-duplicate-blocks");items.add(duplicate);}
        }
        return mapper.writeValueAsString(plan);
    }

    private String reply(String plan,String finishReason)throws Exception {
        return mapper.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason",finishReason,"message",Map.of("content",plan)))));
    }

    private class MockApi implements AutoCloseable {
        final List<JsonNode> requests=new CopyOnWriteArrayList<>();
        final HttpServer server;
        MockApi(int status,List<String> responses)throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/v1/chat/completions",exchange->{
                requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
                byte[] body=responses.get(Math.min(requests.size()-1,responses.size()-1)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,body.length);
                exchange.getResponseBody().write(body);exchange.close();
            });server.start();
        }
        AiPlanner planner() {return new AiPlanner(mapper,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1","test-key","test-model","");}
        public void close() {server.stop(0);}
    }
}
