
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

    private final GatewayProxyService gatewayProxyService;

    public GatewayController(GatewayProxyService gatewayProxyService) {
        this.gatewayProxyService = gatewayProxyService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.loginWithCookie(requestBody, request.getHeader(HttpHeaders.CONTENT_TYPE));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<String> register(@RequestBody String requestBody, HttpServletRequest request) {
        return gatewayProxyService.register(requestBody, request.getHeader(HttpHeaders.CONTENT_TYPE));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return gatewayProxyService.logout();
    }

    @GetMapping("/auth/jwks")
    public ResponseEntity<String> getJwks() {
        return gatewayProxyService.getJwks();
    }

    @GetMapping("/auth/me")
    public ResponseEntity<String> me(
            @CookieValue(name = "access_token", required = false) String token) {

        String authorization = toBearerHeader(token);
        if (authorization == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return gatewayProxyService.getMe(authorization);
    }

    @GetMapping("/products")
    public ResponseEntity<String> getAllProducts(
            @CookieValue(name = "access_token", required = false) String token,
            HttpServletRequest request) {
        return gatewayProxyService.getAllProducts(toBearerHeader(token)
        );
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<String> getProductById(
            @PathVariable Long id,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request)
    {
        return gatewayProxyService.getProductById(id, toBearerHeader(accessToken));
    }

    @PostMapping("/products/add")
    public ResponseEntity<String> addProduct(
            @RequestBody String requestBody,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request)
    {
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

    private String toBearerHeader(String token) {
        {
            return (token != null && !token.isBlank()) ? "Bearer " + token : null;
        }
    }
}