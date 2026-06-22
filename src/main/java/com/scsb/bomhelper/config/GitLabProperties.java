package com.scsb.bomhelper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GitLab connection settings, bound from "gitlab.*" entries in application.properties.
 */
@Component
@ConfigurationProperties(prefix = "gitlab")
public class GitLabProperties {

    /** Base URL of GitLab, e.g., https://gitlab.example.com */
    private String baseUrl;

    /** Connect timeout in milliseconds. */
    private int connectTimeoutMs = 5000;

    /** Read timeout in milliseconds. */
    private int readTimeoutMs = 10000;

    /** Page size when syncing user's Groups / Projects. */
    private int pageSize = 100;

    /** Trust self-signed TLS certificates (for internal HTTPS deployments only). */
    private boolean trustSelfSigned = false;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public boolean isTrustSelfSigned() { return trustSelfSigned; }
    public void setTrustSelfSigned(boolean trustSelfSigned) { this.trustSelfSigned = trustSelfSigned; }
}
