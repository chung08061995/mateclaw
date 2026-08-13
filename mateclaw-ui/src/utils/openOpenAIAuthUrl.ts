const OPENAI_AUTH_HOST = 'auth.openai.com'

/**
 * Opens an OpenAI authentication URL outside MateClaw.
 *
 * Electron's preload bridge is preferred because this function is usually called
 * after an async device-code request, when a normal browser popup may be blocked.
 */
export async function openOpenAIAuthUrl(rawUrl: string): Promise<boolean> {
  let url: URL
  try {
    url = new URL(rawUrl)
  } catch {
    return false
  }

  if (url.protocol !== 'https:' || url.hostname !== OPENAI_AUTH_HOST) {
    return false
  }

  try {
    if (window.mateClawAPI?.openExternal) {
      await window.mateClawAPI.openExternal(url.toString())
      return true
    }

    window.open(url.toString(), '_blank', 'noopener,noreferrer')
    return true
  } catch {
    return false
  }
}
