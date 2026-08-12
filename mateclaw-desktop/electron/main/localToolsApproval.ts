import { dialog, BrowserWindow } from 'electron'

// ─── Local tool approval ─────────────────────────────────────────────────────
// High-risk local operations (file write/edit, shell execution) prompt the user
// with a native dialog showing the full operation context before they run. The
// user may tick "don't ask again this session" to temporarily allow matching
// operations — same path for file ops, same command prefix for shell — until the
// app restarts (the cache is in-memory only).

// Cache of approvals the user chose to remember this session.
const sessionAllow = new Set<string>()

export type ApprovalKind = 'write_file' | 'edit_file' | 'execute_shell'

// The cache key scopes "remember": file ops by exact path, shell by command
// prefix (first word + first 40 chars) so re-running the same kind of command
// doesn't re-prompt, but a different command still does.
function cacheKey(kind: ApprovalKind, subject: string): string {
  if (kind === 'execute_shell') {
    const head = subject.trim().split(/\s+/)[0] || ''
    return `shell:${head}:${subject.trim().slice(0, 40)}`
  }
  return `${kind}:${subject}`
}

interface ApprovalRequest {
  kind: ApprovalKind
  // The path (file ops) or command (shell) this approval is scoped to.
  subject: string
  // Human-readable detail shown in the dialog body.
  detail: string
}

export interface ApprovalResult {
  approved: boolean
}

function titleFor(kind: ApprovalKind): string {
  switch (kind) {
    case 'write_file':
      return 'Allow writing to a local file?'
    case 'edit_file':
      return 'Allow editing a local file?'
    case 'execute_shell':
      return 'Allow running a local command?'
  }
}

export async function requestApproval(req: ApprovalRequest): Promise<ApprovalResult> {
  const key = cacheKey(req.kind, req.subject)
  if (sessionAllow.has(key)) return { approved: true }

  const parent = BrowserWindow.getFocusedWindow() ?? BrowserWindow.getAllWindows()[0]
  const options = {
    type: 'warning' as const,
    title: titleFor(req.kind),
    message: titleFor(req.kind),
    detail: `${req.detail}\n\nA remote agent requested this operation. It will run on your computer.`,
    buttons: ['Deny', 'Allow'],
    defaultId: 0,
    cancelId: 0,
    checkboxLabel: "Don't ask again for matching operations in this session",
    checkboxChecked: false,
    noLink: true,
  }

  const result = parent
    ? await dialog.showMessageBox(parent, options)
    : await dialog.showMessageBox(options)

  const approved = result.response === 1
  if (approved && result.checkboxChecked) {
    sessionAllow.add(key)
  }
  return { approved }
}

// Drop all remembered approvals — called when the desktop disconnects/logs out so
// a new session starts from a clean slate.
export function clearApprovalCache(): void {
  sessionAllow.clear()
}
