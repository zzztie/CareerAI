
package com.zzztie.careerai.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ResumePdfTextExtractor {

    public String extractText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException(
                    "PDF文件内容不能为空"
            );
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                        "PDF中没有提取到文本，可能是扫描件"
                );
            }
            return text.trim();
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "PDF文件读取或解析失败",
                    e
            );
        }
    }
}
