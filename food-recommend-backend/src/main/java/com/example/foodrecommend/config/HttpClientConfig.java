package com.example.foodrecommend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class HttpClientConfig {

    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 10808;

    @PostConstruct
    public void initGlobalProxySelector() {
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                    return Collections.singletonList(Proxy.NO_PROXY);
                }

                // 本地/内网请求直连，不走代理
                String host = uri.getHost();
                if (host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.startsWith("172.") || host.startsWith("192."))) {
                    return Collections.singletonList(Proxy.NO_PROXY);
                }

                boolean proxyAvailable = false;
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(PROXY_HOST, PROXY_PORT), 150);
                    proxyAvailable = true;
                } catch (Exception e) {
                    // 代理不可用，将直连
                }

                if (proxyAvailable) {
                    return Collections.singletonList(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT)));
                } else {
                    return Collections.singletonList(Proxy.NO_PROXY);
                }
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                // 忽略或记录
            }
        });
        log.info("全局自适应代理选择器初始化成功");
    }

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build();
    }
}
