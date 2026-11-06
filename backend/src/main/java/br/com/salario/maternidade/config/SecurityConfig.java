package br.com.salario.maternidade.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
public class SecurityConfig {
    private static final String DEFAULT_ORIGINS = "http://localhost:4200,http://127.0.0.1:4200,https://auxiliomaternidade.financasemdiabrasil.com.br,https://auxilio-maternidade-frontend.onrender.com";
    @Value("${CORS_ALLOWED_ORIGINS:" + DEFAULT_ORIGINS + "}")
    private String allowedOrigins = DEFAULT_ORIGINS;
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable()) // API stateless; revisar se adicionar cookies/sessões.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/health", "/api/whatsapp/webhook").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/whatsapp/webhook").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/leads", "/api/triagens", "/api/triagem", "/api/atendimentos").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .httpBasic(Customizer.withDefaults())
            .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(java.util.Arrays.stream(allowedOrigins.split(","))
            .map(String::trim).filter(origin -> !origin.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    UserDetailsService adminUsers(
        @Value("${ADMIN_USER:}") String username,
        @Value("${ADMIN_PASSWORD_HASH:}") String passwordHash) {
        if (username.isBlank() || !passwordHash.matches("^\\$2[aby]\\$.*")) {
            // Sem credenciais configuradas, nenhum administrador pode entrar.
            return new InMemoryUserDetailsManager();
        }
        return new InMemoryUserDetailsManager(
            User.withUsername(username).password("{bcrypt}" + passwordHash).roles("ADMIN").build());
    }
}
