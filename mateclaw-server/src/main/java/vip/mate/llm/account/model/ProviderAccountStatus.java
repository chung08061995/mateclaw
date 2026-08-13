package vip.mate.llm.account.model;

/** Lifecycle states for a credential inside a provider account pool. */
public final class ProviderAccountStatus {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String ACTIVE = "ACTIVE";
    public static final String COOLDOWN = "COOLDOWN";
    public static final String EXHAUSTED = "EXHAUSTED";
    public static final String ERROR = "ERROR";
    public static final String DISABLED = "DISABLED";

    private ProviderAccountStatus() {}
}
