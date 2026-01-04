package cn.autoai.demo.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Header-based authentication filter
 *
 * Reads X-User-Id from request header, grants ADMIN role if demo-user-123
 * Otherwise grants no role (unauthenticated state)
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String DEMO_USER_ID = "demo-user-123";
    private static final String HEADER_NAME = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(HEADER_NAME);

        if (DEMO_USER_ID.equals(userId)) {
            // User ID matches, grant ADMIN role
            DemoUser user = new DemoUser(userId, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            logger.debug("User " + userId + " authenticated, role: ROLE_ADMIN");
        } else {
            // User ID does not match or does not exist, set to unauthenticated state
            SecurityContextHolder.clearContext();

            if (userId != null && !userId.isEmpty()) {
                logger.debug("User " + userId + " authentication failed, no valid role");
            }
        }

        filterChain.doFilter(request, response);
    }
}
