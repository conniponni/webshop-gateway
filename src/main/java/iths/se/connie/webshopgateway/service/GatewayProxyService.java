
package iths.se.connie.webshopgateway.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import iths.se.connie.webshopgateway.dto.AuthServiceResponse;
import iths.se.connie.webshopgateway.dto.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
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
    private final boolean cookieSecure;
    private final String cookieSameSite;

    private final String authServerUrl;
    private final String productServiceUrl;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false
            );


    public GatewayProxyService(
            @Value("${app.auth-server-url:http://localhost:8080}")
            String authServerUrl,

            @Value("${app.product-service-url:http://localhost:8081}")
            String productServiceUrl,

            @Value("${app.cookie.secure:false}")
            boolean cookieSecure,

            @Value("${app.cookie.same-site:Lax}")
            String cookieSameSite
    ) {
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.authServerUrl = authServerUrl;
        this.productServiceUrl = productServiceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public ResponseEntity<?> loginWithCookie(
            String requestBody,
            String contentType
    ) {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create(authServerUrl + "/auth/login")
                )
                .header(
                        HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/json")
                .POST(
                        HttpRequest.BodyPublishers.ofString(requestBody)
                );

        return sendLogin(request);
    }

    private ResponseEntity<?> sendLogin(
            HttpRequest.Builder request
    ) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 ||
                    response.statusCode() >= 300) {
                return ResponseEntity
                        .status(response.statusCode())
                        .body(response.body());
            }
            return createLoginResponse(response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ResponseEntity.status(502)
                    .body("Gateway could not reach auth service");
        }
    }

    private ResponseEntity<?> createLoginResponse(String responseBody) {

        try {
            AuthServiceResponse authResponse = objectMapper.readValue(
                    responseBody,
                    AuthServiceResponse.class

            );

            if (authResponse.token() == null
                    || authResponse.token().isBlank()
                    || authResponse.expiresIn() <= 0) {
                return ResponseEntity
                        .status(502)
                        .body("Auth service returned an invalid response");
            }

            ResponseCookie cookie = ResponseCookie
                    .from("access_token", authResponse.token())
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .sameSite(cookieSameSite)
                    .path("/")
                    .maxAge(Duration.ofSeconds(authResponse.expiresIn()))
                    .build();

            LoginResponse loginResponse = new LoginResponse(
                    authResponse.expiresIn(),
                    authResponse.subject(),
                    authResponse.roles()
            );

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(loginResponse);

        } catch (JacksonException ex) {
            return ResponseEntity
                    .status(502)
                    .body("Auth service returned an invalid response");
        }
    }

    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie
                .from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    private ResponseEntity<String> forwardGet(
            String baseUrl,
            String path,
            String authorization) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .GET();
        return send(builder, authorization);
    }

    public ResponseEntity<String> register(String requestBody, String contentType) {
        return forwardPost(authServerUrl, "/auth/register", requestBody, null, contentType);
    }

    public ResponseEntity<String> getMe(String authorization) {
        return forwardGet(authServerUrl, "/auth/me", authorization);
    }

    public ResponseEntity<String> getJwks() {
        return forwardGet(authServerUrl, "/auth/jwks", null);
    }

    public ResponseEntity<String> getAllProducts(String authorization) {
        return forwardGet(productServiceUrl, "/api/products", authorization);
    }

    public ResponseEntity<String> getProductById(Long id, String authorization) {
        return forwardGet(productServiceUrl, "/api/products/" + id, authorization);
    }

    public ResponseEntity<String> addProduct(String requestBody, String authorization, String contentType) {
        return forwardPost(productServiceUrl, "/api/products/add", requestBody, authorization, contentType);
    }

    public ResponseEntity<String> deleteProduct(Long id, String authorization) {
        return forwardDelete(productServiceUrl, "/api/products/delete/" + id, authorization);
    }

    private ResponseEntity<String> forwardPost(
            String baseUrl,
            String path,
            String requestBody,
            String authorization,
            String contentType) {

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        if (contentType != null && !contentType.isBlank()) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }

        return send(builder, authorization);
    }

    private ResponseEntity<String> forwardDelete(
            String baseUrl,
            String path,
            String authorization)
    {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .DELETE();
        return send(builder, authorization);
    }

    private ResponseEntity<String> send(HttpRequest.Builder builder, String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            builder.header(
                    HttpHeaders.AUTHORIZATION,
                    authorization
            );
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(response.statusCode());

            response.headers()
                    .firstValue(HttpHeaders.CONTENT_TYPE)
                    .ifPresent(contentType -> responseBuilder.header(
                            HttpHeaders.CONTENT_TYPE,
                            contentType
                    ));
            return responseBuilder.body(response.body());
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Kunde inte nå bakomliggande tjänst");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Anropet avbröts");
        }
    }

}
