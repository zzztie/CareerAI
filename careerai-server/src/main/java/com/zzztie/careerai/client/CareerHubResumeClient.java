package com.zzztie.careerai.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpHeaders;

@Component
public class CareerHubResumeClient {
    private final RestClient restClient;

    public CareerHubResumeClient(
            RestClient.Builder builder,
            @Value("${careerhub.base-url}") String baseUrl) {
        // 1. 创建 JDK HTTP 客户端
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        // 2. 创建 Spring HTTP 请求工厂
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);
        // 3. 设置响应等待超时
        factory.setReadTimeout(Duration.ofSeconds(5));
        // 4. 将请求工厂交给 RestClient
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }



    public byte[] downloadResumePdf(
            Long resumeId,
            String authorization) {

        // 最多尝试两次
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return restClient.get()
                        .uri("/resumes/{id}/download", resumeId)
                        .header("Authorization", authorization)
                        .retrieve()
                        .body(byte[].class);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 401) {
                    throw new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "CareerHub 身份认证失败", e);
                }
                if (status == 403) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "无权访问该简历", e);
                }
                if (status == 404) {
                    throw new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "简历不存在", e);
                }
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "CareerHub 返回异常状态：" + status,
                        e);
            } catch (ResourceAccessException e) {
                // 第二次仍然失败，不再重试
                if (attempt == 2) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "CareerHub 重试后仍无法连接",
                            e);
                }
                // 第一次失败，等待后进行下一次尝试
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();

                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "重试等待被中断",
                            interrupted);
                }
            }
        }
        throw new IllegalStateException("下载流程未正常结束");
    }
}
