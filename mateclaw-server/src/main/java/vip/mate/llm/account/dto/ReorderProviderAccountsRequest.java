package vip.mate.llm.account.dto;

import java.util.List;

/** Ordered account ids, first item receiving priority 1. */
public record ReorderProviderAccountsRequest(List<Long> accountIds) {}
