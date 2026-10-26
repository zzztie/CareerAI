package com.zzztie.careerai.client;

import com.zzztie.careerai.dto.JobDTO;
import com.zzztie.careerai.dto.JobSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CareerHubJobClientTest {

    @Test
    void shouldGetJobFromCareerHub() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost:8080/jobs/7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":7,"title":"后端开发实习生",
                         "company":"拓竹科技","description":"Java 后端岗位",
                         "status":"OPEN"}
                        """, MediaType.APPLICATION_JSON));

        CareerHubJobClient client = new CareerHubJobClient(
                builder, "http://localhost:8080"
        );
        JobDTO job = client.getJobById(7L);

        assertNotNull(job);
        assertEquals(7L, job.id());
        assertEquals("后端开发实习生", job.title());
        server.verify();
    }

    @Test
    void shouldSearchOpenJobsFromCareerHub() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "http://localhost:8080/jobs/search?keyword=Java&status=OPEN&page=1&size=5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"records":[
                          {"id":7,"title":"后端开发实习生",
                           "company":"拓竹科技","status":"OPEN"}
                        ],"total":1,"pages":1}
                        """, MediaType.APPLICATION_JSON));

        CareerHubJobClient client = new CareerHubJobClient(
                builder, "http://localhost:8080"
        );

        JobSearchPage page = client.searchOpenJobs("Java");

        assertNotNull(page);
        assertEquals(1, page.records().size());
        assertEquals(7L, page.records().get(0).id());
        server.verify();
    }
}
