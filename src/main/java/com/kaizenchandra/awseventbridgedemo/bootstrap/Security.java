package com.kaizenchandra.awseventbridgedemo.bootstrap;

import org.springframework.context.annotation.*;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.in.RequestLimits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.cors.*;
import reactor.core.publisher.Flux;

import java.util.*;

@Configuration
public class Security {
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, @Value("${security.cors-origin:http://localhost:8080}") String origin) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "Last-Event-ID"));
        cors.setAllowCredentials(false);
        var source = new org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        var converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            return Flux.fromIterable(roles == null ? List.<String>of() : roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r));
        });
        return http.csrf(c -> c.disable()).cors(c -> c.configurationSource(source)).authorizeExchange(a -> a
                        .pathMatchers("/", "/index.html", "/app.js", "/openapi.yaml", "/local/token", "/api/simulator/callback", "/actuator/health/**").permitAll()
                        .pathMatchers("/api/admin/**", "/actuator/prometheus").hasRole("ADMIN")
                        .pathMatchers("/api/**").hasRole("CUSTOMER").anyExchange().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((exchange,error)->RequestLimits.reject(exchange,401,"UNAUTHORIZED")).accessDeniedHandler((exchange,error)->RequestLimits.reject(exchange,403,"FORBIDDEN")))
                .oauth2ResourceServer(o -> o.authenticationEntryPoint((exchange,error)->RequestLimits.reject(exchange,401,"UNAUTHORIZED")).accessDeniedHandler((exchange,error)->RequestLimits.reject(exchange,403,"FORBIDDEN")).jwt(j -> j.jwtAuthenticationConverter(converter))).build();
    }

    static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), jwt -> jwt.getAudience().contains(audience) ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null)));
    }

    @Bean
    @Profile("!local & !test")
    ReactiveJwtDecoder jwtDecoder(@Value("${security.issuer}") String issuer, @Value("${security.jwk-set-uri}") String jwks, @Value("${security.audience:cinema}") String audience) {
        var d = NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
        d.setJwtValidator(validators(issuer, audience));
        return d;
    }
}
