type Props = { label: string; value?: number | null; tone: string }

export default function ScoreBar({ label, value, tone }: Props) {
  const score = Math.max(0, Math.min(1, value ?? 0))
  return <div className="score-row"><span>{label}</span><div className="score-track"><i className={tone} style={{ width: `${score * 100}%` }} /></div><strong>{score.toFixed(2)}</strong></div>
}
