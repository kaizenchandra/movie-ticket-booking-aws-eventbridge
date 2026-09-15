package com.kaizenchandra.awseventbridgedemo.bootstrap;

import com.kaizenchandra.awseventbridgedemo.shared.domain.Problem;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        public record Credentials(String username, String password) {
        }
    }
}
