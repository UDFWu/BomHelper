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
    @Test void adminPatLoadsAllGroupsAndProjectsWithInheritedManagersAndKeepsSnapshotOnFailure() {
        var builder = RestClient.builder().baseUrl("https://gitlab.invalid");
        var server = MockRestServiceServer.bindTo(builder).build();
        var props = new GitLabProperties();
        props.setAdminToken("admin-pat"); props.setPageSize(1);
        var service = new GitLabService(builder.build(), props);
        server.expect(requestTo("https://gitlab.invalid/api/v4/groups?all_available=true&per_page=1&page=1"))
                .andExpect(header("Authorization", "Bearer admin-pat"))
                .andRespond(withSuccess("[{\"id\":7,\"full_path\":\"parent/group\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gitlab.invalid/api/v4/groups?all_available=true&per_page=1&page=2"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        for (int page = 1; page <= 3; page++) {
            server.expect(requestTo("https://gitlab.invalid/api/v4/groups/7/members/all?per_page=1&page=" + page))
                    .andRespond(withSuccess(page == 3 ? "[]" : "[{\"username\":\"group-user\",\"access_level\":" + (page == 1 ? 50 : 30) + "}]", MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo("https://gitlab.invalid/api/v4/projects?simple=true&per_page=1&page=1"))
                .andRespond(withSuccess("[{\"id\":9,\"name\":\"Project display name\",\"path_with_namespace\":\"parent/group/project\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gitlab.invalid/api/v4/projects?simple=true&per_page=1&page=2"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        for (int page = 1; page <= 3; page++) {
            server.expect(requestTo("https://gitlab.invalid/api/v4/projects/9/members/all?per_page=1&page=" + page))
                    .andRespond(withSuccess(page == 3 ? "[]" : "[{\"username\":\"project-user\",\"access_level\":" + (page == 1 ? 50 : 40) + "}]", MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo("https://gitlab.invalid/api/v4/groups?all_available=true&per_page=1&page=1"))
                .andRespond(withUnauthorizedRequest());
        service.refreshSupervisorDirectory();
        assertThat(service.fetchGroupMembersAsAdmin("parent/group")).hasSize(1);
        assertThat(service.fetchGroupMembersAsAdmin("7")).hasSize(1);
        assertThat(service.fetchProjectMembersAsAdmin("7", "project")).hasSize(2);
        assertThat(service.fetchProjectMembersAsAdmin("parent/group", "project")).hasSize(2);
        assertThat(service.fetchProjectMembersAsAdmin("", "9")).hasSize(2);
        assertThat(service.fetchProjectNameAsAdmin("7", "project")).isEqualTo("Project display name");
        assertThat(service.fetchProjectNameAsAdmin("", "9")).isEqualTo("Project display name");
        service.refreshSupervisorDirectory();
        assertThat(service.fetchProjectMembersAsAdmin("", "9")).hasSize(2);
        server.verify();
    }

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
