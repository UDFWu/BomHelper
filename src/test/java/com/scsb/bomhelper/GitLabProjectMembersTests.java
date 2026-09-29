package com.scsb.bomhelper;

import com.scsb.bomhelper.config.GitLabProperties;
import com.scsb.bomhelper.service.GitLabService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitLabProjectMembersTests {
    @Test void projectMembersEncodeNamespaceOnceAndReadEveryPage() {
        var builder = RestClient.builder().baseUrl("https://gitlab.invalid");
        var server = MockRestServiceServer.bindTo(builder).build();
        var props = new GitLabProperties();
        props.setAdminUsername("api-admin"); props.setAdminPassword("test-secret"); props.setPageSize(1);
        var service = new GitLabService(builder.build(), props);
        server.expect(requestTo("https://gitlab.invalid/oauth/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        for (int page = 1; page <= 3; page++) {
            server.expect(requestTo("https://gitlab.invalid/api/v4/projects/group%2Fsubgroup%2Fproject/members/all?per_page=1&page=" + page))
                    .andExpect(header("Authorization", "Bearer token"))
                    .andRespond(withSuccess(page == 3 ? "[]" : "[{\"id\":" + page + ",\"username\":\"user" + page + "\",\"access_level\":40}]", MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo("https://gitlab.invalid/api/v4/groups/123"))
                .andRespond(withSuccess("{\"full_path\":\"group/subgroup\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gitlab.invalid/api/v4/projects/group%2Fsubgroup%2Fproject/members/all?per_page=1&page=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(service.fetchProjectMembersAsAdmin("group/subgroup", "project")).hasSize(2);
        assertThat(service.fetchProjectMembersAsAdmin("123", "project")).isEmpty();
        server.verify();
    }
}
