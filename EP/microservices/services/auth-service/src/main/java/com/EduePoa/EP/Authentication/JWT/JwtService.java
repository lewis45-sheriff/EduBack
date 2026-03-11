package com.EduePoa.EP.Authentication.JWT;

import com.EduePoa.EP.Authentication.User.UserRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {

    @Autowired
    private UserRepository userRepository;

    @Value("${jwt.secret-key}")
    private String secretKey;

    //     Generate Access Token (Short-lived token)
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "ACCESS");
        return generateJwtToken(claims, userDetails,  2000); // 24 hours
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "REFRESH");
        return generateJwtToken(claims, userDetails, 30L * 24 * 60 * 60 * 1000); // 30 days
    }

    public String generateJwtToken(Map<String, Object> extraClaims, UserDetails userDetails, long expirationTime) {
        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Validate Access Token
    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    // Validate Refresh Token (Usually doesn't expire soon, but can be revoked)
    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    // Extract Username (for both access and refresh tokens)
    public String extractUsername(String token) {
        try {
            return extractClaim(token, Claims::getSubject);
        } catch (ExpiredJwtException e) {
            log.error("JWT expired: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.error("JWT error: {}", e.getMessage());
            return null;
        }
    }
    public  String username(String token) {
        try {
            return extractClaim(token, Claims::getSubject);
        } catch (ExpiredJwtException e) {
            log.error("JWT expired: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.error("JWT error: {}", e.getMessage());
            return null;
        }
    }

    // Extract claim from token
    public <T> T extractClaim(String token, Function<Claims, T> claimsTFunction) {
        final Claims claims = getAllClaims(token);
        return claimsTFunction.apply(claims);
    }
    private Claims getAllClaims(String token) {
        try {
            return Jwts
                    .parserBuilder()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token", e);
        }
    }
    public <T> ResponseEntity<?> extractClaimSafely(String token, Function<Claims, T> claimsTFunction) {
        try {
            final Claims claims = getAllClaims(token);
            T result = claimsTFunction.apply(claims);
            return ResponseEntity.ok(result);
        } catch (JwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            CustomResponse errorResponse = new CustomResponse(
                    "Invalid or expired token.",
                    401,
                    null
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }

    // Check if token is expired
    private boolean isTokenExpired(String token) {
        return extractExpirationDate(token).before(new Date());
    }

    // Extract expiration date from token
    Date extractExpirationDate(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Get signing key for JWT
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    public static String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            System.out.println("Authentication is null");
            return "UNKNOWN_USER";
        }

        if (!authentication.isAuthenticated()) {
            System.out.println("User is not authenticated");
            return "UNKNOWN_USER";
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            System.out.println("User is anonymous");
            return "UNKNOWN_USER";
        }

        Object principal = authentication.getPrincipal();
        return (principal instanceof UserDetails) ? ((UserDetails) principal).getUsername() : principal.toString();
    }
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username != null && username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

}
