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

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable()) // API stateless; revisar se adicionar cookies/sessões.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/leads", "/api/triagens", "/api/triagem").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .httpBasic(Customizer.withDefaults())
            .build();
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
            User.withUsername(username).password(passwordHash).roles("ADMIN").build());
    }
}
