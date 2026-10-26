package com.zzztie.careerai.service;

import com.zzztie.careerai.dto.DocumentChunk;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {
    public List<DocumentChunk> split(
            String source, String text, int chunkSize, int overlap
    ){
        if(source == null || source.isBlank()){
            throw new IllegalArgumentException("文档来源不能为空");
        }
        if(text == null){
            throw new IllegalArgumentException("文档文本不能为null");
        }
        if(chunkSize <=0){
            throw new IllegalArgumentException("分块大小必须大于0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "重叠长度必须大于等于 0，且小于分块大小"
            );
        }
        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 1;
        while (start < text.length()) {
            int size = Math.min(chunkSize, text.length() - start);
            int end = start + size;
            String content = text.substring(start, end);
            chunks.add(new DocumentChunk(source, index, content));
            if (end == text.length()) {
                break;
            }
            start = end - overlap;
            index++;
        }
        return chunks;
    }

    public List<String> splitParagraphs(String text) {
        if (text == null) {
            throw new IllegalArgumentException("文档文本不能为 null");
        }
        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String[] parts = normalized.split("\n\\s*\n");
        List<String> paragraphs = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                paragraphs.add(part.strip());
            }
        }
        return paragraphs;
    }

    public List<DocumentChunk> splitByParagraph(
            String source, String text, int chunkSize
    ) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("文档来源不能为空");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("分块大小必须大于 0");
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> paragraphs = splitParagraphs(text);

        for (String paragraph : paragraphs) {
            List<DocumentChunk> parts =
                    split(source, paragraph, chunkSize, 0);

            for (DocumentChunk part : parts) {
                chunks.add(new DocumentChunk(
                        source,
                        chunks.size() + 1,
                        part.content()
                ));
            }
        }

        return chunks;
    }
}
