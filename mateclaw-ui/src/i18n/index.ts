import { createI18n } from 'vue-i18n'
import { compileToFunction, registerMessageCompiler } from '@intlify/core-base'
import { ref } from 'vue'
import { settingsApi } from '@/api'

export type AppLocale = 'en-US'

const STORAGE_KEY = 'mateclaw_locale'
const DEFAULT_LOCALE: AppLocale = 'en-US'

export const currentLocale = ref<AppLocale>(DEFAULT_LOCALE)

// Replace vue-i18n's default message compiler with a safety wrapper. The
// default compiler throws on parse errors in production builds, which
// caused a regression: workflow step prompts containing Pebble syntax
// (`Hello {{ inputs.payload }}`) leaked into i18n's parser through one of
// vue-i18n's internal lookups and aborted the property panel render.
// Catching the throw here keeps the panel alive — the worst case is that
// a malformed message renders as its literal text instead of the
// interpolated form, which is the same fallback dev mode already has.
const safeMessageCompiler = ((message: any, context: any) => {
  try {
    return compileToFunction(message, context)
  } catch {
    const literal = typeof message === 'string' ? message : String(message)
    return () => literal
  }
}) as typeof compileToFunction
registerMessageCompiler(safeMessageCompiler)

export const i18n = createI18n({
  legacy: false,
  locale: DEFAULT_LOCALE,
  fallbackLocale: DEFAULT_LOCALE,
  messages: {} as Record<AppLocale, any>,
  messageCompiler: safeMessageCompiler,
})

const loadedLocales = new Set<AppLocale>()

// This distribution is intentionally English-only. Keep the loader async so
// the initial bundle remains small and the existing initialization contract
// does not change.
async function loadLocaleMessages(locale: AppLocale) {
  if (loadedLocales.has(locale)) return
  const messages = (await import('./locales/en-US')).default
  i18n.global.setLocaleMessage(locale, messages)
  loadedLocales.add(locale)
}

function normalizeLocale(_locale?: string | null): AppLocale {
  return 'en-US'
}

export async function applyLocale(locale?: string | null) {
  const normalized = normalizeLocale(locale)
  // Must finish loading messages before flipping currentLocale, otherwise the
  // first render after a switch would show the i18n keys verbatim.
  await loadLocaleMessages(normalized)
  currentLocale.value = normalized
  i18n.global.locale.value = normalized
  localStorage.setItem(STORAGE_KEY, normalized)
  return normalized
}

export async function initializeLocale() {
  try {
    const res: any = await settingsApi.getLanguage()
    return await applyLocale(res.data)
  } catch {
    return await applyLocale(localStorage.getItem(STORAGE_KEY))
  }
}
