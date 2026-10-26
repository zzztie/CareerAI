package com.zzztie.careerai.dto;

import java.util.List;

public record ResumeParseResult(
        List<String> skills,
        List<String> education,
        List<String> projects
) {
}