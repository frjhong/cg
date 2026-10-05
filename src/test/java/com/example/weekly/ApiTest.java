package com.example.weekly;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="weekly.ai.fixture=src/test/resources/sample-plan.json")
@AutoConfigureMockMvc
class ApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Test void uploadRealDocumentsDownloadAndDelete()throws Exception {
        var response=mvc.perform(multipart("/api/merge")
            .file(new MockMultipartFile("files","0916.docx","application/octet-stream",SampleMergeTest.sample("0916.docx")))
            .file(new MockMultipartFile("files","0930.docx","application/octet-stream",SampleMergeTest.sample("0930.docx"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.summary.itemCount").value(27)).andReturn();
        var json=mapper.readTree(response.getResponse().getContentAsByteArray());String id=json.path("id").asText();
        var download=mvc.perform(get(json.path("downloadUrl").asText())).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andExpect(header().exists("Content-Disposition")).andReturn();
        var word=new WordPackage("out","result.docx",download.getResponse().getContentAsByteArray(),null);assertTrue(Xml.text(word.body).contains("T2系統移轉中"));
        mvc.perform(get(json.path("auditUrl").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("fixture-replay"));
        mvc.perform(delete("/api/reports/"+id)).andExpect(status().isOk());mvc.perform(get("/api/reports/"+id+"/word")).andExpect(status().isNotFound());
    }
    @Test void invalidUploadsReturnClearError()throws Exception {
        mvc.perform(multipart("/api/merge").file(new MockMultipartFile("files","report.txt","text/plain","bad".getBytes())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        mvc.perform(get("/api/config")).andExpect(jsonPath("$.mode").value("fixture-replay"));
    }
}
