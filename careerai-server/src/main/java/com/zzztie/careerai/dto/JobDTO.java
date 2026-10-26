
package com.zzztie.careerai.dto;

public record JobDTO(
        Long id,
        String createTime,
        String updateTime,
        String title,
        String company,
        String description,
        String status,
        String summary,
        String entityType
) {
}
