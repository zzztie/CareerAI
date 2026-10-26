package com.zzztie.careerai.dto;

public record DocumentChunk(
        String source,
        int index,
        String content
) {
    public int length() {
        return content.length();
    }
}