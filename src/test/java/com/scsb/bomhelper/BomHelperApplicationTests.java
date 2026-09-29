package com.scsb.bomhelper;

import com.scsb.bomhelper.dto.gitlab.*;
import com.scsb.bomhelper.entity.*;
import com.scsb.bomhelper.repository.*;
import com.scsb.bomhelper.security.*;
import com.scsb.bomhelper.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bomtests;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "jasypt.encryptor.password=BomHelper",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
        "gitlab.base-url=https://gitlab.invalid", "gitlab.trust-self-signed=false"
})
@ActiveProfiles("test")
class BomHelperApplicationTests {
    @Autowired BomImportService imports;
    @Autowired BomReportRepository reports;
    @Autowired BomUserRepository users;
    @Autowired GitLabUserSyncService sync;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @MockitoBean GitLabService gitlab;
    MockMvc mvc;
    static final String PASSWORD = "a-test-password-long-enough";
    static final String HASH = LocalPasswords.encoder().encode(PASSWORD);

    @BeforeEach void setup() {
        reports.deleteAll();
        users.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(gitlab.fetchProjectMembersAsAdmin(anyString(), anyString())).thenReturn(List.of());
    }

    static String xml(String scan, String project, String component) {
        return """
                <bom xmlns="http://cyclonedx.org/schema/bom/1.4" serialNumber="urn:uuid:test">
                  <metadata><timestamp>2026-09-29T00:00:00Z</timestamp>
                    <component type="application"><name>iq_application_%s</name></component>
                    <properties><property name="Scan ID">%s</property></properties>
                  </metadata>
                  <components><component type="library" bom-ref="ref1"><name>%s</name><version>1.0</version></component></components>
                  <dependencies><dependency ref="root"><dependency ref="ref1"/></dependency></dependencies>
                  <vulnerabilities><vulnerability><id>CVE-TEST</id><affects><target><ref>ref1</ref></target></affects></vulnerability></vulnerabilities>
                </bom>
                """.formatted(project, scan, component);
    }
    static MockMultipartFile file(String xml) {
        return new MockMultipartFile("file", "bom.xml", "application/xml", xml.getBytes(StandardCharsets.UTF_8));
    }
    void upload(String scan, String group, String project) throws Exception {
        imports.importSbom(file(xml(scan, project, "test-lib")), group, "tester");
    }
    BomUser local(String id, String status) {
        var user = new BomUser();
        user.setUserId(id); user.setUserPassValidWord(HASH);
        user.setStatus(status); user.setAuthorityCode("0170");
        user.setCreatedBy("operator"); user.setUpdatedBy("operator");
        user.setCreatedDate(LocalDateTime.now()); user.setUpdatedDate(LocalDateTime.now());
        return users.saveAndFlush(user);
    }
    GitLabUser gitlabIdentity(String username) {
        var user = new GitLabUser(); user.setUsername(username); user.setState("active"); return user;
    }
    GitLabUserPrincipal developer(String group) {
        var g = new GitLabGroup(); g.setPath(group);
        return new GitLabUserPrincipal(gitlabIdentity("developer"), "test-token", List.of(g), List.of());
    }

    @Test void differentProjectsAndGroupsArePreserved() throws Exception {
        upload("a", "ncbs_mid", "ncbs-mid-repo-p2");
        upload("b", "ncbs_mid", "ncbs-mid-foundation-p2");
        upload("c", "another", "ncbs-mid-repo-p2");
        assertThat(reports.count()).isEqualTo(3);
    }
    @Test void sameProjectReplacesAllOldDetailsAndAcceptsSameScanId() throws Exception {
        upload("a", "ncbs_mid", "project");
        Integer oldId = reports.findAll().get(0).getId();
        upload("b", "ncbs_mid", "project");
        assertThat(reports.count()).isEqualTo(1);
        assertThat(reports.existsById(oldId)).isFalse();
        for (String table : List.of("BomComponent", "BomDependency", "BomVulnerability")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE ScanId = 'a'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isEqualTo(1);
        }
        upload("b", "ncbs_mid", "project");
        assertThat(reports.count()).isEqualTo(1);
    }
    @Test void databaseFailureAfterDeletionRollsBackOldReportAndChildren() throws Exception {
        upload("old", "group", "project");
        assertThatThrownBy(() -> imports.importSbom(file(xml("new", "project", "x".repeat(256))), "group", "tester"))
                .isInstanceOf(Exception.class);
        assertThat(reports.findByScanId("old")).isPresent();
        assertThat(reports.findByScanId("new")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BomComponent WHERE ScanId = 'old'", Integer.class)).isEqualTo(1);
    }
    @Test void scanOwnedByAnotherProjectCannotDeleteEitherReport() throws Exception {
        upload("a", "group", "first"); upload("b", "group", "second");
        assertThatThrownBy(() -> upload("a", "group", "second")).isInstanceOf(IllegalArgumentException.class);
        assertThat(reports.count()).isEqualTo(2);
        assertThat(reports.findByScanId("b")).isPresent();
    }
    @Test void malformedMissingIdentifiersAndExternalEntitiesAreRejected() throws Exception {
        upload("old", "group", "project");
        for (String bad : List.of("<bom>", "<bom/>", xml("", "project", "lib"),
                "<!DOCTYPE bom [<!ENTITY xxe SYSTEM 'file:///does-not-exist'>]>" + xml("new", "project", "&xxe;"))) {
            assertThatThrownBy(() -> imports.importSbom(file(bad), "group", "tester")).isInstanceOf(Exception.class);
        }
        assertThatThrownBy(() -> upload("new", " ", "project")).isInstanceOf(IllegalArgumentException.class);
        assertThat(reports.findByScanId("old")).isPresent();
    }
    @Test void ciProjectOverrideUsesTheSameReplacementRule() throws Exception {
        imports.importSbom(file(xml("a", "xml-name", "lib")), "group", "override", "jenkins");
        imports.importSbom(file(xml("b", "other-xml-name", "lib")), "group", "override", "jenkins");
        assertThat(reports.count()).isEqualTo(1);
        assertThat(reports.findAll().get(0).getGitlabProjectId()).isEqualTo("override");
    }
    @Test void downloadsFullStoredXmlWithAuthorizationAndAttachmentHeaders() throws Exception {
        upload("a", "group", "project");
        var report = reports.findAll().get(0);
        String url = "/api/v1/bom/reports/" + report.getId() + "/download";
        mvc.perform(get(url).with(user(new LocalUserPrincipal("security", "0170"))))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", "attachment; filename=\"bom-report-" + report.getId() + ".xml\""))
                .andExpect(content().bytes(report.getRawXmlContent().getBytes(StandardCharsets.UTF_8)));
        mvc.perform(get(url).with(user(developer("group")))).andExpect(status().isOk());
        mvc.perform(get(url).with(user(developer("other")))).andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/bom/reports/999999/download").with(user(developer("group")))).andExpect(status().isNotFound());
    }
    @Test void localSecurityCanSearchAllButCannotUpload() throws Exception {
        upload("a", "one", "project-one"); upload("b", "two", "project-two");
        var local = new LocalUserPrincipal("security", "0170");
        mvc.perform(get("/api/v1/bom/search").param("type", "application").param("keyword", "project").with(user(local)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].reportId").isNumber());
        mvc.perform(get("/api/v1/bom/search").param("type", "application").param("keyword", "project").with(user(developer("one"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(multipart("/api/v1/bom/upload").file(file(xml("c", "project", "lib"))).param("gitlabGroupId", "one").with(user(local)).with(csrf()))
                .andExpect(status().isForbidden());
    }
    @Test void localLoginNeverCallsGitlabAndRejectsDisabledWrongOrUnknownAccounts() throws Exception {
        local("security", "A"); local("disabled", "D");
        mvc.perform(post("/login").param("loginSource", "local").param("username", "security").param("password", PASSWORD).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        for (String id : List.of("disabled", "unknown")) {
            mvc.perform(post("/login").param("loginSource", "local").param("username", id).param("password", PASSWORD).with(csrf()))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(post("/login").param("loginSource", "local").param("username", "security").param("password", "wrong").with(csrf()))
                .andExpect(redirectedUrl("/login?error"));
        verifyNoInteractions(gitlab);
    }
    @Test void csrfIsRequiredForLoginAndUpload() throws Exception {
        mvc.perform(post("/login").param("loginSource", "local").param("username", "security").param("password", PASSWORD))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/bom/upload").file(file(xml("a", "project", "lib"))).param("gitlabGroupId", "group").with(user(developer("group"))))
                .andExpect(status().isForbidden());
    }
    @Test void gitlabLoginCreates0113AndLeavesExistingAccountUnchanged() throws Exception {
        var token = new OAuthTokenResponse(); token.setAccessToken("token");
        when(gitlab.loginWithPassword("developer", PASSWORD)).thenReturn(token);
        when(gitlab.fetchCurrentUser("token")).thenReturn(gitlabIdentity("developer"));
        when(gitlab.fetchUserGroups("token")).thenReturn(List.of());
        when(gitlab.fetchUserProjects("token")).thenReturn(List.of());
        mvc.perform(post("/login").param("loginSource", "gitlab").param("username", "developer").param("password", PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/"));
        var user = users.findById("developer").orElseThrow();
        assertThat(user.getAuthorityCode()).isEqualTo("0113");
        assertThat(user.getUserPassValidWord()).isNull();
        var created = user.getCreatedDate();
        var updated = user.getUpdatedDate();
        sync.synchronize(gitlabIdentity("developer"));
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findById("developer").orElseThrow().getCreatedDate()).isEqualTo(created);
        assertThat(users.findById("developer").orElseThrow().getUpdatedDate()).isEqualTo(updated);
        mvc.perform(post("/login").param("loginSource", "local").param("username", "developer").param("password", PASSWORD).with(csrf()))
                .andExpect(redirectedUrl("/login?error"));
    }
    @Test void gitlabCannotOverwriteSecurityOfficeAccount() {
        local("same-id", "A");
        var updated = users.findById("same-id").orElseThrow().getUpdatedDate();
        sync.synchronize(gitlabIdentity("SAME-ID"));
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findById("same-id").orElseThrow().getAuthorityCode()).isEqualTo("0170");
        assertThat(users.findById("same-id").orElseThrow().getUserPassValidWord()).isEqualTo(HASH);
        assertThat(users.findById("same-id").orElseThrow().getUpdatedDate()).isEqualTo(updated);
    }
    @Test void xmlEncodingAndUnmappedContentArePreservedForDownload() throws Exception {
        String payload = xml("unicode", "project", "中文套件").replace("</bom>", "<custom>完整報告</custom></bom>");
        byte[] bytes = ("<?xml version=\"1.0\" encoding=\"UTF-16\"?>" + payload).getBytes(StandardCharsets.UTF_16);
        imports.importSbom(new MockMultipartFile("file", "unicode.xml", "application/xml", bytes), "group", "tester");
        assertThat(reports.findByScanId("unicode").orElseThrow().getRawXmlContent()).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + payload);
    }
    @Test void downloadPreservesUploadedLineEndingsAndIndentation() throws Exception {
        for (String newline : List.of("\n", "\r\n", "\r")) {
            String payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + newline
                    + xml("lines", "project", "lib").replace("\n", newline);
            imports.importSbom(file(payload), "group", "tester");
            var report = reports.findByScanId("lines").orElseThrow();
            mvc.perform(get("/api/v1/bom/reports/" + report.getId() + "/download")
                    .with(user(new LocalUserPrincipal("security", "0170"))))
                    .andExpect(content().bytes(payload.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test void adminUpdatesStatusWithoutChangingOtherAccountFields() throws Exception {
        var actor = admin();
        var account = local("target", "A");
        var created = users.findById("target").orElseThrow().getCreatedDate();
        for (String newStatus : List.of("D", "A")) {
            mvc.perform(post("/users/status").with(user(actor)).with(csrf())
                    .param("userId", "TARGET").param("status", newStatus)).andExpect(redirectedUrl("/users"));
            account = users.findById("target").orElseThrow();
            assertThat(account.getStatus()).isEqualTo(newStatus);
            assertThat(account.getUpdatedBy()).isEqualTo("admin");
            assertThat(account.getCreatedDate()).isEqualTo(created);
            assertThat(account.getUserPassValidWord()).isEqualTo(HASH);
            assertThat(account.getAuthorityCode()).isEqualTo("0170");
            if (newStatus.equals("D")) {
                assertThatThrownBy(() -> sync.synchronize(gitlabIdentity("TARGET")))
                        .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
            }
        }
        mvc.perform(post("/users/status").with(user(actor)).with(csrf())
                .param("userId", "target").param("status", "X"))
                .andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(post("/users/status").with(user(actor)).with(csrf())
                .param("userId", "missing").param("status", "D"))
                .andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(post("/users/status").with(user(actor))
                .param("userId", "target").param("status", "D")).andExpect(status().isForbidden());
        mvc.perform(post("/users/status").with(user(new LocalUserPrincipal("target", "0170"))).with(csrf())
                .param("userId", "target").param("status", "D")).andExpect(status().isForbidden());
    }

    @Test void searchUsesEachProjectsManagersRatherThanGroupManagers() throws Exception {
        upload("one", "group", "first"); upload("two", "group", "second");
        var owner = new GitLabProjectMember(); owner.setUsername("owner"); owner.setName("Owner name"); owner.setAccessLevel(50);
        var maintainer = new GitLabProjectMember(); maintainer.setUsername("maintainer"); maintainer.setAccessLevel(40);
        when(gitlab.fetchProjectMembersAsAdmin("group", "first")).thenReturn(List.of(owner));
        when(gitlab.fetchProjectMembersAsAdmin("group", "second")).thenReturn(List.of(maintainer));
        for (String type : List.of("component", "application")) {
            mvc.perform(get("/api/v1/bom/search").param("type", type).param("keyword", type.equals("component") ? "test-lib" : "first")
                    .with(user(new LocalUserPrincipal("security", "0170"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.gitlabProjectId == 'first')].supervisors[0]").value(org.hamcrest.Matchers.hasItem("Owner：Owner name (@owner)")));
        }
        verify(gitlab, never()).fetchGroupMembersAsAdmin(anyString());
        verify(gitlab).fetchProjectMembersAsAdmin("group", "second");
    }

    @Test void sqlUniqueKeyPreventsDuplicateProjectRows() throws Exception {
        upload("a", "group", "project");
        var report = new BomReport();
        report.setGitlabGroupId("group"); report.setGitlabProjectId("project");
        report.setScanId("b"); report.setImportedBy("tester");
        assertThatThrownBy(() -> reports.saveAndFlush(report))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(reports.count()).isEqualTo(1);
    }
    @Test void projectOnlyGitlabMembershipCanDownload() throws Exception {
        upload("a", "group", "project");
        var project = new GitLabProject(); project.setPath("project");
        var principal = new GitLabUserPrincipal(gitlabIdentity("developer"), "token", List.of(), List.of(project));
        mvc.perform(get("/api/v1/bom/reports/" + reports.findAll().get(0).getId() + "/download").with(user(principal)))
                .andExpect(status().isOk());
    }
    @Test void templatesRenderWithCsrfAndUpdatedLabels() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("loginSource")));
        mvc.perform(get("/search").with(user(new LocalUserPrincipal("security", "0170"))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("立即查詢"))));
        mvc.perform(get("/upload").with(user(developer("group")))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("csrf-token")));
    }
    @Test void hashesUseRandomSaltAndMatchOnlyCorrectPassword() {
        var encoder = LocalPasswords.encoder();
        String second = encoder.encode(PASSWORD);
        assertThat(second).isNotEqualTo(HASH).hasSize(118);
        assertThat(encoder.matches(PASSWORD, second)).isTrue();
        assertThat(encoder.matches("wrong", second)).isFalse();
    }
    LocalUserPrincipal admin() {
        var account = local("admin", "A");
        account.setAuthorityCode("9999"); users.saveAndFlush(account);
        return new LocalUserPrincipal("admin", "9999");
    }
    @Test void onlyLocal9999CanManageUsersAndSeeNavigation() throws Exception {
        var admin = admin();
        mvc.perform(get("/users").with(user(admin))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("建立使用者")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(HASH))));
        mvc.perform(get("/").with(user(admin))).andExpect(content().string(org.hamcrest.Matchers.containsString("使用者管理")));
        var gitAdminIdentity = gitlabIdentity("git-admin"); gitAdminIdentity.setIsAdmin(true);
        var gitAdmin = new GitLabUserPrincipal(gitAdminIdentity, "token", List.of(), List.of());
        for (var principal : List.of(new LocalUserPrincipal("security", "0170"), developer("group"), gitAdmin)) {
            mvc.perform(get("/users").with(user(principal))).andExpect(status().isForbidden());
            mvc.perform(post("/users").with(user(principal)).with(csrf())
                    .param("userId", "forbidden").param("password", "12345678").param("confirmation", "12345678")
                    .param("authorityCode", "9999").param("status", "A")).andExpect(status().isForbidden());
            mvc.perform(get("/").with(user(principal)))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("使用者管理"))));
        }
        assertThat(users.existsById("forbidden")).isFalse();
        mvc.perform(get("/users")).andExpect(status().is3xxRedirection());
    }
    @Test void adminCreatesSaltedLocalUsersWithServerControlledAuditFields() throws Exception {
        var actor = admin();
        for (String role : List.of("0170", "9999")) {
            mvc.perform(post("/users").with(user(actor)).with(csrf())
                    .param("userId", "new-" + role).param("password", "12345678").param("confirmation", "12345678")
                    .param("authorityCode", role).param("status", "A").param("createdBy", "spoofed"))
                    .andExpect(redirectedUrl("/users"));
            var account = users.findById("new-" + role).orElseThrow();
            assertThat(LocalPasswords.encoder().matches("12345678", account.getUserPassValidWord())).isTrue();
            assertThat(account.getCreatedBy()).isEqualTo("admin");
            assertThat(account.getUpdatedBy()).isEqualTo("admin");
            assertThat(account.getCreatedDate()).isNotNull();
            mvc.perform(post("/login").with(csrf()).param("loginSource", "local")
                    .param("username", account.getUserId()).param("password", "12345678")).andExpect(redirectedUrl("/"));
        }
    }
    @Test void userCreationRejectsGitlabRoleDuplicatesInvalidDataAndMissingCsrf() throws Exception {
        var actor = admin();
        mvc.perform(post("/users").with(user(actor)).param("userId", "test")).andExpect(status().isForbidden());
        for (String[] params : List.of(
                new String[]{"admin", "12345678", "12345678", "9999", "A"},
                new String[]{"ADMIN", "12345678", "12345678", "9999", "A"},
                new String[]{"developer", "12345678", "12345678", "0113", "A"},
                new String[]{"invalid", "1234567", "1234567", "0170", "A"},
                new String[]{"invalid", "12345678", "different", "0170", "A"},
                new String[]{"invalid", "12345678", "12345678", "0170", "X"},
                new String[]{"<script>", "12345678", "12345678", "0170", "A"})) {
            mvc.perform(post("/users").with(user(actor)).with(csrf())
                    .param("userId", params[0]).param("password", params[1]).param("confirmation", params[2])
                    .param("authorityCode", params[3]).param("status", params[4])).andExpect(status().isBadRequest());
        }
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findById("admin").orElseThrow().getUserPassValidWord()).isEqualTo(HASH);
    }
    @Test void revokedAdminSessionCannotManageAccounts() throws Exception {
        var actor = admin();
        var account = users.findById("admin").orElseThrow(); account.setStatus("D"); users.saveAndFlush(account);
        mvc.perform(get("/users").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/users").with(user(actor)).with(csrf()).param("userId", "new-user")
                .param("password", "12345678").param("confirmation", "12345678")
                .param("authorityCode", "0170").param("status", "A")).andExpect(status().isForbidden());
        assertThat(users.existsById("new-user")).isFalse();
    }
    @Test void invalidPersistedAccountAttributesCannotAuthorizeLocalLogin() throws Exception {
        for (String[] attributes : List.of(new String[]{"8888", "A"}, new String[]{"9999", "X"})) {
            var account = local("invalid-policy", attributes[1]);
            account.setAuthorityCode(attributes[0]);
            users.saveAndFlush(account);
            mvc.perform(post("/login").with(csrf()).param("loginSource", "local")
                    .param("username", account.getUserId()).param("password", PASSWORD))
                    .andExpect(redirectedUrl("/login?error"));
        }
    }

    @Test void bootstrapJasyptSqlAuthenticatesWithRequestedPassword() throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("sql/004_bootstrap_admin.sql"));
        var matcher = java.util.regex.Pattern.compile("ENC\\([A-Za-z0-9+/=]+\\)").matcher(sql);
        assertThat(matcher.find()).isTrue();
        String hash = matcher.group();
        assertThat(com.scsb.bomhelper.util.JasyptCli.buildEncryptor("BomHelper").decrypt(hash.substring(4, hash.length() - 1))).isEqualTo("12345678");
        var account = local("admin", "A"); account.setAuthorityCode("9999"); account.setUserPassValidWord(hash); users.saveAndFlush(account);
        for (String key : List.of("", "incorrect-key")) {
            var provider = new LocalAuthenticationProvider(users, key);
            assertThatThrownBy(() -> provider.authenticate(
                    org.springframework.security.authentication.UsernamePasswordAuthenticationToken.unauthenticated("admin", "12345678")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        }
        mvc.perform(post("/login").with(csrf()).param("loginSource", "local")
                .param("username", "admin").param("password", "wrong"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).param("loginSource", "local")
                .param("username", "admin").param("password", "12345678")).andExpect(redirectedUrl("/"));
    }
    @Test void templatesAndScriptsContainNoInlineStyles() throws Exception {
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/resources/templates"))) {
            for (var path : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = java.nio.file.Files.readString(path);
                assertThat(html).doesNotContain("style=", "<style", ".style.");
            }
        }
    }

}
