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
    private final String orderServiceUrl;
    private final String authServerUrl;
    private final String productServiceUrl;

    // ObjektMapper används för att konvertera JSON-strängar till Java-objekt och vice versa
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false
            );


    // Konstruktor för GatewayProxyService som tar emot värden från application.properties
    public GatewayProxyService(
            @Value("${app.auth-server-url:http://localhost:8080}")
            String authServerUrl,

            @Value("${app.product-service-url:http://localhost:8081}")
            String productServiceUrl,

            @Value("${app.order-service-url}") String orderServiceUrl,
            @Value("${app.cookie.secure:false}")
            boolean cookieSecure,

            @Value("${app.cookie.same-site:Lax}")
            String cookieSameSite
    ) {
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.authServerUrl = authServerUrl;
        this.productServiceUrl = productServiceUrl;
        this.orderServiceUrl = orderServiceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
    // Metod för att skapa en order via gateway

    public ResponseEntity<String> createOrder(
            String requestBody,
            String authorization,
            String contentType) {
        // Anropar forwardPost-metoden för att skicka en POST-förfrågan till ordertjänsten med hjälp av requestBody, authorization och contentType
        return forwardPost(
                orderServiceUrl,
                "/order",
                requestBody,
                authorization,
                contentType
        );

    }

    // Metod för att logga in via gateway och sätta en cookie med access_token
    public ResponseEntity<?> loginWithCookie(
            String requestBody,
            String contentType
    ) {
        // Skapar en HttpRequest.Builder för att skicka en POST-förfrågan till auth-servern med hjälp av requestBody och contentType
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

    // Metod för att logga ut via gateway och ta bort access_token-cookien
    private ResponseEntity<?> sendLogin(
            HttpRequest.Builder request
    ) {
        // Skickar förfrågan till auth-servern och hanterar svaret
        try {
            HttpResponse<String> response = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            // Om svaret från auth-servern inte är framgångsrikt (statuskod 2xx), returnera svaret som det är
            if (response.statusCode() < 200 ||
                    response.statusCode() >= 300) {
                return ResponseEntity
                        .status(response.statusCode())
                        .body(response.body());
            }
            // Om svaret är framgångsrikt, skapa en login-response med access_token och cookie
            return createLoginResponse(response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Om det uppstår ett fel vid kommunikationen med auth-servern, returnera en 502 Bad Gateway-status med ett felmeddelande
            return ResponseEntity.status(502)
                    .body("Gateway could not reach auth service");
        }
    }

    // Metod för att skapa en login-response med access_token och cookie
    private ResponseEntity<?> createLoginResponse(String responseBody) {
        try {
            AuthServiceResponse authResponse = objectMapper.readValue(
                    responseBody,
                    AuthServiceResponse.class

            );
            //  Om authResponse inte innehåller en giltig token eller expiresIn, returnera en 502 Bad Gateway-status med ett felmeddelande
            if (authResponse.token() == null
                    || authResponse.token().isBlank()
                    || authResponse.expiresIn() <= 0) {
                return ResponseEntity
                        .status(502)
                        .body("Auth service returned an invalid response");
            }
            // Skapa en ResponseCookie med access_token och inställningar för httpOnly, secure, sameSite, path och maxAge
            ResponseCookie cookie = ResponseCookie
                    .from("access_token", authResponse.token())
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .sameSite(cookieSameSite)
                    .path("/")
                    .maxAge(Duration.ofSeconds(authResponse.expiresIn()))
                    .build();

            // Skapa en LoginResponse med expiresIn, subject och roles från authResponse
            LoginResponse loginResponse = new LoginResponse(
                    authResponse.expiresIn(),
                    authResponse.subject(),
                    authResponse.roles()
            );

            // Returnera en ResponseEntity med status 200 OK, set-cookie-headern och loginResponse som body
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(loginResponse);

            // om fel uppstår vid json svaret frpn auth servern, returnera 502 Bad Gateway med ett felmeddelande
        } catch (JacksonException ex) {
            return ResponseEntity
                    .status(502)
                    .body("Auth service returned an invalid response");
        }
    }

    // metod för att logga ut via gateway och ta bort access_token-cookien
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie
                .from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        // return för att ta bort acces token cookien och returnera 204 No Content
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    // metod för att hämta information om användaren via gateway
    private ResponseEntity<String> forwardGet(
            String baseUrl,
            String path,
            String authorization) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .GET();
        return send(builder, authorization);
    }
    // register metod
    public ResponseEntity<String> register(String requestBody, String contentType) {
        return forwardPost(authServerUrl, "/auth/register", requestBody, null, contentType);
    }
    // metod för att hämta information om den inloggade användaren via gateway
    public ResponseEntity<String> getMe(String authorization) {
        return forwardGet(authServerUrl, "/auth/me", authorization);
    }

    // metod för att hämta JWKS via gateway
    public ResponseEntity<String> getJwks() {
        return forwardGet(authServerUrl, "/auth/jwks", null);
    }
    // metod för att hämta alla produkter via gateway
    public ResponseEntity<String> getAllProducts(String authorization) {
        return forwardGet(productServiceUrl, "/products", authorization);
    }
    //  metod för att hämta en produkt efter ID via gateway
    public ResponseEntity<String> getProductById(Long id, String authorization) {
        return forwardGet(productServiceUrl, "/products/" + id, authorization);
    }

    // metod för att lägga till en ny produkt via gateway
    public ResponseEntity<String> addProduct(String requestBody, String authorization, String contentType) {
        return forwardPost(productServiceUrl, "/products/add", requestBody, authorization, contentType);
    }
    // metod för att ta bort en produkt via gateway
    public ResponseEntity<String> deleteProduct(Long id, String authorization) {
        return forwardDelete(productServiceUrl, "/products/delete/" + id, authorization);
    }
    // metod för att minska lagersaldo för en lista av produkter via gateway
    private ResponseEntity<String> forwardPost(
            String baseUrl,
            String path,
            String requestBody,
            String authorization,
            String contentType) {
        //builder för att skapa en HttpRequest med POST-metod och requestBody
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        // Om contentType inte är null eller blank, sätt Content-Type-headern i requesten
        if (contentType != null && !contentType.isBlank()) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }

        return send(builder, authorization);
    }
    // metod för att ta bort en produkt via gateway
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
    // metod för att skicka en HttpRequest och hantera svaret
    private ResponseEntity<String> send(HttpRequest.Builder builder, String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            builder.header(
                    HttpHeaders.AUTHORIZATION,
                    authorization
            );
        }
        // Skickar förfrågan och hanterar svaret
        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            // Skapar en ResponseEntity.BodyBuilder med statuskoden från svaret
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