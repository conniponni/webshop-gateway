package iths.se.connie.webshopgateway.dto;

import java.util.List;

public record AuthServiceResponseDTO(
        String accessToken,
        long expiresIn,
        String subject,
        List<String> roles
) {
}
