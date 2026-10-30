package iths.se.connie.webshopgateway.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {
    //bean för httpClient som konfigurerar anslutningstiden till 5 sekunder och returnerar en instans av HttpClient
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
}
