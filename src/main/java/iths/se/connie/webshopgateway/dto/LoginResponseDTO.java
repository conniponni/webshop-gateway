package iths.se.connie.webshopgateway.dto;

import java.util.List;

public record LoginResponseDTO(
        long expiresIn,
        String subject,
        List<String> roles
) {
}
