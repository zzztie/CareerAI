
package com.zzztie.careerai.client;

import com.zzztie.careerai.dto.JobDTO;
import com.zzztie.careerai.dto.JobSearchPage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CareerHubJobClient {

    private final RestClient restClient;

    public CareerHubJobClient(
            RestClient.Builder builder,
            @Value("${careerhub.base-url}") String baseUrl) {

        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public JobDTO getJobById(Long jobId) {

        return restClient.get()
                .uri("/jobs/{id}", jobId)
                .retrieve()
                .body(JobDTO.class);
    }

    public JobSearchPage searchOpenJobs(String keyword) {
        return restClient.get()
                .uri(uri -> uri.path("/jobs/search")
                        .queryParam("keyword", keyword)
                        .queryParam("status", "OPEN")
                        .queryParam("page", 1)
                        .queryParam("size", 5)
                        .build())
                .retrieve()
                .body(JobSearchPage.class);
    }
}
