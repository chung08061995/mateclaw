package vip.mate.llm.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import vip.mate.llm.account.model.ProviderAccountEntity;
import vip.mate.llm.account.repository.ProviderAccountMapper;
import vip.mate.llm.account.service.ProviderAccountService;
import vip.mate.llm.repository.ModelProviderMapper;
import vip.mate.system.service.SettingCrypto;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderQuotaHeaderObservationTest {

    @Test
    void computesUsedPercentResetAndWarningFromProviderHeaders() {
        ProviderAccountMapper mapper = mock(ProviderAccountMapper.class);
        ProviderAccountEntity account = new ProviderAccountEntity();
        account.setId(9L);
        account.setEnabled(true);
        account.setStatus("AVAILABLE");
        account.setQuotaStatus("UNKNOWN");
        account.setAlertEnabled(true);
        account.setAlertThresholdPercent(new BigDecimal("80.00"));
        when(mapper.selectById(9L)).thenReturn(account);
        ProviderAccountService service = new ProviderAccountService(mapper,
                mock(ModelProviderMapper.class), mock(SettingCrypto.class), new ObjectMapper());

        long before = System.currentTimeMillis();
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ratelimit-limit-tokens", "1000");
        headers.set("x-ratelimit-remaining-tokens", "150");
        headers.set("x-ratelimit-reset-tokens", "6m0s");

        service.observeQuotaHeaders(9L, headers);

        assertEquals(new BigDecimal("85.00"), account.getQuotaUsedPercent());
        assertEquals("WARNING", account.getQuotaStatus());
        assertEquals("WARNING", account.getAlertStatus());
        assertNotNull(account.getQuotaResetAt());
        assertTrue(account.getQuotaResetAt() >= before + 359_000);
        assertNotNull(account.getQuotaUpdatedAt());
        verify(mapper).updateById(account);
    }
}
