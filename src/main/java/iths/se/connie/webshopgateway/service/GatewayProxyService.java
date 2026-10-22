
package iths.se.connie.webshopgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class GatewayProxyService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.auth-server-url:http://localhost:8080}")
    private String authServerUrl;

    public GatewayProxyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public record ProxyResult(
            int status,
            String body,
            String token,
            long expiresIn
    ) {}

    public ProxyResult login(String requestBody) {
        ProxyResult response = send(
                "/auth/login", "POST", requestBody, null
        );

        if (response.status() < 200 || response.status() >= 300) {
            return response;
        }

        try {
            JsonNode json = objectMapper.readTree(response.body());

            String token = json.path("token").asText(null);
            long expiresIn = json.path("expiresIn").asLong(0);

            if (token == null || token.isBlank() || expiresIn <= 0) {
                return new ProxyResult(
                        502,
                        "{\"error\":\"Invalid token response from auth service\"}",
                        null,
                        0
                );
            }

            // Behåll subject, roles och expiresIn i svaret,
            // men skicka inte JWT-token tillbaka till frontend.
            if (json instanceof ObjectNode object) {
                object.remove("token");

                return new ProxyResult(
                        response.status(),
                        objectMapper.writeValueAsString(object),
                        token,
                        expiresIn
                );
            }

            return new ProxyResult(
                    502,
                    "{\"error\":\"Invalid token response\"}",
                    null,
                    0
            );

        } catch (IOException e) {
            return new ProxyResult(
                    502,
                    "{\"error\":\"Could not read auth response\"}",
                    null,
                    0
            );
        }
    }

    public ProxyResult register(String requestBody) {
        return send("/auth/register", "POST", requestBody, null);
    }

    public ProxyResult getCurrentUser(String token) {
        return send("/auth/me", "GET", null, token);
    }

    public ProxyResult getJwks() {
        return send("/auth/jwks", "GET", null, null);
    }

    private ProxyResult send(
            String path,
            String method,
            String body,
            String token
    ) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(authServerUrl + path))
                    .timeout(Duration.ofSeconds(15));

            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }

            if ("POST".equalsIgnoreCase(method)) {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                body == null ? "" : body
                        ));
            } else {
                builder.GET();
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            return new ProxyResult(
                    response.statusCode(),
                    response.body(),
                    null,
                    0
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return new ProxyResult(
                    503,
                    "{\"error\":\"Request interrupted\"}",
                    null,
                    0
            );

        } catch (IOException | IllegalArgumentException e) {
            return new ProxyResult(
                    502,
                    "{\"error\":\"Auth service unavailable\"}",
                    null,
                    0
            );
        }
    }
}
