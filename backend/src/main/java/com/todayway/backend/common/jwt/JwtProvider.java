package com.todayway.backend.common.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final JwtProperties props;

    public JwtProvider(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret()));
    }

    public String issueAccessToken(String memberUid) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(props.issuer())
                .subject(memberUid)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTokenExpirationMinutes(), ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public String issueRefreshToken(String memberUid) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(props.issuer())
                .subject(memberUid)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.refreshTokenExpirationDays(), ChronoUnit.DAYS)))
                .signWith(key)
                .compact();
    }

    public String parseSubject(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
