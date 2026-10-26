package com.zzztie.careerai.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlainTextReader {

    public String read(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("文件路径不能为 null");
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}