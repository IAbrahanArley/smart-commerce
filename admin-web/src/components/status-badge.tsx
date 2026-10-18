export default function StatusBadge({ tone, children }: { tone: "positive" | "neutral"; children: string }) {
  return <span className={`status-badge status-${tone}`}>{children}</span>;
}
