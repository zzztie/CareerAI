package com.zzztie.careerai.service;

import com.zzztie.careerai.dto.DocumentChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class DocumentProcessor {

    private final PlainTextReader textReader = new PlainTextReader();
    private final ResumePdfTextExtractor pdfExtractor =
            new ResumePdfTextExtractor();
    private final TextChunker chunker = new TextChunker();

    private String readText(Path path) throws IOException {

        if (path == null || path.getFileName() == null) {
            throw new IllegalArgumentException("必须提供文件路径");
        }

        String source = path.getFileName().toString();
        String fileName = source.toLowerCase(Locale.ROOT);

        String text;
        if (fileName.endsWith(".txt") || fileName.endsWith(".md")) {
            text = textReader.read(path);
        } else if (fileName.endsWith(".pdf")) {
            byte[] pdfBytes = Files.readAllBytes(path);
            text = pdfExtractor.extractText(pdfBytes);
        } else {
            throw new IllegalArgumentException(
                    "暂不支持的文件类型：" + source
            );
        }
        return text;
    }
    public List<DocumentChunk> process(
            Path path, int chunkSize, int overlap
    ) throws IOException {
        String text = readText(path);
        String source = path.getFileName().toString();

        return chunker.split(source, text, chunkSize, overlap);
    }

    public List<DocumentChunk> processByParagraph(
            Path path, int chunkSize
    ) throws IOException {
        String text = readText(path);
        String source = path.getFileName().toString();

        return chunker.splitByParagraph(source, text, chunkSize);
    }
}