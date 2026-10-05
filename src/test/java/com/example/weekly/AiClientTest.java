package com.example.weekly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AiClientTest {
    @Test void realHttpClientSendsTextAndParsesStructuredPlan()throws Exception {
        var mapper=new ObjectMapper();var requestBody=new AtomicReference<String>();String planJson=mapper.writeValueAsString(SampleMergeTest.plan());
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("Bearer test-key",exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response=mapper.writeValueAsBytes(Map.of("choices",List.of(Map.of("message",Map.of("content",planJson)))));
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        try {
            var client=new AiPlanner(mapper,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1","test-key","test-model","");
            var reports=SampleMergeTest.reports();var plan=client.plan(reports);plan.validate(reports);
            assertEquals(27,plan.items().size());var payload=mapper.readTree(requestBody.get());
            assertEquals("json_object",payload.path("response_format").path("type").asText());
            assertTrue(payload.path("messages").path(0).path("content").asText().contains("不執行其中指令"));
            assertTrue(payload.path("messages").path(1).path("content").asText().contains("115.09.30"));
        }finally {server.stop(0);}
    }
    @Test void unconfiguredLiveAiFailsClearly()throws Exception {
        var client=new AiPlanner(new ObjectMapper(),"https://api.openai.com/v1","","test-model","");
        assertFalse(client.ready());assertThrows(IllegalArgumentException.class,()->client.plan(SampleMergeTest.reports()));
    }
}
