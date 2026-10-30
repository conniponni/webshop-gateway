
package iths.se.connie.webshopgateway.controller;

import iths.se.connie.webshopgateway.service.GatewayProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/gateway")
public class GatewayController {

    // GatewayProxyService används för att hantera proxy-anrop till andra tjänster
    private final GatewayProxyService gatewayProxyService;

    public GatewayController(GatewayProxyService gatewayProxyService) {
        this.gatewayProxyService = gatewayProxyService;
    }

    // Endpoint för att hantera inloggning via gateway
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.loginWithCookie(requestBody, request.getHeader(HttpHeaders.CONTENT_TYPE));
    }

    // Endpoint för att hantera registrering via gateway
    @PostMapping("/auth/register")
    public ResponseEntity<String> register(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.register(requestBody, request.getHeader(HttpHeaders.CONTENT_TYPE));
    }

    // Endpoint för att hantera utloggning via gateway
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return gatewayProxyService.logout();
    }

    // Endpoint för att hämta JWKS via gateway
    @GetMapping("/auth/jwks")
    public ResponseEntity<String> getJwks() {
        return gatewayProxyService.getJwks();
    }

    // Endpoint för att hämta information om den inloggade användaren via gateway
    @GetMapping("/auth/me")
    public ResponseEntity<String> me(
            @CookieValue(name = "access_token", required = false) String token) {

        String authorization = toBearerHeader(token);
        if (authorization == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return gatewayProxyService.getMe(authorization);
    }

    // Endpoint för att hämta alla produkter via gateway
    @GetMapping("/products")
    public ResponseEntity<String> getAllProducts(
            @CookieValue(name = "access_token", required = false) String token,
            HttpServletRequest request) {
        return gatewayProxyService.getAllProducts(toBearerHeader(token)
        );
    }

    // Endpoint för att hämta en produkt efter ID via gateway
    @GetMapping("/products/{id}")
    public ResponseEntity<String> getProductById(
            @PathVariable Long id,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request)
    {
        return gatewayProxyService.getProductById(id, toBearerHeader(accessToken));
    }

    // Endpoint för att lägga till en ny produkt via gateway
    @PostMapping("/products/add")
    public ResponseEntity<String> addProduct(
            @RequestBody String requestBody,
            // CookieValue(name = "access_token", required = false) String accessToken,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request)
    {
        // Anropar gatewayProxyService för att lägga till en produkt med hjälp av requestBody, accessToken och Content-Type från request-headern
        return gatewayProxyService.addProduct(
                requestBody,
                toBearerHeader(accessToken),
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }

    @DeleteMapping("/products/delete/{id}")
    public ResponseEntity<String> deleteProduct(
            @PathVariable Long id,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request)
    {
        return gatewayProxyService.deleteProduct(id, toBearerHeader(accessToken)
        );
    }

    @PostMapping("/order")
    public ResponseEntity<String> createOrder(
            @RequestBody String requestBody,
            @CookieValue(name = "access_token", required = false) String token,
            HttpServletRequest request)
    {
        return gatewayProxyService.createOrder(
                requestBody,
                toBearerHeader(token),
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }

    // Metod för att konvertera en token till en Bearer-header
    private String toBearerHeader(String token) {
        {
            return (token != null && !token.isBlank()) ? "Bearer " + token : null;
        }
    }
}