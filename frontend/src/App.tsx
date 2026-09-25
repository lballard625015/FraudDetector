import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from 'react'
import { Activity, BarChart3, Bell, CheckCircle2, ChevronRight, CircleAlert, FileCheck2, Inbox, LayoutDashboard, Radio, ShieldCheck, X } from 'lucide-react'
import { Account, addCaseNote, Alert, Analyst, AuthResponse, CaseEvent, CaseRecord, DashboardSummary, getAccountRisk, getAccounts, getAlerts, getCases, getDashboardSummary, getTimeline, login, logout, register, saveAuth, storedAuth, updateCaseStatus, verifyCase } from './api'
import RiskChart, { RiskRange } from './components/RiskChart'
import ScoreBar from './components/ScoreBar'
import { connectLiveUpdates } from './websocket'

type View = 'overview' | 'alerts' | 'cases' | 'risk'
type CaseFilter = 'active' | 'resolved' | 'dismissed'
type WorkflowStatus = 'open' | 'investigating' | 'escalated' | 'resolved' | 'dismissed'

const formatTime = (value: string) => new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
const score = (value?: number | null) => `${((value ?? 0) * 100).toFixed(0)}%`
const statusLabel = (status: string) => status.charAt(0).toUpperCase() + status.slice(1)
const humanizeLabel = (value?: string | null) => value ? value.split(/[_\s-]+/).map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(' ') : ''
const isActiveCase = (status: string) => ['open', 'investigating', 'escalated'].includes(status)
const viewFromHash = (hash: string): View => ['overview', 'alerts', 'cases', 'risk'].includes(hash.slice(1)) ? hash.slice(1) as View : 'overview'

export default function App() {
  const [analyst, setAnalyst] = useState<Analyst | null>(() => storedAuth()?.analyst ?? null)
  const [view, setView] = useState<View>(() => viewFromHash(window.location.hash))
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [cases, setCases] = useState<CaseRecord[]>([])
  const [selectedCase, setSelectedCase] = useState<CaseRecord | null>(null)
  const [timeline, setTimeline] = useState<CaseEvent[]>([])
  const [connected, setConnected] = useState(false)
  const [statusFilter, setStatusFilter] = useState('')
  const [caseFilter, setCaseFilter] = useState<CaseFilter>('active')
  const [verifyState, setVerifyState] = useState<{ valid: boolean; message: string } | null>(null)
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const [actionMessage, setActionMessage] = useState('')
  const [actionBusy, setActionBusy] = useState(false)
  const [summary, setSummary] = useState<DashboardSummary>({ signalsFlagged: 0, alertsOpened: 0, casesActive: 0 })

  const refresh = async () => {
    if (!analyst) return
    try {
      const [nextAlerts, nextCases, nextSummary] = await Promise.all([getAlerts(), getCases(), getDashboardSummary()])
      setAlerts(nextAlerts.sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt)))
      setCases(nextCases)
      setSummary(nextSummary)
      if (selectedCase) {
        const current = nextCases.find((item) => item.caseId === selectedCase.caseId)
        if (current) setSelectedCase(current)
      }
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Unable to reach API') }
  }

  useEffect(() => { void refresh() }, [statusFilter, analyst])
  useEffect(() => {
    const handleHistory = () => setView(viewFromHash(window.location.hash))
    window.addEventListener('popstate', handleHistory)
    return () => window.removeEventListener('popstate', handleHistory)
  }, [])
  useEffect(() => {
    if (!analyst) return
    return connectLiveUpdates((incoming) => { setAlerts((current) => [incoming as Alert, ...current]); void refresh() }, () => void refresh(), setConnected)
  }, [analyst])
  useEffect(() => { if (selectedCase) void getTimeline(selectedCase.caseId).then(setTimeline) }, [selectedCase])

  const navigate = (next: View) => { setView(next); window.history.pushState({ view: next }, '', `#${next}`) }
  const openCase = (caseRecord: CaseRecord) => { setSelectedCase(caseRecord); setVerifyState(null); navigate('cases') }
  const openAlert = (alert: Alert) => { const match = cases.find((item) => item.alertId === alert.alertId); if (match) openCase(match); else navigate('alerts') }
  const handleStatus = async (status: WorkflowStatus) => {
    if (!selectedCase || actionBusy) return
    setActionBusy(true)
    setError('')
    try {
      const next = await updateCaseStatus(selectedCase.caseId, status)
      setSelectedCase(next)
      setCases((current) => current.map((item) => item.caseId === next.caseId ? next : item))
      setTimeline(await getTimeline(next.caseId))
      await refresh()
      setActionMessage(`Case ${statusLabel(status).toLowerCase()}. Alert status is now ${statusLabel(status).toLowerCase()}.`)
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Unable to update case') }
    finally { setActionBusy(false) }
  }
  const handleNote = async () => { if (!selectedCase || !note.trim()) return; await addCaseNote(selectedCase.caseId, note.trim()); setNote(''); setTimeline(await getTimeline(selectedCase.caseId)) }
  const handleVerify = async () => { if (selectedCase) setVerifyState(await verifyCase(selectedCase.caseId)) }
  const handleAuthenticated = (auth: AuthResponse) => { saveAuth(auth); setAnalyst(auth.analyst) }
  const handleLogout = async () => { await logout(); setAnalyst(null); setAlerts([]); setCases([]); setSelectedCase(null) }

  const openAlerts = alerts.filter((item) => item.status === 'open').length
  const highRisk = alerts.filter((item) => (item.combinedScore ?? 0) >= .7).length
  const nav = [{ id: 'overview' as View, label: 'Command center', icon: LayoutDashboard }, { id: 'alerts' as View, label: 'Alert queue', icon: Inbox }, { id: 'cases' as View, label: 'Case workspace', icon: ShieldCheck }, { id: 'risk' as View, label: 'Account risk', icon: BarChart3 }]
  const selectedAlert = useMemo(() => {
    const alert = alerts.find((item) => item.alertId === selectedCase?.alertId)
    return alert ? { ...alert, category: humanizeLabel(alert.category), severity: humanizeLabel(alert.severity) } : undefined
  }, [alerts, selectedCase])

  if (!analyst) return <AuthScreen onAuthenticated={handleAuthenticated} />

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark">F</span><div>Fraud Detector<small>ANALYST CONSOLE</small></div></div>
      <nav>{nav.map(({ id, label, icon: Icon }) => <button key={id} className={`nav-button ${view === id ? 'active' : ''}`} onClick={() => navigate(id)}><Icon size={16} />{label}</button>)}</nav>
      <div className="sidebar-footer">Build 0.5.0 / Ops node 01</div>
    </aside>
    <main className="main">
      <header className="topbar"><div><div className="kicker">Transaction integrity / analyst console</div><h1>{view === 'cases' ? 'Case workspace' : view === 'alerts' ? 'Alert queue' : view === 'risk' ? 'Account risk' : 'Command center'}</h1></div><div className="topbar-actions"><div className="status-pill"><span className={`status-dot ${connected ? '' : 'off'}`} />{connected ? 'Live connection' : 'REST only'}</div><div className="user-session"><span>{analyst.displayName}</span><small>{analyst.role}</small></div><button className="action" onClick={() => void handleLogout()}>Sign out</button></div></header>
      {error && <div className="error-banner"><CircleAlert size={15} />{error}<button onClick={() => setError('')}><X size={14} /></button></div>}
      {actionMessage && <div className="success-banner"><CheckCircle2 size={15} />{actionMessage}<button onClick={() => setActionMessage('')}><X size={14} /></button></div>}
      {view === 'overview' && <Overview alerts={alerts} summary={summary} onOpenAlert={openAlert} onViewAlerts={() => navigate('alerts')} />}
      {view === 'alerts' && <AlertQueue alerts={alerts} filter={statusFilter} onFilter={setStatusFilter} onOpenAlert={openAlert} />}
      {view === 'risk' && <RiskView />}
      {view === 'cases' && <CaseWorkspace cases={cases} selectedCase={selectedCase} selectedAlert={selectedAlert} timeline={timeline} verifyState={verifyState} note={note} caseFilter={caseFilter} onFilter={setCaseFilter} actionBusy={actionBusy} onNote={setNote} onAddNote={handleNote} onVerify={handleVerify} onStatus={handleStatus} onSelect={openCase} />}
    </main>
  </div>
}

function AuthScreen({ onAuthenticated }: { onAuthenticated: (auth: AuthResponse) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const auth = mode === 'login' ? await login(email, password) : await register(email, displayName, password)
      onAuthenticated(auth)
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Unable to authenticate') }
    finally { setBusy(false) }
  }

  return <div className="auth-shell"><div className="auth-panel"><div className="brand auth-brand"><span className="brand-mark">F</span><div>Fraud Detector<small>ANALYST CONSOLE</small></div></div><div className="kicker">Secure operations access</div><h1>{mode === 'login' ? 'Welcome back' : 'Create analyst account'}</h1><p className="auth-subtitle">{mode === 'login' ? 'Sign in to review alerts and manage investigations.' : 'Register an analyst identity for this operations console.'}</p><form onSubmit={submit}>{mode === 'register' && <label>Display name<input value={displayName} onChange={(event) => setDisplayName(event.target.value)} required minLength={2} maxLength={80} /></label>}<label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required autoComplete="email" /></label><label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required minLength={8} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></label>{error && <div className="auth-error"><CircleAlert size={14} />{error}</div>}<button className="action primary auth-submit" disabled={busy}>{busy ? 'Authenticating...' : mode === 'login' ? 'Sign in' : 'Register'}</button></form><button className="auth-switch" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}>{mode === 'login' ? 'Need an analyst account? Register' : 'Already registered? Sign in'}</button></div></div>
}

function Overview({ alerts, summary, onOpenAlert, onViewAlerts }: { alerts: Alert[]; summary: DashboardSummary; onOpenAlert: (item: Alert) => void; onViewAlerts: () => void }) {
  return <><div className="flow-header"><div><div className="kicker">How Fraud Detector works</div><h2>From detection to investigation</h2><p>Every transaction is scored first. Only stronger signals enter the analyst queue, and only escalated alerts become cases.</p></div><button className="action" onClick={onViewAlerts}>Review alerts <ChevronRight size={13} /></button></div><div className="funnel" aria-label="Detection funnel"><FunnelStage value={summary.signalsFlagged} label="Signals flagged" description="Raw detections from the scoring pipeline" width="100%" /><FlowHelp title="Scoring and deduplication" summary="How signals become alerts" explanation="Rules, anomaly scores, and graph evidence are combined. Repeated detections for the same transaction and category reuse an existing open alert instead of creating another one." /><FunnelStage value={summary.alertsOpened} label="Alerts awaiting review" description="Open alerts not yet resolved" width="76%" /><FlowHelp title="Analyst escalation" summary="How alerts become cases" explanation="An alert becomes a case when its combined score reaches the configured threshold. Analysts can then investigate, escalate, resolve, or dismiss it." /><FunnelStage value={summary.casesActive} label="Cases active" description="Alerts under active investigation" width="52%" /></div><div className="overview-feed panel"><div className="panel-head"><div><h2><Radio size={16} style={{ verticalAlign: 'middle', marginRight: 7, color: '#79a9b8' }} />Live signal feed</h2><div className="panel-sub">Incoming alerts</div></div><button className="action" onClick={onViewAlerts}>Open queue <ChevronRight size={13} /></button></div><div className="signal-list">{alerts.slice(0, 7).map((alert) => <button className="signal" key={alert.alertId} onClick={() => onOpenAlert(alert)}><span className={`signal-bar ${alert.severity}`} /><div><div className="signal-title">{humanizeLabel(alert.category) || 'Unclassified Signal'}</div><div className="signal-meta">{alert.alertId.slice(0, 12)} / {formatTime(alert.createdAt)}</div></div><div className="signal-score">{score(alert.combinedScore)}</div></button>)}{!alerts.length && <div className="empty">No live signals received.</div>}</div></div></>
}

function FlowHelp({ title, summary, explanation }: { title: string; summary: string; explanation: string }) {
  return <details className="flow-help"><summary><span>{title}</span><small>{summary}</small><span className="flow-caret" aria-hidden="true" /></summary><p>{explanation}</p></details>
}

function FunnelStage({ value, label, description, width }: { value: number; label: string; description: string; width: string }) {
  const tone = label.startsWith('Signals') ? 'signal-stage' : label.startsWith('Alerts') ? 'alert-stage' : 'case-stage'
  const step = label.startsWith('Signals') ? '01' : label.startsWith('Alerts') ? '02' : '03'
  const displayLabel = label === 'Signals flagged' ? 'Signals detected' : label === 'Alerts awaiting review' ? 'Open alerts' : 'Active cases'
  return <div className={`funnel-stage ${tone}`}><div className="funnel-step">{step}</div><div className="funnel-value">{value.toLocaleString()}</div><div><strong>{displayLabel}</strong><p>{description}</p></div></div>
}

function AlertQueue({ alerts, filter, onFilter, onOpenAlert }: { alerts: Alert[]; filter: string; onFilter: (value: string) => void; onOpenAlert: (item: Alert) => void }) {
  const statuses: WorkflowStatus[] = ['open', 'investigating', 'escalated', 'resolved', 'dismissed']
  const counts = statuses.reduce<Record<string, number>>((result, status) => ({ ...result, [status]: alerts.filter((item) => item.status === status).length }), {})
  const visibleAlerts = filter ? alerts.filter((item) => item.status === filter) : alerts
  return <div className="panel"><div className="panel-head"><div><h2>Alert triage queue</h2><div className="panel-sub">Newest first / select a row to investigate</div></div><select className="filter" value={filter} onChange={(event) => onFilter(event.target.value)}><option value="">All statuses</option>{statuses.map((status) => <option key={status} value={status}>{statusLabel(status)}</option>)}</select></div><div className="status-counts">{statuses.map((status) => <button className={`status-count ${filter === status ? 'selected' : ''}`} key={status} onClick={() => onFilter(filter === status ? '' : status)}><span className={`status-badge ${status}`}>{statusLabel(status)}</span><strong>{counts[status]}</strong></button>)}</div><AlertTable alerts={visibleAlerts} onOpenAlert={onOpenAlert} /></div>
}

function AlertTable({ alerts, onOpenAlert }: { alerts: Alert[]; onOpenAlert: (item: Alert) => void }) {
  return <div className="table-wrap"><table className="alert-table"><thead><tr><th>Signal</th><th>Category</th><th>Severity</th><th>Combined</th><th>Status</th><th>Created</th></tr></thead><tbody>{alerts.map((alert) => <tr key={alert.alertId} onClick={() => onOpenAlert(alert)}><td><span className="id-chip" title="Copy signal ID" onClick={(event) => { event.stopPropagation(); void navigator.clipboard?.writeText(alert.alertId) }}>{alert.alertId.slice(0, 8)}</span></td><td><span className="tag">{humanizeLabel(alert.category) || 'Unknown'}</span></td><td><span className={`severity ${alert.severity}`}>{humanizeLabel(alert.severity) || 'Low'}</span></td><td className="score-cell">{score(alert.combinedScore)}</td><td>{statusLabel(alert.status)}</td><td className="mono">{formatTime(alert.createdAt)}</td></tr>)}</tbody></table>{!alerts.length && <div className="empty">No alerts match this filter.</div>}</div>
}

function CaseWorkspace({ cases, selectedCase, selectedAlert, timeline, verifyState, note, caseFilter, onFilter, actionBusy, onNote, onAddNote, onVerify, onStatus, onSelect }: { cases: CaseRecord[]; selectedCase: CaseRecord | null; selectedAlert?: Alert; timeline: CaseEvent[]; verifyState: { valid: boolean; message: string } | null; note: string; caseFilter: CaseFilter; onFilter: (value: CaseFilter) => void; actionBusy: boolean; onNote: (value: string) => void; onAddNote: () => void; onVerify: () => void; onStatus: (status: WorkflowStatus) => void; onSelect: (item: CaseRecord) => void }) {
  const visibleCases = cases.filter((item) => caseFilter === 'active' ? isActiveCase(item.status) : item.status === caseFilter)
  return <div className="case-layout"><div className="panel"><div className="panel-head"><div><h2>Cases</h2><div className="panel-sub">{caseFilter === 'active' ? 'Active investigations' : `${statusLabel(caseFilter)} cases`}</div></div><select className="filter" value={caseFilter} onChange={(event) => onFilter(event.target.value as CaseFilter)}><option value="active">Active</option><option value="resolved">Resolved</option><option value="dismissed">Dismissed</option></select></div><div className="case-list">{visibleCases.map((item) => <button className={`case-item ${selectedCase?.caseId === item.caseId ? 'selected' : ''}`} key={item.caseId} onClick={() => onSelect(item)}><span>{item.caseId.slice(0, 10)}</span><small><span className={`status-badge ${item.status}`}>{statusLabel(item.status)}</span> / {formatTime(item.openedAt)}</small></button>)}{!visibleCases.length && <div className="empty">No {caseFilter} cases.</div>}</div></div><div className="panel detail">{!selectedCase ? <div className="empty">Select a case to open its investigation record.</div> : <><div className="detail-header"><div><div className="kicker">Investigation record</div><h2>{selectedCase.caseId}</h2><div className="detail-id">Alert {selectedCase.alertId}</div><div className="workflow-trail"><span className={selectedCase.status === 'open' ? 'current' : ''}>Open</span><ChevronRight size={12} /><span className={selectedCase.status === 'investigating' ? 'current' : ''}>Investigating</span><ChevronRight size={12} /><span className={selectedCase.status === 'escalated' ? 'current' : ''}>Escalated</span><ChevronRight size={12} /><span className={['resolved', 'dismissed'].includes(selectedCase.status) ? 'current' : ''}>{statusLabel(selectedCase.status)}</span></div></div><div className="actions"><span className={`status-badge large ${selectedCase.status}`}>{statusLabel(selectedCase.status)}</span><button className="action" disabled={actionBusy} onClick={() => onStatus('investigating')}>Investigate</button><button className="action" disabled={actionBusy} onClick={() => onStatus('escalated')}>Escalate</button><button className="action primary" disabled={actionBusy} onClick={() => onStatus('resolved')}>Resolve</button><button className="action" disabled={actionBusy} onClick={() => onStatus('dismissed')}>Dismiss</button></div></div><div className="score-grid"><div className="score-card"><div className="stat-label">Combined risk</div><strong>{score(selectedAlert?.combinedScore)}</strong><ScoreBar label="Rule" value={selectedAlert?.ruleScore} tone="" /><ScoreBar label="ML anomaly" value={selectedAlert?.mlScore} tone="blue" /><ScoreBar label="Graph ring" value={selectedAlert?.graphScore} tone="green" /></div><div className="score-card"><div className="stat-label">Classification</div><strong>{selectedAlert?.category || 'pending'}</strong><div className="signal-meta">Severity {selectedAlert?.severity || 'unknown'} / status {statusLabel(selectedCase.status)}</div></div><div className="score-card"><div className="stat-label">Audit integrity</div><strong>{verifyState ? (verifyState.valid ? 'VALID' : 'FAILED') : 'UNVERIFIED'}</strong><button className="action" onClick={onVerify}><FileCheck2 size={13} /> Verify chain</button>{verifyState && <div className={`verify ${verifyState.valid ? '' : 'invalid'}`}>{verifyState.valid ? <CheckCircle2 size={13} /> : <CircleAlert size={13} />}{verifyState.message}</div>}</div></div><CaseExplanations alert={selectedAlert} /><div className="note-box"><textarea placeholder="Add analyst note to the audit trail..." value={note} onChange={(event) => onNote(event.target.value)} /><button className="action primary" onClick={onAddNote}>Add note</button></div><div className="timeline"><h3>Audit trail / timeline</h3>{timeline.map((event) => <div className="event" key={event.eventId}><span className="event-type">{event.eventType}</span><span className="event-time">{new Date(event.createdAt).toLocaleString()}</span><div className="event-payload">{JSON.stringify(event.payload, null, 2)}</div></div>)}</div></>}</div></div>
}

function CaseExplanations({ alert }: { alert?: Alert }) {
  return <div className="explanation-tabs"><details><summary>Classification explanation</summary><ul><li><strong>{alert?.category || 'Pending classification'}</strong> is the strongest detection category attached to this alert.</li><li><strong>Velocity:</strong> more than five transactions in five minutes.</li><li><strong>Structuring:</strong> at least three transactions between 9,000 and 10,000 within one hour.</li><li><strong>Device / geo change:</strong> activity separated by more than 500 km.</li><li><strong>Mule Ring:</strong> repeated movement of funds through connected accounts; coordinated bursts and device takeover patterns can provide supporting evidence.</li><li><strong>Severity:</strong> the model-assigned risk band, such as Low, Medium, High, or Critical.</li><li><strong>Status:</strong> the analyst workflow state, displayed as Open, Investigating, Escalated, Resolved, or Dismissed.</li></ul></details><details><summary>How risk works</summary><ul><li><strong>Rule score:</strong> deterministic signals such as velocity, structuring, and device / geo changes.</li><li><strong>ML score:</strong> an Isolation Forest anomaly score. It compares the transaction feature pattern with learned normal behavior; more unusual behavior scores higher.</li><li><strong>Graph score:</strong> evidence from connected-account behavior, including cycles and clusters in a rolling one-hour relationship window.</li><li><strong>Combined score:</strong> 40% rule evidence + 35% ML anomaly evidence + 25% graph evidence.</li><li>Scores at or above the configured <strong>0.70 case threshold</strong> create cases for analyst review.</li></ul></details></div>
}

function RiskView() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [account, setAccount] = useState('')
  const [accountQuery, setAccountQuery] = useState('')
  const [range, setRange] = useState<RiskRange>('6h')
  const [riskPoints, setRiskPoints] = useState<{ timestamp: string; risk: number }[]>([])
  const handleAccountQuery = (event: ChangeEvent<HTMLInputElement>) => setAccountQuery(event.target.value)
  const chooseAccount = (selected: Account) => { setAccount(selected.accountId); setAccountQuery(selected.ownerName) }
  useEffect(() => { void getAccounts(accountQuery).then((nextAccounts) => { setAccounts(nextAccounts); if (!account && nextAccounts[0]) { setAccount(nextAccounts[0].accountId); setAccountQuery(nextAccounts[0].ownerName) } }).catch(() => undefined) }, [accountQuery])
  useEffect(() => { if (account) void getAccountRisk(account, range).then((risk) => setRiskPoints(risk.points)).catch(() => setRiskPoints([])) }, [account, range])
  const selectedAccount = accounts.find((item) => item.accountId === account)
  const matchingAccounts = accountQuery.trim() && accountQuery !== selectedAccount?.ownerName ? accounts.slice(0, 6) : []
  return <div className="risk-page"><div className="risk-heading"><div><div className="kicker">Account monitoring</div><h2>Account risk over time</h2><p>Search an account and time window to inspect its composite risk score.</p></div><div className="risk-controls"><div className="account-search"><label className="account-search-label" htmlFor="account-search">Search</label><input id="account-search" className="filter" value={accountQuery} onChange={handleAccountQuery} placeholder="Search by account number or name" aria-label="Search by account number or name" />{matchingAccounts.length > 0 && <div className="account-search-results">{matchingAccounts.map((item) => <button className="account-search-result" key={item.accountId} onClick={() => chooseAccount(item)}><span>{item.ownerName}</span><small>{item.accountId.slice(0, 8)}</small></button>)}</div>}</div><select className="filter" value={range} onChange={(event) => setRange(event.target.value as RiskRange)}><option value="6h">Last 6 hours</option><option value="1d">Last day</option><option value="1w">Last week</option><option value="1m">Last month</option><option value="1y">Last year</option></select></div></div>{account && <><div className="risk-chart-panel"><RiskChart account={selectedAccount?.ownerName || account.slice(0, 8)} accountId={account} range={range} riskPoints={riskPoints} /></div><div className="risk-explanation"><h3>What composite risk means</h3><p>Composite risk is a normalized score from 0 to 1 that summarizes how strongly the available evidence points to suspicious activity for this account and time window.</p><ul><li><strong>0.00-0.29:</strong> lower observed risk; activity is closer to the account baseline.</li><li><strong>0.30-0.69:</strong> elevated risk; review the contributing transactions and signals.</li><li><strong>0.70-1.00:</strong> high risk; the case threshold may be reached when the evidence is corroborated.</li></ul><p>The trend is directional, not a probability of fraud. A rising line means the combined evidence is becoming more concerning; inspect the alert components before taking action.</p></div></>}</div>
}
