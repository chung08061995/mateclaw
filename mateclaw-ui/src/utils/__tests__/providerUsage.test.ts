import { describe, expect, it } from 'vitest'
import {
  clampedQuotaPercent,
  hasKnownQuota,
  normalizeProviderUsageAccounts,
  providerAlertTone,
} from '@/utils/providerUsage'

describe('provider usage response normalization', () => {
  it('normalizes the backend usage DTO and derives the active account', () => {
    const accounts = normalizeProviderUsageAccounts({
      accountCount: 1,
      totalTokens: 42,
      accounts: [{
        id: '1234567890123456789',
        providerId: 'openai-chatgpt',
        label: 'Work',
        authType: 'oauth',
        enabled: true,
        priority: 1,
        status: 'ACTIVE',
        quotaStatus: 'UNKNOWN',
        quotaUsedPercent: null,
        promptTokens: 30,
        completionTokens: 12,
        totalTokens: 42,
      }],
    })

    expect(accounts).toHaveLength(1)
    expect(accounts[0].id).toBe('1234567890123456789')
    expect(accounts[0].active).toBe(true)
    expect(accounts[0].totalTokens).toBe(42)
  })

  it('preserves an explicit zero-percent quota as known', () => {
    const account = normalizeProviderUsageAccounts([{
      id: 1,
      providerId: 'gemini',
      label: 'Primary',
      authType: 'api_key',
      enabled: true,
      priority: 1,
      status: 'AVAILABLE',
      active: false,
      quotaUsedPercent: 0,
      promptTokens: 0,
      completionTokens: 0,
      totalTokens: 0,
    }])[0]

    expect(hasKnownQuota(account)).toBe(true)
    expect(clampedQuotaPercent(account)).toBe(0)
  })

  it('keeps missing provider quota explicitly unknown', () => {
    const account = normalizeProviderUsageAccounts([{
      id: 1,
      providerId: 'anthropic',
      label: 'Backup',
      authType: 'api_key',
      enabled: true,
      priority: 2,
      status: 'AVAILABLE',
      active: false,
      quotaUsedPercent: null,
      promptTokens: 0,
      completionTokens: 0,
      totalTokens: 0,
    }])[0]

    expect(hasKnownQuota(account)).toBe(false)
    expect(clampedQuotaPercent(account)).toBeNull()
  })
})
describe('provider quota warnings', () => {
  const base = {
    id: 1,
    providerId: 'openai',
    label: 'Primary',
    authType: 'api_key',
    enabled: true,
    priority: 1,
    active: false,
    promptTokens: 0,
    completionTokens: 0,
    totalTokens: 0,
  }

  it('warns at 80 percent', () => {
    expect(providerAlertTone({ ...base, status: 'AVAILABLE', quotaUsedPercent: 80 })).toBe('warning')
  })

  it('treats an exhausted provider signal as critical without a percentage', () => {
    expect(providerAlertTone({
      ...base,
      status: 'EXHAUSTED',
      quotaStatus: 'EXHAUSTED',
      quotaUsedPercent: null,
    })).toBe('critical')
  })

  it('shows a healthy enabled account as available when quota is unknown', () => {
    expect(providerAlertTone({ ...base, status: 'AVAILABLE', quotaUsedPercent: null })).toBe('healthy')
  })
})
