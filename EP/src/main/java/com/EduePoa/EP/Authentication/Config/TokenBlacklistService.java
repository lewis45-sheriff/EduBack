package com.EduePoa.EP.Authentication.Config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TokenBlacklistService {

    // Use a cache with TTL matching your token expiration
    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedRate = 3600000) // Clean up every hour
    public void cleanupExpiredTokens() {
        // Remove tokens that have expired
        // You'd need to store token + expiration time
        blacklistedTokens.clear(); // Simple version
    }

    public void blacklistToken(String token) {
        if (token != null && !token.trim().isEmpty()) {
            blacklistedTokens.add(token);
            log.info("Token blacklisted");
        }
    }

    public boolean isBlacklisted(String token) {
        return blacklistedTokens.contains(token);
    }
}