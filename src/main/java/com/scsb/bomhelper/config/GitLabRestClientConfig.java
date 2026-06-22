package com.scsb.bomhelper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * 提供呼叫 GitLab API 用的 RestClient。
 * 若公司 GitLab 使用自簽憑證，可透過 gitlab.trust-self-signed=true 略過憑證驗證。
 */
@Configuration
public class GitLabRestClientConfig {

    private final GitLabProperties properties;

    public GitLabRestClientConfig(GitLabProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "gitLabRestClient")
    public RestClient gitLabRestClient() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new IllegalStateException(
                    "未設定 gitlab.base-url，請於 application.properties 中設定 GitLab Base URL");
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        if (properties.isTrustSelfSigned()) {
            installTrustAllSslContext();
        }

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * ⚠️ 僅供「公司內部自簽憑證」環境使用。
     * 將 JVM 全域 default SSLContext 改成信任所有憑證；正式環境請改回 false。
     */
    private void installTrustAllSslContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            SSLContext.setDefault(sslContext);
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new IllegalStateException("無法啟用 trust-self-signed SSL Context", e);
        }
    }

}
