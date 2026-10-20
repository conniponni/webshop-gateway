
package iths.se.connie.webshopgateway.controller;

import iths.se.connie.webshopgateway.service.GatewayProxyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Duration;

@RestController
@RequestMapping("/gateway")
public class GatewayController {

    private final GatewayProxyService gatewayProxyService;

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    public GatewayController(GatewayProxyService gatewayProxyService) {
        this.gatewayProxyService = gatewayProxyService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<String> login(@RequestBody String body) {
        var result = gatewayProxyService.login(body);

        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(result.status());

        if (result.token() != null) {
            ResponseCookie cookie = ResponseCookie
                    .from("ACCESS_TOKEN", result.token())
                    .httpOnly(true)
                    .secure(secureCookie)
                    .path("/")
                    .sameSite("Lax")
                    .maxAge(Duration.ofSeconds(result.expiresIn()))
                    .build();

            response.header(
                    HttpHeaders.SET_COOKIE,
                    cookie.toString()
            );
        }

        return response
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(result.body());
    }

    @PostMapping("/auth/register")
    public ResponseEntity<String> register(@RequestBody String body) {
        var result = gatewayProxyService.register(body);

        return ResponseEntity.status(result.status())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(result.body());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie
                .from("ACCESS_TOKEN", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/auth/jwks")
    public ResponseEntity<String> jwks() {
        var result = gatewayProxyService.getJwks();

        return ResponseEntity.status(result.status())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(result.body());
    }

    @GetMapping("/auth/me")
    public ResponseEntity<String> me(
            @CookieValue(
                    value = "ACCESS_TOKEN",
                    required = false
            ) String token
    ) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401)
                    .body("{\"error\":\"Not authenticated\"}");
        }

        var result = gatewayProxyService.getCurrentUser(token);

        return ResponseEntity.status(result.status())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(result.body());
    }
}
