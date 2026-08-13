<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ProviderUsageAccount } from '@/types/providerUsage'
import {
  clampedQuotaPercent,
  formatProviderTimestamp,
  hasKnownQuota,
  providerAlertTone,
} from '@/utils/providerUsage'

const props = defineProps<{ account: ProviderUsageAccount }>()
const emit = defineEmits<{ details: [] }>()
const { t, locale } = useI18n()

const percent = computed(() => clampedQuotaPercent(props.account))
const tone = computed(() => providerAlertTone(props.account))
const normalizedStatus = computed(() =>
  (props.account.status || props.account.quotaStatus || '').toUpperCase())
const isRateLimited = computed(() =>
  normalizedStatus.value === 'RATE_LIMITED' || normalizedStatus.value === 'COOLDOWN')
const isExhausted = computed(() => tone.value === 'critical')

function formatPercent(value: number): string {
  return new Intl.NumberFormat(locale.value, { maximumFractionDigits: 1 }).format(value)
}

const chipLabel = computed(() => {
  if (isExhausted.value) return t('chat.providerQuota.chipExhausted')
  if (isRateLimited.value) return t('chat.providerQuota.chipRateLimited')
  if (hasKnownQuota(props.account) && percent.value != null) {
    return t('chat.providerQuota.chipKnown', { percent: formatPercent(percent.value) })
  }
  return t('chat.providerQuota.chipUnknown')
})

const statusLabel = computed(() => {
  if (isExhausted.value) return t('chat.providerQuota.exhausted')
  if (isRateLimited.value) return t('chat.providerQuota.rateLimited')
  if (percent.value != null) {
    return t('providerUsage.quotaUsed', { percent: formatPercent(percent.value) })
  }
  return t('chat.providerQuota.unavailable')
})

function formatDate(value: string | number | null | undefined): string | null {
  return formatProviderTimestamp(value, locale.value)
}

const resetAt = computed(() => formatDate(props.account.quotaResetAt))
const observedAt = computed(() => formatDate(props.account.quotaUpdatedAt))
</script>

<template>
  <el-popover placement="top-end" trigger="click" :width="286" popper-class="mc-provider-quota-popover">
    <template #reference>
      <button
        class="provider-quota-chip"
        :class="`provider-quota-chip--${tone}`"
        type="button"
        :title="chipLabel"
        :aria-label="chipLabel"
      >
        <span class="provider-quota-chip__dot" aria-hidden="true" />
        <span class="provider-quota-chip__label">{{ chipLabel }}</span>
      </button>
    </template>

    <div class="provider-quota-panel">
      <div class="provider-quota-panel__head">
        <strong>{{ $t('chat.providerQuota.title') }}</strong>
        <span class="provider-quota-panel__status" :class="`provider-quota-panel__status--${tone}`">
          {{ statusLabel }}
        </span>
      </div>
      <div class="provider-quota-panel__account">
        <span>{{ $t('chat.providerQuota.account') }}</span>
        <strong>{{ account.label || account.externalAccountId || $t('providerUsage.unknownAccount') }}</strong>
        <small v-if="account.active">{{ $t('chat.providerQuota.mostRecent') }}</small>
      </div>
      <div v-if="percent != null" class="provider-quota-panel__track" aria-hidden="true">
        <span :style="{ width: `${percent}%` }" />
      </div>
      <dl class="provider-quota-panel__meta">
        <template v-if="resetAt"><dt>{{ $t('chat.providerQuota.reset') }}</dt><dd>{{ resetAt }}</dd></template>
        <template v-if="observedAt"><dt>{{ $t('chat.providerQuota.lastObserved') }}</dt><dd>{{ observedAt }}</dd></template>
      </dl>
      <p>{{ $t('chat.providerQuota.freshnessHint') }}</p>
      <button type="button" class="provider-quota-panel__details" @click="emit('details')">
        {{ $t('chat.providerQuota.details') }}
      </button>
    </div>
  </el-popover>
</template>

<style scoped>
.provider-quota-chip {
  display: inline-flex; align-items: center; min-height: 28px; max-width: min(280px, 100%);
  gap: 7px; padding: 4px 10px; border: 1px solid var(--mc-border, #e5e7eb);
  border-radius: 999px; background: var(--mc-bg-elevated, #fff);
  color: var(--mc-text-secondary, #6b7280); cursor: pointer; font-size: 12px; line-height: 1.3;
}
.provider-quota-chip:hover, .provider-quota-chip:focus-visible { border-color: var(--mc-primary, #b4592d); outline: none; }
.provider-quota-chip__dot { width: 8px; height: 8px; flex: none; border-radius: 50%; background: #94a3b8; }
.provider-quota-chip__label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.provider-quota-chip--healthy .provider-quota-chip__dot { background: #10b981; }
.provider-quota-chip--warning .provider-quota-chip__dot { background: #f59e0b; }
.provider-quota-chip--critical .provider-quota-chip__dot { background: #ef4444; }
.provider-quota-chip--warning { color: #b45309; }
.provider-quota-chip--critical { color: #dc2626; }

.provider-quota-panel { color: var(--mc-text-primary, #111827); font-size: 13px; }
.provider-quota-panel__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.provider-quota-panel__status { color: var(--mc-text-secondary, #6b7280); text-align: right; }
.provider-quota-panel__status--healthy { color: #047857; }
.provider-quota-panel__status--warning { color: #b45309; }
.provider-quota-panel__status--critical { color: #dc2626; }
.provider-quota-panel__account { display: grid; gap: 3px; margin-top: 14px; overflow-wrap: anywhere; }
.provider-quota-panel__account span, .provider-quota-panel__account small { color: var(--mc-text-secondary, #6b7280); }
.provider-quota-panel__track { height: 6px; margin-top: 12px; overflow: hidden; border-radius: 3px; background: var(--mc-border, #e5e7eb); }
.provider-quota-panel__track span { display: block; height: 100%; border-radius: inherit; background: #10b981; }
.provider-quota-panel__meta { display: grid; grid-template-columns: auto 1fr; gap: 5px 10px; margin: 12px 0 0; }
.provider-quota-panel__meta dt { color: var(--mc-text-secondary, #6b7280); }
.provider-quota-panel__meta dd { margin: 0; text-align: right; overflow-wrap: anywhere; }
.provider-quota-panel p { margin: 12px 0 0; color: var(--mc-text-secondary, #6b7280); font-size: 12px; line-height: 1.45; }
.provider-quota-panel__details { min-height: 36px; margin-top: 10px; padding: 0; border: 0; background: transparent; color: var(--mc-primary, #b4592d); cursor: pointer; font: inherit; font-weight: 600; }
@media (max-width: 768px) { .provider-quota-chip, .provider-quota-panel__details { min-height: 44px; } }
:global(.mc-provider-quota-popover) { max-width: calc(100vw - max(24px, env(safe-area-inset-left) + env(safe-area-inset-right))) !important; }
</style>
