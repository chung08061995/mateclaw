package vip.mate.llm.account;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import vip.mate.llm.account.model.ProviderAccountEntity;
import vip.mate.llm.account.repository.ProviderAccountMapper;
import vip.mate.llm.account.service.ProviderAccountService;
import vip.mate.llm.model.ModelProviderEntity;
import vip.mate.llm.repository.ModelProviderMapper;
import vip.mate.system.service.SettingCrypto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderAccountDeletionTest {

    @Test
    void deletedLegacyOauthAccountStaysDeletedWhenAccountsAreReloaded() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProviderAccountEntity.class);
        TableInfoHelper.initTableInfo(assistant, ModelProviderEntity.class);
        ProviderAccountMapper accountMapper = mock(ProviderAccountMapper.class);
        ModelProviderMapper providerMapper = mock(ModelProviderMapper.class);
        SettingCrypto crypto = mock(SettingCrypto.class);
        ProviderAccountService service = new ProviderAccountService(
                accountMapper, providerMapper, crypto, new ObjectMapper());

        ProviderAccountEntity account = new ProviderAccountEntity();
        account.setId(42L);
        account.setProviderId("openai-chatgpt");
        account.setAuthType("oauth");
        account.setExternalAccountId("account-1");
        account.setLegacyImport(true);

        ModelProviderEntity legacy = new ModelProviderEntity();
        legacy.setProviderId("openai-chatgpt");
        legacy.setName("OpenAI ChatGPT");
        legacy.setAuthType("oauth");
        legacy.setOauthAccessToken("still-present");
        legacy.setOauthRefreshToken("refresh-token");
        legacy.setOauthAccountId("account-1");

        when(accountMapper.selectById(42L)).thenReturn(account);
        when(accountMapper.deleteById(42L)).thenReturn(1);
        when(accountMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(accountMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(providerMapper.selectById("openai-chatgpt")).thenReturn(legacy);
        when(providerMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(crypto.encrypt(anyString())).thenReturn("encrypted");

        service.deleteAccount(42L);

        assertTrue(service.listAccounts("openai-chatgpt").isEmpty());
        verify(accountMapper, never()).insert(any(ProviderAccountEntity.class));
    }
}
