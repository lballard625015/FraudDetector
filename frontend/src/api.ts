export type Alert = {
  alertId: string
  transactionId?: string | null
  ruleScore?: number | null
  mlScore?: number | null
  graphScore?: number | null
  combinedScore?: number | null
  category?: string | null
  severity?: string | null
  createdAt: string
  status: string
}

export type CaseRecord = {
  caseId: string
  alertId?: string
  status: string
  priority?: string | null
  escalationReason?: string | null
  escalatedAt?: string | null
  assignedTo?: string | null
  openedAt: string
  closedAt?: string | null
  resolution?: string | null
}

export type CaseEvent = {
  eventId: string
  caseId: string
  eventType: string
  payload: Record<string, unknown>
  previousHash: string
  eventHash: string
  createdAt: string
}

export type DashboardSummary = {
  signalsFlagged: number
  alertsOpened: number
  casesActive: number
}

export type Account = {
  accountId: string
  ownerName: string
  riskScore: number
  status: string
}

export type AccountRisk = {
  accountId: string
  ownerName: string
  currentRisk: number
  points: { timestamp: string; risk: number }[]
}

export type Analyst = {
  analystId: string
  email: string
  displayName: string
  role: string
}

export type AuthResponse = {
  token: string
  analyst: Analyst
  expiresAt: string
}

const authStorageKey = 'fraud-detection-auth'

export const storedAuth = (): AuthResponse | null => {
  const value = localStorage.getItem(authStorageKey)
  if (!value) return null
  try { return JSON.parse(value) as AuthResponse } catch { localStorage.removeItem(authStorageKey); return null }
}

export const saveAuth = (auth: AuthResponse) => localStorage.setItem(authStorageKey, JSON.stringify(auth))
export const clearAuth = () => localStorage.removeItem(authStorageKey)

const json = async <T>(response: Response): Promise<T> => {
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`)
  return response.json() as Promise<T>
}

const request = (input: RequestInfo | URL, init: RequestInit = {}) => {
  const auth = storedAuth()
  const headers = new Headers(init.headers)
  if (auth?.token) headers.set('Authorization', `Bearer ${auth.token}`)
  return fetch(input, { ...init, headers })
}

export const register = (email: string, displayName: string, password: string) =>
  fetch('/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, displayName, password }) }).then((response) => json<AuthResponse>(response))

export const login = (email: string, password: string) =>
  fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) }).then((response) => json<AuthResponse>(response))

export const logout = () => request('/api/auth/logout', { method: 'POST' }).then(() => clearAuth())

export const getAlerts = (status?: string) =>
  request(`/api/alerts${status ? `?status=${encodeURIComponent(status)}` : ''}`).then((response) => json<Alert[]>(response))

export const getDashboardSummary = () =>
  request('/api/dashboard/summary').then((response) => json<DashboardSummary>(response))

export const getAccounts = (query = '') => request(`/api/accounts/search?q=${encodeURIComponent(query)}`).then((response) => json<Account[]>(response))

export const getAccountRisk = (accountId: string, range: string) =>
  request(`/api/accounts/${accountId}/risk?range=${encodeURIComponent(range)}`).then((response) => json<AccountRisk>(response))

export const getCases = () => request('/api/cases').then((response) => json<CaseRecord[]>(response))

export const getTimeline = (caseId: string) =>
  request(`/api/cases/${caseId}/timeline`).then((response) => json<CaseEvent[]>(response))

export const verifyCase = (caseId: string) =>
  request(`/api/cases/${caseId}/verify`).then((response) => json<{ valid: boolean; eventCount: number; message: string }>(response))

export const updateCaseStatus = (caseId: string, status: string) =>
  request(`/api/cases/${caseId}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  }).then((response) => json<CaseRecord>(response))

export const addCaseNote = (caseId: string, note: string) =>
  request(`/api/cases/${caseId}/notes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ note, author: 'analyst' }),
  })
