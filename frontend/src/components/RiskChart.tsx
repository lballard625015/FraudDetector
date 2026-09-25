import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useEffect, useMemo, useState } from 'react'
import { Alert } from '../api'

const defaultRiskValues = [0.18, 0.24, 0.21, 0.47, 0.39, 0.68, 0.74]
const accountRiskValues: Record<string, number[]> = {
  'Account 00482': defaultRiskValues,
  'Account 00117': [0.08, 0.11, 0.14, 0.19, 0.17, 0.23, 0.21],
}

export type RiskRange = '6h' | '1d' | '1w' | '1m' | '1y'

const rangeConfig: Record<RiskRange, { label: string; duration: number; interval: number }> = {
  '6h': { label: 'Last 6 hours', duration: 6 * 60 * 60 * 1000, interval: 60 * 60 * 1000 },
  '1d': { label: 'Last day', duration: 24 * 60 * 60 * 1000, interval: 4 * 60 * 60 * 1000 },
  '1w': { label: 'Last week', duration: 7 * 24 * 60 * 60 * 1000, interval: 24 * 60 * 60 * 1000 },
  '1m': { label: 'Last month', duration: 30 * 24 * 60 * 60 * 1000, interval: 5 * 24 * 60 * 60 * 1000 },
  '1y': { label: 'Last year', duration: 365 * 24 * 60 * 60 * 1000, interval: 60 * 24 * 60 * 60 * 1000 },
}

const formatTime = (timestamp: number) => new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })

function buildPoints(values: number[], now: number, range: RiskRange) {
  const config = rangeConfig[range]
  return values.map((risk, index) => ({ timestamp: now - config.duration + index * (config.duration / (values.length - 1)), risk }))
}

const formatRisk = (value: number) => value.toFixed(2)

export default function RiskChart({ account = 'Account 00482', accountId = '', range = '6h', alerts = [], riskPoints = [] }: { account?: string; accountId?: string; range?: RiskRange; alerts?: Alert[]; riskPoints?: { timestamp: string; risk: number }[] }) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 60_000)
    return () => window.clearInterval(timer)
  }, [])
  const values = accountRiskValues[account] ?? defaultRiskValues
  const observedPoints = useMemo(() => alerts
    .filter((alert) => alert.combinedScore != null)
    .sort((left, right) => +new Date(left.createdAt) - +new Date(right.createdAt))
    .slice(-7)
    .map((alert) => ({ timestamp: +new Date(alert.createdAt), risk: alert.combinedScore ?? 0 })), [alerts])
  const accountPoints = useMemo(() => riskPoints
    .map((point) => ({ timestamp: +new Date(point.timestamp), risk: point.risk }))
    .sort((left, right) => left.timestamp - right.timestamp), [riskPoints])
  const accountSeries = useMemo(() => accountPoints.length === 1
    ? [{ timestamp: accountPoints[0].timestamp - rangeConfig[range].duration, risk: accountPoints[0].risk }, accountPoints[0]]
    : accountPoints, [accountPoints, range])
  const points = useMemo(() => accountSeries.length >= 2 ? accountSeries : observedPoints.length >= 2 ? observedPoints : buildPoints(values, now, range), [accountSeries, observedPoints, values, now, range])
  const timeTicks = useMemo(() => {
    const start = points[0].timestamp
    const end = points[points.length - 1].timestamp
    return [0, 0.25, 0.5, 0.75, 1].map((fraction) => start + (end - start) * fraction)
  }, [points])

  return <div><div className="chart-axis-label"><span>Composite Risk Score</span><span>{account}</span>{accountId && <span>Account {accountId}</span>}<span>{rangeConfig[range].label.replace(/\b\w/g, (letter) => letter.toUpperCase())}</span></div><div className="chart-wrap"><ResponsiveContainer width="100%" height="100%"><LineChart data={points} margin={{ top: 12, right: 18, left: 22, bottom: 52 }}><XAxis dataKey="timestamp" type="number" domain={['dataMin', 'dataMax']} ticks={timeTicks} tickFormatter={formatTime} stroke="#7c8da4" tickLine={false} axisLine={false} tickMargin={16} minTickGap={22} /><YAxis domain={[0, 1]} width={42} tickCount={5} tickFormatter={formatRisk} stroke="#7c8da4" tickLine={false} axisLine={false} tickMargin={12} /><Tooltip labelFormatter={(value) => formatTime(Number(value))} formatter={(value: number) => [formatRisk(value), 'Composite risk']} contentStyle={{ background: '#111113', border: '1px solid rgb(255 255 255 / 12%)', borderRadius: 6 }} /><Line type="monotone" dataKey="risk" stroke="#93c5fd" strokeWidth={2} dot={{ fill: '#93c5fd', r: 3 }} /></LineChart></ResponsiveContainer></div></div>
}
