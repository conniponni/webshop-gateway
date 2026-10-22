package iths.se.connie.webshopgateway.dto;

import java.util.List;

public record LoginResponse(
        Long expiresIn,
        String subject,
        List<String> roles
) {
}
