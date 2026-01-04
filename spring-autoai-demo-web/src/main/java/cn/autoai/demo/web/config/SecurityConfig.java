package cn.autoai.demo.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security configuration - Header-based authentication
 *
 * Permission description:
 * 1. Public interfaces (no permission required):
 *    - GET /api/users* - Query users
 *    - GET /api/orders* - Query orders
 *    - GET /api/orders/products* - Get product list
 *    - GET /api/orders/customers* - Get customer list
 *    - GET /api/orders/statistics* - Statistical information
 *    - /auto-ai/** - AutoAi conversation interface
 *    - /, /index.html - Static resources
 *
 * 2. Interfaces requiring authentication (need valid user):
 *    - POST /api/users - Create user (requires ADMIN role)
 *    - PUT /api/users/{id} - Update user (requires ADMIN role)
 *    - DELETE /api/users/{id} - Delete user (requires ADMIN role)
 *    - POST /api/users/{id}/salary - Adjust salary (requires ADMIN role)
 *    - POST /api/orders - Create order (requires USER role)
 *    - PUT /api/orders/{orderNo}/status - Update order status (requires USER role)
 *    - DELETE /api/orders/{orderNo} - Cancel order (requires ADMIN role)
 *
 * Authentication mechanism:
 * - Frontend needs to provide X-User-Id in request header
 * - When X-User-Id is demo-user-123, grant ROLE_ADMIN role
 * - Other user IDs or no user ID provided, considered unauthenticated
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public HeaderAuthenticationFilter headerAuthenticationFilter() {
        return new HeaderAuthenticationFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Disable CSRF (RESTful APIs typically don't need it)
            .csrf(csrf -> csrf.disable())
            // Configure session management as stateless
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // Add custom Header authentication filter
            .addFilterBefore(headerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // ========== Public interfaces (no authentication required) ==========

                // AutoAi conversation interface
                .requestMatchers("/auto-ai/**").permitAll()

                // Static resources
                .requestMatchers("/", "/index.html", "/favicon.ico").permitAll()

                // User query interfaces (read-only, public)
                .requestMatchers(HttpMethod.GET, "/api/users").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/**").permitAll()

                // Order query interfaces (read-only, public)
                .requestMatchers(HttpMethod.GET, "/api/orders").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/orders/**").permitAll()

                // Public query interfaces
                .requestMatchers(HttpMethod.GET, "/api/orders/products").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/orders/customers").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/orders/statistics/**").permitAll()

                // ========== Interfaces requiring authentication (write operations) ==========

                // User management write operations (requires ADMIN role)
                // .requestMatchers(HttpMethod.POST, "/api/users").hasRole("ADMIN")
                // .requestMatchers(HttpMethod.PUT, "/api/users/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/users/*").hasRole("ADMIN")
                // .requestMatchers(HttpMethod.POST, "/api/users/*/salary").hasRole("ADMIN")

                // // Order management write operations (requires USER role)
                // .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("USER")
                // .requestMatchers(HttpMethod.PUT, "/api/orders/*/status").hasRole("USER")
                // .requestMatchers(HttpMethod.DELETE, "/api/orders/*").hasRole("ADMIN")

                // ========== All other requests require authentication ==========
                .anyRequest().permitAll()
            );

        return http.build();
    }

    /**
     * CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));

        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"
        ));

        // Allowed Headers
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Allow credentials
        configuration.setAllowCredentials(true);

        // Exposed Headers (for frontend reading)
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Total-Count"
        ));

        // Preflight request cache time (seconds)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
