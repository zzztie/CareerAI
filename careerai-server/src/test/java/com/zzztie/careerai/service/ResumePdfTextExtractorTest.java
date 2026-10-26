
package com.zzztie.careerai.service;

import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumePdfTextExtractorTest {

    @Test
    void shouldExtractTextFromPdf(@TempDir Path tempDir) throws IOException {
        byte[] pdfBytes;

        // 1. 创建一个用于测试的 PDF
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream =
                     new ByteArrayOutputStream()) {

            document.addPage(new PDPage());

            try (PDPageContentStream contentStream =
                         new PDPageContentStream(
                                 document,
                                 document.getPage(0))) {

                contentStream.beginText();
                contentStream.setFont(
                        new PDType1Font(
                                Standard14Fonts.FontName.HELVETICA),
                        12
                );
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Java Spring Boot MySQL");
                contentStream.endText();
            }
            document.save(outputStream);
            pdfBytes = outputStream.toByteArray();
        }
        // 2. 调用我们自己编写的 PDF 文本提取类
        ResumePdfTextExtractor extractor =
                new ResumePdfTextExtractor();
        String text = extractor.extractText(pdfBytes);
        // 3. 检查是否成功提取文字
        assertTrue(
                text.contains("Java Spring Boot MySQL"),
                "PDF 文本提取结果不符合预期"
        );
        // 4. 将提取出的 PDF 文本切分为 Chunk
        TextChunker chunker = new TextChunker();
        var chunks = chunker.split("test-resume.pdf", text, 10, 2);
        // 5. 输出每个块的来源、序号、长度和正文
        chunks.forEach(c -> System.out.printf(
                "source=%s, index=%d, length=%d, content=[%s]%n",
                c.source(), c.index(), c.length(), c.content()
        ));
        // 6. 验证切分内容及重叠位置
        assertEquals(
                List.of("Java Sprin", "ing Boot M", " MySQL"),
                chunks.stream().map(c -> c.content()).toList()
        );
        // 7. 验证每个块的来源和连续序号
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals("test-resume.pdf", chunks.get(i).source());
            assertEquals(i + 1, chunks.get(i).index());
        }
        // 8. 验证 PDF 文件通过统一入口处理
        DocumentProcessor processor = new DocumentProcessor();

        Path pdfPath = tempDir.resolve("test-resume.pdf");
        Files.write(pdfPath, pdfBytes);

        var pdfChunks = processor.process(pdfPath, 10, 2);
        assertEquals(chunks, pdfChunks);

        // 9. 验证 TXT 和 Markdown 文件通过统一入口处理
        for (String fileName : List.of("notes.txt", "notes.MD")) {
            Path textPath = tempDir.resolve(fileName);
            Files.writeString(
                    textPath,
                    "Java Spring Boot MySQL",
                    StandardCharsets.UTF_8
            );

            var textChunks = processor.process(textPath, 10, 2);

            assertEquals(
                    List.of("Java Sprin", "ing Boot M", " MySQL"),
                    textChunks.stream().map(c -> c.content()).toList()
            );

            for (int i = 0; i < textChunks.size(); i++) {
                assertEquals(fileName, textChunks.get(i).source());
                assertEquals(i + 1, textChunks.get(i).index());
            }
        }
    }
}
