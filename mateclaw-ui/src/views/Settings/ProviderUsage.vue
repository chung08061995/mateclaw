<template>
  <section class="provider-usage-page" aria-labelledby="provider-usage-title">
    <header class="page-header">
      <div class="page-lead">
        <div class="page-kicker">{{ t('providerUsage.kicker') }}</div>
        <h1 id="provider-usage-title" class="page-title">{{ t('providerUsage.title') }}</h1>
        <p class="page-desc">{{ t('providerUsage.desc') }}</p>
      </div>
      <div class="header-actions">
        <button class="btn btn--secondary" type="button" :disabled="loading" @click="loadAccounts">
          <svg aria-hidden="true" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="23 4 23 10 17 10" />
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
          </svg>
          {{ loading ? t('providerUsage.refreshing') : t('providerUsage.refresh') }}
        </button>
        <button class="btn btn--primary" type="button" @click="openCreateDialog">
          <svg aria-hidden="true" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          {{ t('providerUsage.addAccount') }}
        </button>
      </div>
    </header>

    <div v-if="loading && accounts.length === 0" class="state-panel" role="status">
      <span class="spinner" aria-hidden="true"></span>
      <span>{{ t('providerUsage.loading') }}</span>
    </div>

    <div v-else-if="loadError && accounts.length === 0" class="state-panel state-panel--error" role="alert">
      <svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="8" x2="12" y2="12" />
        <line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
      <strong>{{ loadError }}</strong>
      <button class="btn btn--secondary" type="button" @click="loadAccounts">{{ t('providerUsage.refresh') }}</button>
    </div>

    <template v-else>
      <div class="summary-grid" aria-label="Provider pool summary">
        <article class="summary-card summary-card--active">
          <span class="summary-label">{{ t('providerUsage.currentAccount') }}</span>
          <template v-if="activeAccount">
            <div class="active-account">
              <span class="provider-logo provider-logo--small">
                <img :src="getProviderIcon(activeAccount.providerId)" alt="" @error="onProviderIconError" />
              </span>
              <span class="summary-main summary-main--account">
                <strong>{{ accountLabel(activeAccount) }}</strong>
                <small>{{ providerLabel(activeAccount) }}</small>
              </span>
            </div>
          </template>
          <span v-else class="summary-main summary-main--empty">{{ t('providerUsage.noActiveAccount') }}</span>
        </article>

        <article class="summary-card">
          <span class="summary-label">{{ t('providerUsage.totalAccounts') }}</span>
          <strong class="summary-value">{{ accounts.length.toLocaleString() }}</strong>
        </article>

        <article class="summary-card">
          <span class="summary-label">{{ t('providerUsage.totalTokens') }}</span>
          <strong class="summary-value">{{ formatNumber(totalTokens) }}</strong>
        </article>

        <article class="summary-card" :class="{ 'summary-card--warning': alertAccounts.length > 0 }">
          <span class="summary-label">{{ t('providerUsage.alerts') }}</span>
          <strong class="summary-value">{{ alertAccounts.length.toLocaleString() }}</strong>
        </article>
      </div>

      <aside v-if="alertAccounts.length > 0" class="alert-banner" role="alert">
        <span class="alert-icon" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </span>
        <span>
          <strong>{{ t('providerUsage.warningTitle', { count: alertAccounts.length }) }}</strong>
          <small>{{ t('providerUsage.warningDesc') }}</small>
        </span>
      </aside>

      <div v-if="accounts.length === 0" class="empty-panel">
        <span class="empty-icon" aria-hidden="true">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
            <path d="M4 19V9" /><path d="M10 19V5" /><path d="M16 19v-7" /><path d="M22 19H2" />
            <circle cx="18" cy="6" r="3" /><path d="m16.8 6 .8.8L19.5 5" />
          </svg>
        </span>
        <h2>{{ t('providerUsage.emptyTitle') }}</h2>
        <p>{{ t('providerUsage.emptyDesc') }}</p>
        <button class="btn btn--primary" type="button" @click="openCreateDialog">{{ t('providerUsage.addAccount') }}</button>
      </div>

      <div v-else class="account-grid">
        <article
          v-for="account in orderedAccounts"
          :key="String(account.id)"
          class="account-card"
          :class="[`account-card--${alertTone(account)}`, { 'account-card--active': account.active, 'account-card--disabled': !account.enabled }]"
        >
          <header class="account-card__header">
            <div class="account-identity">
              <span class="provider-logo">
                <img :src="getProviderIcon(account.providerId)" alt="" @error="onProviderIconError" />
              </span>
              <span class="account-heading">
                <span class="provider-name">{{ providerLabel(account) }}</span>
                <strong>{{ accountLabel(account) }}</strong>
              </span>
            </div>
            <div class="account-badges">
              <span v-if="account.active" class="badge badge--active">{{ t('providerUsage.active') }}</span>
              <span v-else-if="!account.enabled" class="badge badge--muted">{{ t('providerUsage.disabled') }}</span>
              <span v-else class="badge badge--backup">{{ t('providerUsage.backup') }}</span>
            </div>
          </header>

          <div class="account-meta">
            <span v-if="account.externalAccountId" class="meta-chip" :title="account.externalAccountId">
              <svg aria-hidden="true" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="8" r="4" /><path d="M20 21a8 8 0 1 0-16 0" />
              </svg>
              {{ account.externalAccountId }}
            </span>
            <span class="meta-chip">{{ authTypeLabel(account.authType) }}</span>
            <span class="meta-chip">{{ t('providerUsage.priority', { priority: account.priority }) }}</span>
          </div>

          <div class="status-row">
            <span class="status-dot" aria-hidden="true"></span>
            <span>{{ statusLabel(account) }}</span>
            <span v-if="alertTone(account) === 'critical'" class="status-alert">{{ t('providerUsage.critical') }}</span>
            <span v-else-if="alertTone(account) === 'warning'" class="status-alert">{{ t('providerUsage.warning') }}</span>
          </div>

          <section class="quota-panel" :class="`quota-panel--${alertTone(account)}`">
            <div class="section-heading">
              <span>{{ t('providerUsage.quota') }}</span>
              <strong v-if="knownQuota(account)">{{ t('providerUsage.quotaUsed', { percent: displayQuota(account) }) }}</strong>
              <strong v-else>{{ t('providerUsage.quotaUnknown') }}</strong>
            </div>

            <template v-if="knownQuota(account)">
              <div
                class="quota-track"
                role="progressbar"
                :aria-label="t('providerUsage.quota')"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-valuenow="quotaPercent(account) ?? 0"
              >
                <span class="quota-fill" :style="{ width: `${quotaPercent(account) ?? 0}%` }"></span>
              </div>
              <div class="quota-footnotes">
                <span>{{ account.quotaResetAt ? t('providerUsage.quotaReset', { time: formatDate(account.quotaResetAt) }) : t('providerUsage.quotaResetUnknown') }}</span>
                <span v-if="account.quotaUpdatedAt">{{ t('providerUsage.quotaUpdated', { time: formatDate(account.quotaUpdatedAt) }) }}</span>
                <span v-if="account.quotaSource">{{ t('providerUsage.quotaSource', { source: account.quotaSource }) }}</span>
              </div>
            </template>
            <p v-else class="quota-unknown-copy">{{ t('providerUsage.quotaUnknownHint') }}</p>
          </section>

          <section class="usage-panel">
            <div class="section-heading">
              <span>{{ t('providerUsage.usage') }}</span>
              <strong>{{ formatNumber(account.totalTokens || 0) }}</strong>
            </div>
            <div class="token-breakdown">
              <span>
                <small>{{ t('providerUsage.promptTokens') }}</small>
                <strong>{{ formatNumber(account.promptTokens || 0) }}</strong>
              </span>
              <span>
                <small>{{ t('providerUsage.completionTokens') }}</small>
                <strong>{{ formatNumber(account.completionTokens || 0) }}</strong>
              </span>
            </div>
            <div class="last-used">
              {{ account.lastUsedAt ? t('providerUsage.lastUsed', { time: formatDate(account.lastUsedAt) }) : t('providerUsage.neverUsed') }}
            </div>
          </section>

          <div v-if="account.lastErrorMessage || account.lastErrorCode" class="account-error" role="status">
            <strong>{{ accountErrorTitle(account) }}</strong>
            <span>{{ accountErrorMessage(account) }}</span>
          </div>

          <footer class="account-actions">
            <div class="priority-actions" :aria-label="t('providerUsage.priority', { priority: account.priority })">
              <button
                class="icon-btn"
                type="button"
                :disabled="!canMoveAccount(account, -1) || isActionBusy(account)"
                :title="t('providerUsage.moveUp')"
                :aria-label="`${t('providerUsage.moveUp')}: ${accountLabel(account)}`"
                @click="moveAccount(account, -1)"
              >
                <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="18 15 12 9 6 15" /></svg>
              </button>
              <button
                class="icon-btn"
                type="button"
                :disabled="!canMoveAccount(account, 1) || isActionBusy(account)"
                :title="t('providerUsage.moveDown')"
                :aria-label="`${t('providerUsage.moveDown')}: ${accountLabel(account)}`"
                @click="moveAccount(account, 1)"
              >
                <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9" /></svg>
              </button>
            </div>
            <div class="management-actions">
              <button class="text-btn" type="button" :disabled="isActionBusy(account)" @click="toggleAccount(account)">
                {{ account.enabled ? t('providerUsage.disable') : t('providerUsage.enable') }}
              </button>
              <button class="text-btn" type="button" :disabled="isActionBusy(account)" @click="openEditDialog(account)">
                {{ t('providerUsage.edit') }}
              </button>
              <button class="text-btn text-btn--danger" type="button" :disabled="isActionBusy(account)" @click="removeAccount(account)">
                {{ t('providerUsage.remove') }}
              </button>
            </div>
          </footer>
        </article>
      </div>
    </template>

    <el-dialog
      v-model="dialogVisible"
      class="provider-account-dialog"
      :title="editingAccount ? t('providerUsage.editTitle') : t('providerUsage.addTitle')"
      width="560px"
      append-to-body
      align-center
      destroy-on-close
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
    >
      <p class="dialog-intro">{{ t('providerUsage.formIntro') }}</p>
      <form class="account-form" @submit.prevent="saveAccount">
        <label class="form-field">
          <span>{{ t('providerUsage.provider') }}</span>
          <el-select
            v-model="form.preset"
            :placeholder="t('providerUsage.providerPlaceholder')"
            :disabled="!!editingAccount"
            size="large"
            @change="applyPreset"
          >
            <el-option v-for="option in credentialOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </label>

        <label v-if="form.authType === 'api_key' || editingAccount" class="form-field">
          <span>{{ t('providerUsage.label') }}</span>
          <el-input v-model="form.label" size="large" :placeholder="t('providerUsage.labelPlaceholder')" maxlength="80" show-word-limit />
        </label>

        <label v-if="form.authType === 'api_key'" class="form-field">
          <span>{{ t('providerUsage.apiKey') }}</span>
          <el-input
            v-model="form.apiKey"
            size="large"
            type="password"
            show-password
            autocomplete="off"
            :placeholder="t('providerUsage.apiKeyPlaceholder')"
          />
          <small v-if="editingAccount">{{ t('providerUsage.apiKeyKeep') }}</small>
        </label>

        <div v-else-if="form.authType === 'oauth'" class="oauth-note">
          <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /><polyline points="9 12 11 14 15 10" /></svg>
          <span>{{ t('providerUsage.oauthHint') }}</span>
        </div>

        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      </form>

      <template #footer>
        <div class="dialog-actions">
          <button class="btn btn--secondary" type="button" :disabled="saving" @click="dialogVisible = false">{{ t('providerUsage.cancel') }}</button>
          <button class="btn btn--primary" type="button" :disabled="saving" @click="saveAccount">
            {{ saving ? t('providerUsage.saving') : editingAccount ? t('providerUsage.save') : form.authType === 'oauth' ? t('providerUsage.oauthLaunch') : t('providerUsage.create') }}
          </button>
        </div>
      </template>
    </el-dialog>

    <DeviceCodeDialog
      :visible="deviceCodeDialog.visible"
      :user-code="deviceCodeDialog.userCode"
      :verification-url="deviceCodeDialog.verificationUrl"
      :verification-url-complete="deviceCodeDialog.verificationUrlComplete"
      :expires-at="deviceCodeDialog.expiresAt"
      @close="closeDeviceCodeDialog"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { providerAccountApi } from '@/api'
import { mcConfirm } from '@/components/common/useConfirm'
import { mcToast } from '@/composables/useMcToast'
import DeviceCodeDialog from '@/views/Settings/Models/modals/DeviceCodeDialog.vue'
import { useProviderOAuth } from '@/views/Settings/Models/composables/useProviderOAuth'
import type { ProviderUsageAccount } from '@/types/providerUsage'
import type { ProviderInfo } from '@/types'
import { getProviderIcon, onProviderIconError } from '@/utils/providerIcons'
import {
  clampedQuotaPercent,
  formatProviderTimestamp,
  hasKnownQuota,
  normalizeProviderUsageAccounts,
  parseProviderTimestamp,
  providerAlertTone,
} from '@/utils/providerUsage'

const { t, locale } = useI18n()

type CredentialPreset = 'openai-oauth' | 'openai-api' | 'claude-api' | 'gemini-api'

interface AccountForm {
  preset: CredentialPreset | ''
  providerId: string
  authType: 'oauth' | 'api_key' | ''
  label: string
  apiKey: string
}

const accounts = ref<ProviderUsageAccount[]>([])
const loading = ref(false)
const loadError = ref('')
const actionBusyIds = ref(new Set<string>())
const dialogVisible = ref(false)
const editingAccount = ref<ProviderUsageAccount | null>(null)
const saving = ref(false)
const formError = ref('')
const form = reactive<AccountForm>({ preset: '', providerId: '', authType: '', label: '', apiKey: '' })
const oauthEditingProvider = ref<ProviderInfo | null>(null)
const oauthProviders = ref<ProviderInfo[]>([])

const {
  handleOAuthLogin,
  deviceCodeDialog,
  closeDeviceCodeDialog,
} = useProviderOAuth({
  editingProvider: oauthEditingProvider,
  providers: oauthProviders,
  loadProviders: loadAccounts,
})

const credentialOptions = computed(() => [
  { value: 'openai-oauth' as const, label: t('providerUsage.credentialTypes.openaiOauth'), providerId: 'openai-chatgpt', authType: 'oauth' as const },
  { value: 'openai-api' as const, label: t('providerUsage.credentialTypes.openaiApi'), providerId: 'openai', authType: 'api_key' as const },
  { value: 'claude-api' as const, label: t('providerUsage.credentialTypes.claudeApi'), providerId: 'anthropic', authType: 'api_key' as const },
  { value: 'gemini-api' as const, label: t('providerUsage.credentialTypes.geminiApi'), providerId: 'gemini', authType: 'api_key' as const },
])

const orderedAccounts = computed(() => [...accounts.value].sort((left, right) => {
  const byProvider = providerLabel(left).localeCompare(providerLabel(right))
  if (byProvider !== 0) return byProvider
  return (left.priority ?? Number.MAX_SAFE_INTEGER) - (right.priority ?? Number.MAX_SAFE_INTEGER)
}))
const activeAccount = computed(() => accounts.value
  .filter((account) => account.active)
  .sort((left, right) => timestamp(right.lastUsedAt) - timestamp(left.lastUsedAt))[0] || null)
const totalTokens = computed(() => accounts.value.reduce((sum, account) => sum + Number(account.totalTokens || 0), 0))
const alertAccounts = computed(() => accounts.value.filter((account) => ['warning', 'critical'].includes(providerAlertTone(account))))

function responsePayload(response: unknown): unknown {
  if (!response || typeof response !== 'object') return response
  const wrapped = response as { data?: unknown }
  return 'data' in wrapped ? wrapped.data : response
}

async function loadAccounts() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await providerAccountApi.summary()
    accounts.value = normalizeProviderUsageAccounts(responsePayload(response))
  } catch (error) {
    loadError.value = error instanceof Error && error.message ? error.message : t('providerUsage.loadFailed')
    mcToast.error(loadError.value)
  } finally {
    loading.value = false
  }
}

function accountLabel(account: ProviderUsageAccount): string {
  return account.label || account.externalAccountId || t('providerUsage.unknownAccount')
}

function providerLabel(account: ProviderUsageAccount): string {
  if (account.providerName) return account.providerName
  const labels: Record<string, string> = {
    'openai-chatgpt': 'OpenAI',
    openai: 'OpenAI',
    'anthropic-claude-code': 'Claude',
    anthropic: 'Claude',
    gemini: 'Gemini',
  }
  return labels[account.providerId] || account.providerId || t('providerUsage.unknownProvider')
}

function authTypeLabel(authType: string): string {
  const normalized = (authType || '').toLowerCase()
  if (normalized === 'oauth') return 'OAuth'
  if (normalized === 'api_key' || normalized === 'apikey') return 'API key'
  return authType || t('providerUsage.unknownStatus')
}

function statusLabel(account: ProviderUsageAccount): string {
  const status = (account.status || '').trim()
  if (!status) return account.enabled ? t('providerUsage.unknownStatus') : t('providerUsage.disabled')
  return status.toLowerCase().replace(/[_-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function formatNumber(value: number): string {
  return Number(value || 0).toLocaleString()
}

function formatDate(value: string | number): string {
  return formatProviderTimestamp(value, locale.value) || t('providerUsage.timeUnavailable')
}

function timestamp(value?: string | number | null): number {
  return parseProviderTimestamp(value)?.getTime() ?? 0
}

function knownQuota(account: ProviderUsageAccount): boolean {
  return hasKnownQuota(account)
}

function quotaPercent(account: ProviderUsageAccount): number | null {
  return clampedQuotaPercent(account)
}

function displayQuota(account: ProviderUsageAccount): string {
  const value = quotaPercent(account)
  return value == null ? '—' : value.toLocaleString(undefined, { maximumFractionDigits: 1 })
}

function alertTone(account: ProviderUsageAccount) {
  return providerAlertTone(account)
}

function isUsageLimitReached(account: ProviderUsageAccount): boolean {
  const detail = `${account.lastErrorCode || ''} ${account.lastErrorMessage || ''}`.toLowerCase()
  return detail.includes('usage_limit_reached') || detail.includes('usage limit has been reached')
}

function accountErrorTitle(account: ProviderUsageAccount): string {
  if (isUsageLimitReached(account)) return t('providerUsage.usageLimitReachedTitle')
  return account.lastErrorCode || t('providerUsage.providerErrorTitle')
}

function accountErrorMessage(account: ProviderUsageAccount): string {
  if (isUsageLimitReached(account)) return t('providerUsage.usageLimitReachedMessage')
  return account.lastErrorMessage || t('providerUsage.providerErrorMessage')
}

function accountKey(account: ProviderUsageAccount): string {
  return String(account.id)
}

function isActionBusy(account: ProviderUsageAccount): boolean {
  return actionBusyIds.value.has(accountKey(account))
}

function markAction(account: ProviderUsageAccount, busy: boolean) {
  const next = new Set(actionBusyIds.value)
  if (busy) next.add(accountKey(account))
  else next.delete(accountKey(account))
  actionBusyIds.value = next
}

function resetForm() {
  Object.assign(form, { preset: '', providerId: '', authType: '', label: '', apiKey: '' })
  formError.value = ''
}

function presetFor(account: ProviderUsageAccount): CredentialPreset {
  const authType = (account.authType || '').toLowerCase()
  if (account.providerId === 'openai-chatgpt' || (account.providerId === 'openai' && authType === 'oauth')) return 'openai-oauth'
  if (account.providerId === 'anthropic') return 'claude-api'
  if (account.providerId === 'gemini') return 'gemini-api'
  return 'openai-api'
}

function openCreateDialog() {
  editingAccount.value = null
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(account: ProviderUsageAccount) {
  editingAccount.value = account
  Object.assign(form, {
    preset: presetFor(account),
    providerId: account.providerId,
    authType: (account.authType || '').toLowerCase() === 'oauth' ? 'oauth' : 'api_key',
    label: account.label || '',
    apiKey: '',
  })
  formError.value = ''
  dialogVisible.value = true
}

function applyPreset(value: CredentialPreset) {
  const selected = credentialOptions.value.find((option) => option.value === value)
  if (!selected) return
  form.providerId = selected.providerId
  form.authType = selected.authType
  form.apiKey = ''
}

async function saveAccount() {
  formError.value = ''
  const label = form.label.trim()
  if (
    !form.providerId ||
    !form.authType ||
    ((form.authType === 'api_key' || editingAccount.value) && !label) ||
    (!editingAccount.value && form.authType === 'api_key' && !form.apiKey.trim())
  ) {
    formError.value = t('providerUsage.required')
    return
  }

  saving.value = true
  try {
    if (!editingAccount.value && form.authType === 'oauth') {
      dialogVisible.value = false
      // Device authorization always waits for a new completed login. The
      // legacy LOCAL status endpoint may already be connected to account A
      // and would otherwise report success before account B finishes login.
      await handleOAuthLogin(form.providerId, true)
      return
    }
    if (editingAccount.value) {
      const payload = {
        label,
        ...(form.apiKey.trim() ? { apiKey: form.apiKey.trim() } : {}),
      }
      await providerAccountApi.update(editingAccount.value.id, payload)
    } else {
      await providerAccountApi.create({
        providerId: form.providerId,
        label,
        apiKey: form.apiKey.trim(),
      })
    }
    dialogVisible.value = false
    mcToast.success(t('providerUsage.saved'))
    await loadAccounts()
  } catch (error) {
    formError.value = error instanceof Error && error.message ? error.message : t('providerUsage.saveFailed')
  } finally {
    saving.value = false
  }
}

async function toggleAccount(account: ProviderUsageAccount) {
  markAction(account, true)
  try {
    if (account.enabled) await providerAccountApi.disable(account.id)
    else await providerAccountApi.enable(account.id)
    await loadAccounts()
  } catch (error) {
    mcToast.error(error instanceof Error && error.message ? error.message : t('providerUsage.actionFailed'))
  } finally {
    markAction(account, false)
  }
}

async function removeAccount(account: ProviderUsageAccount) {
  const confirmed = await mcConfirm({
    title: t('providerUsage.deleteTitle'),
    message: t('providerUsage.deleteConfirm', { label: accountLabel(account) }),
    confirmText: t('providerUsage.remove'),
    cancelText: t('providerUsage.cancel'),
    tone: 'danger',
  })
  if (!confirmed) return

  markAction(account, true)
  try {
    await providerAccountApi.remove(account.id)
    mcToast.success(t('providerUsage.deleted'))
    await loadAccounts()
  } catch (error) {
    mcToast.error(error instanceof Error && error.message ? error.message : t('providerUsage.actionFailed'))
  } finally {
    markAction(account, false)
  }
}

function providerPool(account: ProviderUsageAccount): ProviderUsageAccount[] {
  return accounts.value
    .filter((item) => item.providerId === account.providerId)
    .sort((left, right) => left.priority - right.priority)
}

function canMoveAccount(account: ProviderUsageAccount, offset: -1 | 1): boolean {
  const pool = providerPool(account)
  const index = pool.findIndex((item) => String(item.id) === String(account.id))
  return index >= 0 && index + offset >= 0 && index + offset < pool.length
}

async function moveAccount(moving: ProviderUsageAccount, offset: -1 | 1) {
  const next = providerPool(moving)
  const index = next.findIndex((item) => String(item.id) === String(moving.id))
  const nextIndex = index + offset
  if (index < 0 || nextIndex < 0 || nextIndex >= next.length) return
  ;[next[index], next[nextIndex]] = [next[nextIndex], next[index]]
  markAction(moving, true)
  try {
    await providerAccountApi.reorder(moving.providerId, next.map((account) => account.id))
    const priorities = new Map(next.map((account, priority) => [String(account.id), priority + 1]))
    accounts.value = accounts.value.map((account) =>
      account.providerId === moving.providerId
        ? { ...account, priority: priorities.get(String(account.id)) ?? account.priority }
        : account,
    )
    mcToast.success(t('providerUsage.reordered'))
  } catch (error) {
    mcToast.error(error instanceof Error && error.message ? error.message : t('providerUsage.actionFailed'))
    await loadAccounts()
  } finally {
    markAction(moving, false)
  }
}

onMounted(loadAccounts)
</script>

<style scoped>
.provider-usage-page {
  width: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  color: var(--mc-text-primary);
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.page-lead { min-width: 0; flex: 1 1 420px; }
.page-kicker { color: var(--mc-primary); font-size: 11px; line-height: 1; font-weight: 800; letter-spacing: 0.13em; text-transform: uppercase; margin-bottom: 12px; }
.page-title { margin: 0; font-size: clamp(26px, 4vw, 40px); line-height: 1; letter-spacing: -0.04em; font-weight: 800; }
.page-desc { max-width: 760px; margin: 12px 0 0; color: var(--mc-text-secondary); font-size: 14px; line-height: 1.65; }

.header-actions,
.dialog-actions,
.management-actions,
.priority-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.btn,
.text-btn,
.icon-btn {
  min-height: 40px;
  border: 1px solid var(--mc-border);
  border-radius: 11px;
  background: var(--mc-bg-elevated);
  color: var(--mc-text-primary);
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.btn { display: inline-flex; align-items: center; justify-content: center; gap: 7px; padding: 9px 14px; }
.btn:hover:not(:disabled), .text-btn:hover:not(:disabled), .icon-btn:hover:not(:disabled) { border-color: color-mix(in srgb, var(--mc-primary) 48%, var(--mc-border)); color: var(--mc-primary-hover); }
.btn:active:not(:disabled), .text-btn:active:not(:disabled), .icon-btn:active:not(:disabled) { transform: translateY(1px); }
.btn:focus-visible, .text-btn:focus-visible, .icon-btn:focus-visible { outline: 2px solid var(--mc-primary); outline-offset: 2px; }
.btn:disabled, .text-btn:disabled, .icon-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn--primary { background: var(--mc-primary); border-color: var(--mc-primary); color: #fff; }
.btn--primary:hover:not(:disabled) { background: var(--mc-primary-hover); color: #fff; }
.btn--secondary { background: var(--mc-bg-elevated); }

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  min-width: 0;
  min-height: 112px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 12px;
  padding: 17px;
  border: 1px solid var(--mc-border-light);
  border-radius: 16px;
  background: color-mix(in srgb, var(--mc-bg-elevated) 94%, white 6%);
  box-shadow: 0 12px 30px rgba(128, 84, 60, 0.06);
}

:global(html.dark) .summary-card { background: color-mix(in srgb, var(--mc-bg-elevated) 94%, black 6%); }
.summary-card--active { background: linear-gradient(135deg, color-mix(in srgb, var(--mc-primary-bg) 75%, var(--mc-bg-elevated)), var(--mc-bg-elevated)); }
.summary-card--warning { border-color: color-mix(in srgb, #d98a32 45%, var(--mc-border)); background: color-mix(in srgb, #fff0d8 42%, var(--mc-bg-elevated)); }
.summary-label { color: var(--mc-text-tertiary); font-size: 11px; font-weight: 800; letter-spacing: 0.07em; text-transform: uppercase; }
.summary-value { font-size: clamp(26px, 3vw, 38px); line-height: 1; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.summary-main { color: var(--mc-text-secondary); font-size: 13px; line-height: 1.4; }
.summary-main--account { display: flex; flex-direction: column; min-width: 0; }
.summary-main--account strong, .summary-main--account small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.summary-main--account strong { color: var(--mc-text-primary); font-size: 15px; }
.summary-main--empty { overflow-wrap: anywhere; }
.active-account { display: flex; align-items: center; min-width: 0; gap: 10px; }

.alert-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid color-mix(in srgb, #d98a32 42%, var(--mc-border));
  border-radius: 14px;
  background: color-mix(in srgb, #fff1d8 58%, var(--mc-bg-elevated));
  color: color-mix(in srgb, #9c5818 82%, var(--mc-text-primary));
}

:global(html.dark) .alert-banner { background: color-mix(in srgb, #7f4c18 28%, var(--mc-bg-elevated)); color: #f2bc7c; }
.alert-icon { flex: 0 0 auto; margin-top: 1px; }
.alert-banner > span:last-child { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.alert-banner strong, .alert-banner small { overflow-wrap: anywhere; }
.alert-banner small { color: var(--mc-text-secondary); line-height: 1.5; }

.account-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.account-card {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 17px;
  border: 1px solid var(--mc-border-light);
  border-radius: 18px;
  background: color-mix(in srgb, var(--mc-bg-elevated) 96%, white 4%);
  box-shadow: 0 16px 36px rgba(128, 84, 60, 0.07);
}

:global(html.dark) .account-card { background: color-mix(in srgb, var(--mc-bg-elevated) 95%, black 5%); }
.account-card--active { border-color: color-mix(in srgb, var(--mc-primary) 45%, var(--mc-border)); box-shadow: 0 16px 40px color-mix(in srgb, var(--mc-primary) 12%, transparent); }
.account-card--warning { border-color: color-mix(in srgb, #d98a32 42%, var(--mc-border)); }
.account-card--critical { border-color: color-mix(in srgb, #d6534d 55%, var(--mc-border)); }
.account-card--disabled { opacity: 0.76; }
.account-card__header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; min-width: 0; }
.account-identity { display: flex; align-items: center; gap: 11px; min-width: 0; }
.provider-logo { width: 42px; height: 42px; flex: 0 0 auto; display: inline-flex; align-items: center; justify-content: center; border: 1px solid var(--mc-border-light); border-radius: 12px; background: var(--mc-bg); }
.provider-logo--small { width: 34px; height: 34px; border-radius: 10px; }
.provider-logo img { width: 24px; height: 24px; object-fit: contain; }
.provider-logo--small img { width: 20px; height: 20px; }
.account-heading { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.account-heading strong, .provider-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.account-heading strong { font-size: 16px; line-height: 1.25; }
.provider-name { color: var(--mc-text-tertiary); font-size: 11px; font-weight: 800; letter-spacing: 0.06em; text-transform: uppercase; }
.account-badges { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 6px; flex: 0 0 auto; }
.badge { padding: 5px 8px; border-radius: 999px; font-size: 10px; font-weight: 800; letter-spacing: 0.04em; white-space: nowrap; }
.badge--active { color: var(--mc-primary-hover); background: var(--mc-primary-bg); }
.badge--backup { color: #287061; background: #dff1eb; }
.badge--muted { color: var(--mc-text-tertiary); background: var(--mc-bg-muted); }
:global(html.dark) .badge--backup { color: #8fd0be; background: rgba(57, 129, 110, 0.24); }

.account-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.meta-chip { max-width: 100%; display: inline-flex; align-items: center; gap: 5px; padding: 5px 8px; border: 1px solid var(--mc-border-light); border-radius: 8px; color: var(--mc-text-secondary); background: var(--mc-bg-muted); font-size: 11px; line-height: 1.2; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.status-row { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; color: var(--mc-text-secondary); font-size: 12px; font-weight: 650; }
.status-dot { width: 8px; height: 8px; flex: 0 0 auto; border-radius: 50%; background: var(--mc-text-tertiary); box-shadow: 0 0 0 3px color-mix(in srgb, var(--mc-text-tertiary) 12%, transparent); }
.account-card--healthy .status-dot, .account-card--active .status-dot { background: #3a927a; box-shadow: 0 0 0 3px rgba(58, 146, 122, 0.14); }
.account-card--warning .status-dot { background: #d98a32; box-shadow: 0 0 0 3px rgba(217, 138, 50, 0.15); }
.account-card--critical .status-dot { background: #d6534d; box-shadow: 0 0 0 3px rgba(214, 83, 77, 0.15); }
.status-alert { margin-left: auto; color: inherit; font-size: 11px; font-weight: 800; }
.account-card--warning .status-alert { color: #b66b20; }
.account-card--critical .status-alert { color: #bd3f3a; }

.quota-panel,
.usage-panel {
  min-width: 0;
  padding: 13px;
  border: 1px solid var(--mc-border-light);
  border-radius: 13px;
  background: var(--mc-bg-muted);
}

.quota-panel--warning { border-color: color-mix(in srgb, #d98a32 35%, var(--mc-border)); }
.quota-panel--critical { border-color: color-mix(in srgb, #d6534d 38%, var(--mc-border)); }
.section-heading { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: 6px 12px; color: var(--mc-text-secondary); font-size: 11px; font-weight: 750; }
.section-heading strong { color: var(--mc-text-primary); font-size: 13px; overflow-wrap: anywhere; }
.quota-track { height: 8px; margin-top: 11px; overflow: hidden; border-radius: 999px; background: color-mix(in srgb, var(--mc-border) 72%, transparent); }
.quota-fill { display: block; height: 100%; border-radius: inherit; background: #3a927a; transition: width 0.25s ease; }
.quota-panel--warning .quota-fill { background: #d98a32; }
.quota-panel--critical .quota-fill { background: #d6534d; }
.quota-footnotes { display: flex; flex-wrap: wrap; gap: 4px 12px; margin-top: 9px; color: var(--mc-text-tertiary); font-size: 10px; line-height: 1.4; }
.quota-footnotes span { overflow-wrap: anywhere; }
.quota-unknown-copy { margin: 9px 0 0; color: var(--mc-text-tertiary); font-size: 11px; line-height: 1.5; overflow-wrap: anywhere; }
.token-breakdown { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-top: 10px; }
.token-breakdown > span { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.token-breakdown small { color: var(--mc-text-tertiary); font-size: 10px; }
.token-breakdown strong { font-size: 14px; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.last-used { margin-top: 9px; color: var(--mc-text-tertiary); font-size: 10px; line-height: 1.4; overflow-wrap: anywhere; }
.account-error { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; border: 1px solid color-mix(in srgb, #d6534d 36%, var(--mc-border)); border-radius: 11px; background: color-mix(in srgb, #f7d7d4 28%, var(--mc-bg-elevated)); color: #a33b37; font-size: 11px; line-height: 1.45; overflow-wrap: anywhere; }
:global(html.dark) .account-error { background: rgba(120, 45, 42, 0.2); color: #ee9d98; }

.account-actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-top: auto; padding-top: 2px; }
.icon-btn { width: 40px; min-width: 40px; padding: 0; display: inline-flex; align-items: center; justify-content: center; }
.text-btn { padding: 8px 10px; }
.text-btn--danger { color: #b9433e; }
.text-btn--danger:hover:not(:disabled) { border-color: #d6534d; color: #d6534d; }

.state-panel,
.empty-panel {
  min-height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 28px;
  border: 1px dashed var(--mc-border);
  border-radius: 18px;
  color: var(--mc-text-secondary);
  text-align: center;
}
.state-panel--error { color: #b9433e; }
.spinner { width: 28px; height: 28px; border: 3px solid var(--mc-border); border-top-color: var(--mc-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.empty-icon { width: 58px; height: 58px; display: inline-flex; align-items: center; justify-content: center; border-radius: 18px; color: var(--mc-primary); background: var(--mc-primary-bg); }
.empty-panel h2 { margin: 0; color: var(--mc-text-primary); font-size: 20px; }
.empty-panel p { max-width: 520px; margin: 0 0 4px; font-size: 13px; line-height: 1.6; }

.dialog-intro { margin: -4px 0 18px; color: var(--mc-text-secondary); font-size: 13px; line-height: 1.55; }
.account-form { display: flex; flex-direction: column; gap: 16px; }
.form-field { display: flex; flex-direction: column; gap: 7px; color: var(--mc-text-primary); font-size: 12px; font-weight: 750; }
.form-field small { color: var(--mc-text-tertiary); font-weight: 500; line-height: 1.4; }
.oauth-note { display: flex; align-items: flex-start; gap: 10px; padding: 12px; border: 1px solid color-mix(in srgb, var(--mc-primary) 24%, var(--mc-border)); border-radius: 12px; background: var(--mc-primary-bg); color: var(--mc-text-secondary); font-size: 12px; line-height: 1.5; }
.oauth-note svg { flex: 0 0 auto; color: var(--mc-primary-hover); margin-top: 1px; }
.form-error { margin: 0; padding: 10px 12px; border-radius: 10px; background: color-mix(in srgb, #f5d4d1 54%, var(--mc-bg-elevated)); color: #aa3834; font-size: 12px; line-height: 1.45; }
.dialog-actions { justify-content: flex-end; }

@media (max-width: 1180px) {
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .account-grid { grid-template-columns: 1fr; }
}

@media (max-width: 768px) {
  .provider-usage-page { gap: 16px; }
  .page-header { gap: 14px; }
  .page-lead { flex-basis: 100%; }
  .header-actions { width: 100%; }
  .header-actions .btn { flex: 1 1 160px; }
  .summary-card { min-height: 102px; padding: 14px; }
  .account-card { padding: 14px; border-radius: 15px; }
}

@media (max-width: 480px) {
  .page-title { font-size: 26px; }
  .page-desc { font-size: 13px; }
  .header-actions { flex-direction: column; align-items: stretch; }
  .header-actions .btn { width: 100%; flex-basis: auto; min-height: 44px; }
  .summary-grid { grid-template-columns: 1fr; }
  .summary-card { min-height: 92px; }
  .account-card__header { flex-direction: column; }
  .account-badges { justify-content: flex-start; }
  .token-breakdown { grid-template-columns: 1fr; }
  .account-actions { align-items: stretch; }
  .priority-actions { width: 100%; }
  .priority-actions .icon-btn { flex: 1; min-height: 44px; }
  .management-actions { width: 100%; display: grid; grid-template-columns: 1fr; }
  .management-actions .text-btn { min-height: 44px; width: 100%; }
  .alert-banner { padding: 12px; }
  .empty-panel, .state-panel { min-height: 220px; padding: 20px 12px; }
}

@media (prefers-reduced-motion: reduce) {
  .btn, .text-btn, .icon-btn, .quota-fill { transition: none; }
  .spinner { animation-duration: 1.5s; }
}

:global(.provider-account-dialog) {
  width: min(560px, calc(100vw - 24px)) !important;
  max-height: calc(100dvh - 24px);
  margin: 12px auto !important;
  border-radius: 18px;
  overflow: hidden;
}

:global(.provider-account-dialog .el-dialog__body) {
  max-height: calc(100dvh - 180px);
  overflow-y: auto;
  overscroll-behavior: contain;
}

@media (max-width: 480px) {
  :global(.provider-account-dialog .el-dialog__header),
  :global(.provider-account-dialog .el-dialog__body),
  :global(.provider-account-dialog .el-dialog__footer) { padding-left: 14px; padding-right: 14px; }
  :global(.provider-account-dialog .dialog-actions) { display: grid; grid-template-columns: 1fr; }
  :global(.provider-account-dialog .dialog-actions .btn) { min-height: 44px; width: 100%; }
}
</style>
