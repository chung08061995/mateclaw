const WebSocket = require('ws')

const backendUrl = process.env.MATECLAW_BACKEND_URL
if (!backendUrl) throw new Error('MATECLAW_BACKEND_URL is required')

async function main() {
  const login = await fetch(`${backendUrl}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  }).then(response => response.json())
  const token = login.data?.token
  if (!token) throw new Error('Could not log in with the local default admin account')

  const workspaces = await fetch(`${backendUrl}/api/v1/workspaces`, {
    headers: { authorization: `Bearer ${token}` },
  }).then(response => response.json())
  const workspaceId = String(workspaces.data?.[0]?.id ?? '')
  if (!workspaceId) throw new Error('No workspace is available')

  const target = await fetch('http://127.0.0.1:9226/json/new?about:blank', { method: 'PUT' }).then(r => r.json())
  const ws = new WebSocket(target.webSocketDebuggerUrl)
  let id = 0
  const pending = new Map()
  const events = []
  ws.on('message', raw => {
    const message = JSON.parse(String(raw))
    if (message.id && pending.has(message.id)) {
      pending.get(message.id)(message)
      pending.delete(message.id)
    } else {
      events.push(message.method)
    }
  })
  await new Promise(resolve => ws.once('open', resolve))

  const call = (method, params = {}) => new Promise(resolve => {
    const requestId = ++id
    pending.set(requestId, resolve)
    ws.send(JSON.stringify({ id: requestId, method, params }))
  })

  await call('Page.enable')
  await call('Runtime.enable')
  await call('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 900,
    deviceScaleFactor: 1,
    mobile: false,
  })
  await call('Page.addScriptToEvaluateOnNewDocument', {
    source: `localStorage.setItem('token', ${JSON.stringify(token)}); localStorage.setItem('mc-workspace-id', ${JSON.stringify(workspaceId)}); localStorage.setItem('mateclaw_locale', 'en-US'); localStorage.setItem('mc-onboarding-done', 'true');`,
  })
  await call('Page.navigate', { url: `${backendUrl}/chat` })
  while (!events.includes('Page.loadEventFired')) await new Promise(resolve => setTimeout(resolve, 25))
  await new Promise(resolve => setTimeout(resolve, 2500))
  await call('Runtime.evaluate', {
    expression: `[...document.querySelectorAll('.conv-title span')]
      .find(element => element.textContent.trim() === 'hi')
      ?.closest('.conv-item')?.click()`,
  })
  await new Promise(resolve => setTimeout(resolve, 1500))

  const evaluated = await call('Runtime.evaluate', {
    expression: `JSON.stringify((() => {
      const text = document.body.innerText
      return {
        path: location.pathname,
        hasChatConversation: text.includes('General Assistant') && text.includes('GPT-5.6 Sol') && text.includes('hi'),
        hasQuotaLabel: /Provider quota|Account quota|Quota remaining|Quota unavailable/i.test(text),
        bodyExcerpt: text.slice(-1000),
      }
    })())`,
    returnByValue: true,
  })
  ws.close()
  const result = JSON.parse(evaluated.result.result.value)
  console.log(JSON.stringify(result, null, 2))
  if (!result.hasChatConversation) {
    console.error('FAIL: the chat conversation did not render, so the reproduction is invalid')
    process.exitCode = 2
  } else if (!result.hasQuotaLabel) {
    console.error('FAIL: chat renders the selected OAuth model but no provider-account quota status')
    process.exitCode = 1
  }
}

main().catch(error => {
  console.error(error)
  process.exitCode = 2
})
