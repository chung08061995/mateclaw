package vip.mate.llm.account.model;

/** Provider-reported quota state. UNKNOWN means the provider exposes no quota API. */
public final class ProviderQuotaStatus {
    public static final String UNKNOWN = "UNKNOWN";
    public static final String OK = "OK";
    public static final String WARNING = "WARNING";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String EXHAUSTED = "EXHAUSTED";

    private ProviderQuotaStatus() {}
}
