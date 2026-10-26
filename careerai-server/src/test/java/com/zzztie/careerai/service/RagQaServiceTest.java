package com.zzztie.careerai.service;

import com.zzztie.careerai.dto.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagQaServiceTest {

    @Test
    void detailedComparisonReportsModelUsage() {
        RagIndexService indexService = mock(RagIndexService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec =
                mock(ChatClient.CallResponseSpec.class);
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Generation generation = mock(Generation.class);
        Usage usage = mock(Usage.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.chatResponse()).thenReturn(response);
        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("匹配 [R1][J1]"));
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(120);
        when(usage.getCompletionTokens()).thenReturn(30);

        String question = "是否匹配？";
        when(indexService.searchTopKWithinDistance("resume-test", question, 2, 0.5))
                .thenReturn(List.of(new DocumentChunk("resume.txt", 1, "会 Java")));
        when(indexService.searchTopKWithinDistance("job-test", question, 2, 0.5))
                .thenReturn(List.of(new DocumentChunk("job.txt", 1, "要求 Java")));

        RagQaService service = new RagQaService(indexService, builder);
        RagQaService.EvaluationResult result = service.compareResumeAndJobDetailed(
                "resume-test", "job-test", null, question, 2, 0.5
        );

        assertEquals(120, result.promptTokens());
        assertEquals(30, result.completionTokens());
        assertTrue(result.response().contains("匹配 [R1][J1]"));
        assertTrue(result.response().contains("[R1] 简历：resume.txt，第 1 块"));
        assertTrue(result.response().contains("[J1] 岗位 JD：job.txt，第 1 块"));
        assertTrue(result.retrievalMs() >= 0);
        assertTrue(result.modelMs() >= 0);
    }

    @Test
    void refusesComparisonWhenJobEvidenceMissing() {
        RagIndexService indexService = mock(RagIndexService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        String question = "我适合这个岗位吗？";
        when(indexService.searchTopKWithinDistance(
                "resume-test", question, 2, 0.5
        )).thenReturn(List.of(
                new DocumentChunk("resume.txt", 1, "使用 Spring Boot 开发后台接口")
        ));
        when(indexService.searchTopKWithinDistance(
                "job-test", question, 2, 0.5
        )).thenReturn(List.of());

        RagQaService service = new RagQaService(indexService, builder);
        String result = service.compareResumeAndJob(
                "resume-test", "job-test", question, 2, 0.5
        );

        assertEquals("无法比较：未检索到岗位 JD的相关证据。", result);        verifyNoInteractions(chatClient);
    }

    @Test
    void includesResumeAndJobEvidenceWithSources() {
        RagIndexService indexService = mock(RagIndexService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec =
                mock(ChatClient.CallResponseSpec.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(
                "简历有 Spring Boot 接口经验 [R1]，岗位要求 Spring Boot [J1]。"
        );

        String question = "我适合这个岗位吗？";
        when(indexService.searchTopKWithinDistance(
                "resume-test", question, 2, 0.5
        )).thenReturn(List.of(
                new DocumentChunk("resume.txt", 1, "使用 Spring Boot 开发后台接口")
        ));
        when(indexService.searchTopKWithinDistance(
                "job-test", question, 2, 0.5
        )).thenReturn(List.of(
                new DocumentChunk("job.txt", 3, "要求使用 Spring Boot 开发后台接口")
        ));

        RagQaService service = new RagQaService(indexService, builder);
        String result = service.compareResumeAndJob(
                "resume-test", "job-test", question, 2, 0.5
        );

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(prompt.capture());
        String sentToModel = prompt.getValue();

        assertTrue(sentToModel.contains("[R1] 简历：resume.txt，第 1 块"));
        assertTrue(sentToModel.contains("使用 Spring Boot 开发后台接口"));
        assertTrue(sentToModel.contains("[J1] 岗位 JD：job.txt，第 3 块"));
        assertTrue(sentToModel.contains("要求使用 Spring Boot 开发后台接口"));

        assertTrue(result.contains("简历有 Spring Boot 接口经验 [R1]"));
        assertTrue(result.contains("[R1] 简历：resume.txt，第 1 块"));
        assertTrue(result.contains("[J1] 岗位 JD：job.txt，第 3 块"));
    }

    @Test
    void includesKnowledgeAsSeparateEvidence() {
        RagIndexService indexService = mock(RagIndexService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec =
                mock(ChatClient.CallResponseSpec.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("已收到资料");

        String question = "我该怎样准备面试？";
        when(indexService.searchTopKWithinDistance(
                "resume-test", question, 2, 0.5
        )).thenReturn(List.of(
                new DocumentChunk("resume.txt", 1, "做过 Spring Boot 项目")
        ));
        when(indexService.searchTopKWithinDistance(
                "job-test", question, 2, 0.5
        )).thenReturn(List.of(
                new DocumentChunk("job.txt", 1, "Java 后端实习岗位")
        ));
        when(indexService.searchTopKWithinDistance(
                "knowledge-test", question, 2, 0.5
        )).thenReturn(List.of(
                new DocumentChunk(
                        "java-backend-guide.txt", 2,
                        "准备面试时，梳理真实项目中的接口设计和数据库操作"
                )
        ));

        RagQaService service = new RagQaService(indexService, builder);
        String result = service.compareResumeAndJob(
                "resume-test", "job-test", "knowledge-test",
                question, 2, 0.5
        );

        ArgumentCaptor<String> userPrompt =
                ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(userPrompt.capture());

        assertTrue(userPrompt.getValue().contains(
                "[K1] 知识库：java-backend-guide.txt，第 2 块"
        ));
        assertTrue(userPrompt.getValue().contains(
                "梳理真实项目中的接口设计和数据库操作"
        ));
        assertTrue(result.contains(
                "[K1] 知识库：java-backend-guide.txt，第 2 块"
        ));

        ArgumentCaptor<String> systemPrompt =
                ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemPrompt.capture());
        assertTrue(systemPrompt.getValue().contains(
                "不能用来推断目标岗位没有写出的要求"
        ));
    }

    @Test
    void refusesComparisonWithoutJobEvenWhenKnowledgeKeyExists() {
        RagIndexService indexService = mock(RagIndexService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);

        String question = "我适合这个岗位吗？";
        when(indexService.searchTopKWithinDistance(
                "resume-test", question, 2, 0.5
        )).thenReturn(List.of(
                new DocumentChunk("resume.txt", 1, "做过 Spring Boot 项目")
        ));
        when(indexService.searchTopKWithinDistance(
                "job-test", question, 2, 0.5
        )).thenReturn(List.of());

        RagQaService service = new RagQaService(indexService, builder);
        String result = service.compareResumeAndJob(
                "resume-test", "job-test", "knowledge-test",
                question, 2, 0.5
        );

        assertEquals("无法比较：未检索到岗位 JD的相关证据。", result);
        verifyNoInteractions(chatClient);
    }
}
