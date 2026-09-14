package com.kaizenchandra.awseventbridgedemo.bootstrap;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.jwt.*;

import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;
import java.time.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import com.kaizenchandra.awseventbridgedemo.shared.domain.Problem;

@Configuration
@Profile("local")
public class LocalIdentity {
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public LocalIdentity(@Value("${local.keys:.local}") String directory) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(directory, "private.der"))));
        publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Path.of(directory, "public.der"))));
    }

    @Bean
    ReactiveJwtDecoder localDecoder() {
        var decoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(Security.validators("http://localhost:8080/local", "cinema"));
        return decoder;
    }

    @RestController
    @Profile("local")
    public static class TokenHttp {
        private final RSAPrivateKey key;
        private final String customer;
        private final String admin;
        private final Clock clock;

        public TokenHttp(LocalIdentity identity, @Value("${local.customer-password}") String customer, @Value("${local.admin-password}") String admin, Clock clock) {
            this.key = identity.privateKey;
            this.customer = customer;
            this.admin = admin;
            this.clock = clock;
        }

        public record Credentials(String username, String password) {
        }

        @PostMapping("/local/token")
        public Map<String, Object> token(@RequestBody Credentials body) throws Exception {
            boolean isAdmin = "admin".equals(body.username());
            Problem.require(Set.of("alice", "bob", "admin").contains(body.username()) && body.password() != null && MessageDigest.isEqual((isAdmin ? admin : customer).getBytes(java.nio.charset.StandardCharsets.UTF_8), body.password().getBytes(java.nio.charset.StandardCharsets.UTF_8)), "INVALID_CREDENTIALS");
            var now = clock.instant();
            var claims = new JWTClaimsSet.Builder().issuer("http://localhost:8080/local").audience("cinema").subject(body.username()).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(900))).claim("roles", isAdmin ? List.of("ADMIN", "CUSTOMER") : List.of("CUSTOMER")).build();
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(key));
            return Map.of("access_token", jwt.serialize(), "expires_in", 900, "token_type", "Bearer");
        }
    }
}
