import type {
  ProviderUsageAccount,
  ProviderUsageProviderGroup,
  ProviderUsageSummaryPayload,
} from '@/types/providerUsage'

export type ProviderAlertTone = 'critical' | 'warning' | 'healthy' | 'muted'

function looksLikeAccount(value: unknown): value is ProviderUsageAccount {
  if (!value || typeof value !== 'object') return false
  const record = value as Record<string, unknown>
  return record.id != null && typeof record.providerId === 'string'
}

function normalizeAccount(
  account: ProviderUsageAccount,
  group?: ProviderUsageProviderGroup,
): ProviderUsageAccount {
  const status = account.status || null
  const quotaStatus = account.quotaStatus || null
  const triggered = (account.alertStatus || '').toUpperCase() === 'TRIGGERED'
  const inferredAlert = triggered
    ? (quotaStatus || status || 'WARNING')
    : account.alertLevel

  return {
    ...account,
    providerId: account.providerId || group?.providerId || '',
    providerName: account.providerName || group?.providerName || undefined,
    authType: account.authType || 'api_key',
    enabled: account.enabled !== false,
    priority: Number.isFinite(Number(account.priority)) ? Number(account.priority) : 100,
    status,
    active: account.active === true || (status || '').toUpperCase() === 'ACTIVE',
    quotaStatus,
    alertLevel: inferredAlert,
    promptTokens: Number(account.promptTokens || 0),
    completionTokens: Number(account.completionTokens || 0),
    totalTokens: Number(account.totalTokens || 0),
  }
}

/**
 * The summary endpoint accepts both the flat response used by the desktop app
 * and a provider-grouped response used by server deployments. Normalising at
 * this boundary keeps the view deliberately boring and resilient during a
 * rolling frontend/backend update.
 */
export function normalizeProviderUsageAccounts(value: unknown): ProviderUsageAccount[] {
  const payload = (value ?? {}) as ProviderUsageSummaryPayload

  if (Array.isArray(value)) {
    return value.filter(looksLikeAccount).map((account) => normalizeAccount(account))
  }

  if (Array.isArray(payload.accounts)) {
    return payload.accounts.filter(looksLikeAccount).map((account) => normalizeAccount(account))
  }

  if (!Array.isArray(payload.providers)) return []

  return payload.providers.flatMap((provider) => {
    if (looksLikeAccount(provider)) return [normalizeAccount(provider)]
    const group = provider as ProviderUsageProviderGroup
    if (!Array.isArray(group.accounts)) return []
    return group.accounts.filter(looksLikeAccount).map((account) => normalizeAccount(account, group))
  })
}

export function hasKnownQuota(account: ProviderUsageAccount): boolean {
  return typeof account.quotaUsedPercent === 'number' && Number.isFinite(account.quotaUsedPercent)
}

export function clampedQuotaPercent(account: ProviderUsageAccount): number | null {
  if (!hasKnownQuota(account)) return null
  return Math.min(100, Math.max(0, account.quotaUsedPercent as number))
}

export function providerAlertTone(account: ProviderUsageAccount): ProviderAlertTone {
  const alert = (account.alertLevel || account.alertStatus || '').toUpperCase()
  const status = (account.status || '').toUpperCase()
  const quotaStatus = (account.quotaStatus || '').toUpperCase()
  const percent = clampedQuotaPercent(account)

  if (
    alert === 'CRITICAL' ||
    alert === 'EXHAUSTED' ||
    status === 'EXHAUSTED' ||
    status === 'QUOTA_EXHAUSTED' ||
    quotaStatus === 'EXHAUSTED' ||
    (percent != null && percent >= 100)
  ) return 'critical'

  if (
    alert === 'WARNING' ||
    status === 'RATE_LIMITED' ||
    status === 'COOLDOWN' ||
    quotaStatus === 'RATE_LIMITED' ||
    quotaStatus === 'WARNING' ||
    (percent != null && percent >= 80)
  ) return 'warning'

  if (account.enabled && ['ACTIVE', 'AVAILABLE', 'CONNECTED', 'HEALTHY', 'READY'].includes(status)) {
    return 'healthy'
  }

  return 'muted'
}
