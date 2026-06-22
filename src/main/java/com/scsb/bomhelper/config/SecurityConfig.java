package com.scsb.bomhelper.config;

import com.scsb.bomhelper.security.GitLabAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 設定：
 * - /login、/css、/js、/images 開放匿名
 * - 其他所有頁面與 /api/** 一律需要登入
 * - 表單登入提交位址 /login，成功導回 /，失敗導回 /login?error
 * - 登出位址 /logout，登出後導回 /login?logout
 * - 認證委派給 GitLabAuthenticationProvider 處理
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final GitLabAuthenticationProvider gitLabAuthenticationProvider;

    public SecurityConfig(GitLabAuthenticationProvider gitLabAuthenticationProvider) {
        this.gitLabAuthenticationProvider = gitLabAuthenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(gitLabAuthenticationProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authenticationManager(authenticationManager())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**",
                                "/images/**", "/favicon.ico", "/error")
                        .permitAll()
                        // ⚠️ /api/ci/** 為機器對機器整合端點（Jenkins pipeline 直接上傳）
                        // 目前不做身份驗證；建議部署時於反向代理 (nginx) 或防火牆做 IP 白名單。
                        .requestMatchers("/api/ci/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(1)
                )
                // 暫時關閉 CSRF 以便 /api 仍可正常被前端呼叫；
                // 若要啟用，請於前端加上 X-CSRF-TOKEN header。
                .csrf(csrf -> csrf.disable())
                // 預設 X-Frame-Options: DENY 會擋掉同站 iframe（index.html 嵌入 /search、/upload），
                // 改為 SAMEORIGIN：允許自家頁面互嵌，仍可防止外站 clickjacking。
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                );

        return http.build();
    }
}
