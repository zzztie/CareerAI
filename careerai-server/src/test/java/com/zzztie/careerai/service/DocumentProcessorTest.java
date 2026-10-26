package com.zzztie.careerai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.zzztie.careerai.dto.DocumentChunk;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentProcessorTest {

    @TempDir
    Path tempDir;

    private final DocumentProcessor processor = new DocumentProcessor();

    @Test
    void shouldRejectUnsupportedFileType() throws IOException {
        Path path = tempDir.resolve("notes.docx");
        Files.writeString(path, "test content", StandardCharsets.UTF_8);

        assertThrows(
                IllegalArgumentException.class,
                () -> processor.process(path, 30, 5)
        );
    }

    @Test
    void shouldReportMissingFile() {
        Path path = tempDir.resolve("missing.md");

        assertThrows(
                NoSuchFileException.class,
                () -> processor.process(path, 30, 5)
        );
    }

    @Test
    void shouldRejectInvalidPdf() throws IOException {
        Path path = tempDir.resolve("broken.pdf");
        Files.writeString(
                path,
                "This is plain text, not a PDF.",
                StandardCharsets.UTF_8
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> processor.process(path, 30, 5)
        );

        assertTrue(error.getCause() instanceof IOException);
    }

    @Test
    void shouldPreserveProjectParagraphs() throws IOException {
        Path path = tempDir.resolve("resume.md");
        Files.writeString(
                path,
                "Java项目\n使用Redis\n\n考试系统\n试卷管理",
                StandardCharsets.UTF_8
        );

        var fixedChunks = processor.process(path, 20, 0);
        var paragraphChunks = processor.processByParagraph(path, 20);

        fixedChunks.forEach(c -> System.out.println(
                "固定长度：" + c.content().replace("\n", "\\n")
        ));

        paragraphChunks.forEach(c -> System.out.println(
                "段落优先：" + c.content().replace("\n", "\\n")
        ));

        assertEquals(
                List.of(
                        new DocumentChunk("resume.md", 1, "Java项目\n使用Redis"),
                        new DocumentChunk("resume.md", 2, "考试系统\n试卷管理")
                ),
                paragraphChunks
        );
    }
}