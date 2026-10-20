
package iths.se.connie.webshopgateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class JwtCookieFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "ACCESS_TOKEN";
    private static final String AUTHORIZATION = "Authorization";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        // Only add cookie-based authentication to order requests.
        return !(path.equals("/gateway/orders")
                || path.startsWith("/gateway/orders/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = getTokenFromCookie(request);

        HttpServletRequest wrappedRequest =
                new HttpServletRequestWrapper(request) {

                    @Override
                    public String getHeader(String name) {
                        if (AUTHORIZATION.equalsIgnoreCase(name)) {
                            return token == null
                                    ? null
                                    : "Bearer " + token;
                        }

                        return super.getHeader(name);
                    }

                    @Override
                    public Enumeration<String> getHeaders(String name) {
                        if (AUTHORIZATION.equalsIgnoreCase(name)) {
                            if (token == null) {
                                return Collections.emptyEnumeration();
                            }

                            return Collections.enumeration(
                                    List.of("Bearer " + token)
                            );
                        }

                        return super.getHeaders(name);
                    }

                    @Override
                    public Enumeration<String> getHeaderNames() {
                        List<String> names =
                                Collections.list(super.getHeaderNames());

                        // Do not trust a client-supplied Authorization header.
                        names.removeIf(
                                AUTHORIZATION::equalsIgnoreCase
                        );

                        if (token != null) {
                            names.add(AUTHORIZATION);
                        }

                        return Collections.enumeration(names);
                    }
                };

        filterChain.doFilter(wrappedRequest, response);
    }

    private String getTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())
                    && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
