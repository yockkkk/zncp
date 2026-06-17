package com.example.foodrecommend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class HttpClientConfig {

    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 10808;

    private boolean proxyAvailable = false;

    @PostConstruct
    public void detectProxy() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(PROXY_HOST, PROXY_PORT), 2000);
            proxyAvailable = true;
            log.info("代理检测: {}:{} 可用，将启用代理", PROXY_HOST, PROXY_PORT);
        } catch (Exception e) {
            proxyAvailable = false;
            log.info("代理检测: {}:{} 不可用，将直连", PROXY_HOST, PROXY_PORT);
        }
    }

    @Bean
    public OkHttpClient okHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true);

        if (proxyAvailable) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            builder.proxy(proxy);
            log.info("OkHttpClient 已配置代理");
        }

        return builder.build();
    }
}
