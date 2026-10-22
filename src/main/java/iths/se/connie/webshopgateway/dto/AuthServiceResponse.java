package iths.se.connie.webshopgateway.dto;

import java.util.List;

public record AuthServiceResponse(
        String token,
        Long expiresIn,
        String subject,
        List<String> roles
) {
}
