package vip.mate.llm.account.model;

/**
 * Decrypted credentials for model construction. This record is intentionally
 * never used as a controller response.
 */
public record ProviderAccountCredentials(
        String apiKey,
        String accessToken,
        String refreshToken,
        String providerAccountId,
        Long expiresAt
) {}
