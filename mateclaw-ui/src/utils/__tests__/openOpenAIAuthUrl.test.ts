import { afterEach, describe, expect, it, vi } from 'vitest'
import { openOpenAIAuthUrl } from '@/utils/openOpenAIAuthUrl'

afterEach(() => {
  delete window.mateClawAPI
  vi.restoreAllMocks()
})

describe('openOpenAIAuthUrl', () => {
  it('uses the Electron bridge for the OpenAI device sign-in page', async () => {
    const openExternal = vi.fn().mockResolvedValue(undefined)
    window.mateClawAPI = { openExternal } as any

    await expect(openOpenAIAuthUrl('https://auth.openai.com/codex/device?user_code=ABCD')).resolves.toBe(true)
    expect(openExternal).toHaveBeenCalledWith('https://auth.openai.com/codex/device?user_code=ABCD')
  })

  it('falls back to a browser tab outside the Electron shell', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null)

    await expect(openOpenAIAuthUrl('https://auth.openai.com/codex/device')).resolves.toBe(true)
    expect(open).toHaveBeenCalledWith(
      'https://auth.openai.com/codex/device',
      '_blank',
      'noopener,noreferrer'
    )
  })

  it('rejects URLs outside the official OpenAI authentication host', async () => {
    const open = vi.spyOn(window, 'open')

    await expect(openOpenAIAuthUrl('https://example.com/codex/device')).resolves.toBe(false)
    expect(open).not.toHaveBeenCalled()
  })
})
