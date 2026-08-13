export type ProviderAccountId = string | number

export interface ProviderUsageAccount {
  id: ProviderAccountId
  providerId: string
  providerName?: string
  label: string
  externalAccountId?: string | null
  authType: string
  enabled: boolean
  priority: number
  status?: string | null
  active: boolean
  quotaUsedPercent?: number | null
  quotaResetAt?: string | number | null
  quotaUpdatedAt?: string | number | null
  quotaSource?: string | null
  alertLevel?: string | null
  quotaStatus?: string | null
  alertStatus?: string | null
  alertEnabled?: boolean
  alertThresholdPercent?: number | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  lastUsedAt?: string | number | null
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

export interface ProviderUsageProviderGroup {
  providerId: string
  providerName?: string
  accounts?: ProviderUsageAccount[]
  [key: string]: unknown
}

export interface ProviderUsageSummaryPayload {
  providerCount?: number
  accountCount?: number
  enabledAccountCount?: number
  activeAccountCount?: number
  alertCount?: number
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  accounts?: ProviderUsageAccount[]
  providers?: Array<ProviderUsageProviderGroup | ProviderUsageAccount>
  activeAccount?: ProviderUsageAccount | null
}

export interface CreateProviderAccountPayload {
  providerId: string
  label: string
  apiKey: string
  externalAccountId?: string
  priority?: number
  alertEnabled?: boolean
  alertThresholdPercent?: number
}

export interface UpdateProviderAccountPayload {
  label?: string
  apiKey?: string
  priority?: number
}
