package org.canvas.identity;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    UserDetailsService administrator(
            @Value("${canvas.admin.username}") String username,
            @Value("${canvas.admin.password-hash}") String passwordHash) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordHash.startsWith("{") ? passwordHash : "{bcrypt}" + passwordHash)
                .roles("ADMIN")
                .build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health", "/api/session", "/api/login", "/public/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginProcessingUrl("/api/login")
                        .successHandler((request, response, authentication) -> response.setStatus(200))
                        .failureHandler((request, response, exception) -> writeProblem(response, 401,
                                "Unauthorized", "The username or password was incorrect.")))
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(response, 401,
                                "Unauthorized", "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(response, 403,
                                "Forbidden", "The request was not permitted.")))
                .build();
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse response, int status,
            String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title
                + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
