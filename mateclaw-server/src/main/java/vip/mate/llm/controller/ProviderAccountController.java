package vip.mate.llm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.common.result.R;
import vip.mate.llm.account.dto.CreateProviderAccountRequest;
import vip.mate.llm.account.dto.ProviderAccountDTO;
import vip.mate.llm.account.dto.ProviderUsageSummaryDTO;
import vip.mate.llm.account.dto.ReorderProviderAccountsRequest;
import vip.mate.llm.account.dto.UpdateProviderAccountRequest;
import vip.mate.llm.account.dto.UpdateProviderQuotaRequest;
import vip.mate.llm.account.service.ProviderAccountService;
import vip.mate.workspace.core.annotation.RequireGlobalAdmin;

import java.util.List;

/** Administrative REST interface for multi-account provider pools and usage. */
@Tag(name = "Provider Accounts")
@RestController
@RequestMapping("/api/v1/llm/provider-accounts")
@RequiredArgsConstructor
public class ProviderAccountController {
    private final ProviderAccountService accountService;

    @Operation(summary = "List provider accounts without exposing credentials")
    @GetMapping
    @RequireGlobalAdmin
    public R<List<ProviderAccountDTO>> list(
            @RequestParam(value = "providerId", required = false) String providerId) {
        return R.ok(accountService.listAccounts(providerId));
    }

    @Operation(summary = "Create an API-key account for any provider")
    @PostMapping
    @RequireGlobalAdmin
    public R<ProviderAccountDTO> create(@RequestBody CreateProviderAccountRequest request) {
        return R.ok(accountService.createApiKeyAccount(request));
    }

    @Operation(summary = "Update provider account metadata or replace its API key")
    @PutMapping("/{accountId}")
    @RequireGlobalAdmin
    public R<ProviderAccountDTO> update(@PathVariable Long accountId,
                                        @RequestBody UpdateProviderAccountRequest request) {
        return R.ok(accountService.updateAccount(accountId, request));
    }

    @Operation(summary = "Delete a provider account")
    @DeleteMapping("/{accountId}")
    @RequireGlobalAdmin
    public R<Void> delete(@PathVariable Long accountId) {
        accountService.deleteAccount(accountId);
        return R.ok();
    }

    @Operation(summary = "Enable a provider account")
    @PostMapping("/{accountId}/enable")
    @RequireGlobalAdmin
    public R<ProviderAccountDTO> enable(@PathVariable Long accountId) {
        return R.ok(accountService.setEnabled(accountId, true));
    }

    @Operation(summary = "Disable a provider account")
    @PostMapping("/{accountId}/disable")
    @RequireGlobalAdmin
    public R<ProviderAccountDTO> disable(@PathVariable Long accountId) {
        return R.ok(accountService.setEnabled(accountId, false));
    }

    @Operation(summary = "Reorder every account in one provider pool")
    @PutMapping("/providers/{providerId}/order")
    @RequireGlobalAdmin
    public R<List<ProviderAccountDTO>> reorder(@PathVariable String providerId,
                                               @RequestBody ReorderProviderAccountsRequest request) {
        return R.ok(accountService.reorder(providerId, request.accountIds()));
    }

    @Operation(summary = "Update provider-reported quota and reset metadata")
    @PutMapping("/{accountId}/quota")
    @RequireGlobalAdmin
    public R<ProviderAccountDTO> updateQuota(@PathVariable Long accountId,
                                             @RequestBody UpdateProviderQuotaRequest request) {
        return R.ok(accountService.updateQuotaStatus(accountId, request));
    }

    @Operation(summary = "Get MateClaw token totals and provider quota metadata")
    @GetMapping("/usage")
    @RequireGlobalAdmin
    public R<ProviderUsageSummaryDTO> usage() {
        return R.ok(accountService.usageSummary());
    }
}
