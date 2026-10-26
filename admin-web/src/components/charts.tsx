"use client";

import { Bar, BarChart, CartesianGrid, Cell, ComposedChart, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatBRL } from "@/lib/catalog";
import type { SalesPoint } from "@/lib/dashboard";
import type { BreakdownRow } from "@/lib/finance";

// Dashtail-style charts (recharts). The SVG is decorative for assistive technology: each chart sits in
// a figure whose caption and the table next to it carry the same figures.
const AXIS = { fontSize: 12, fill: "var(--sc-color-text-secondary)" };
const GRID = "var(--color-border)";
const compact = new Intl.NumberFormat("pt-BR", { notation: "compact", maximumFractionDigits: 1 });

function Box({ title, children }: { title: string; children: React.ReactNode }) {
  return <div className="rounded-md border border-border bg-card px-3 py-2 text-sm shadow-overlay">
    <p className="m-0 font-semibold">{title}</p>
    {children}
  </div>;
}

export function SalesChart({ points }: { points: SalesPoint[] }) {
  return <div className="h-72 w-full" aria-hidden="true">
    <ResponsiveContainer width="100%" height="100%">
      <ComposedChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
        <CartesianGrid vertical={false} stroke={GRID} />
        <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={{ stroke: GRID }} minTickGap={16} />
        <YAxis yAxisId="revenue" tick={AXIS} tickLine={false} axisLine={false} width={56} tickFormatter={value => `R$ ${compact.format(value)}`} />
        <YAxis yAxisId="orders" orientation="right" tick={AXIS} tickLine={false} axisLine={false} width={32} allowDecimals={false} />
        <Tooltip cursor={{ fill: "var(--color-accent)" }} content={({ active, payload }) => {
          const point = active ? payload?.[0]?.payload as SalesPoint | undefined : undefined;
          return point ? <Box title={point.label}>
            <p className="m-0">Receita: {formatBRL(point.revenueText)}</p>
            <p className="m-0">Pedidos pagos: {point.orders}</p>
          </Box> : null;
        }} />
        <Bar yAxisId="revenue" dataKey="revenue" name="Receita" fill="var(--color-primary)" radius={[4, 4, 0, 0]} maxBarSize={40} isAnimationActive={false} />
        <Line yAxisId="orders" dataKey="orders" name="Pedidos pagos" type="monotone" stroke="var(--color-chart-2)" strokeWidth={2} dot={false} isAnimationActive={false} />
      </ComposedChart>
    </ResponsiveContainer>
  </div>;
}

const FILL = { revenue: "var(--color-primary)", deduction: "var(--color-chart-deduction)", result: "var(--color-chart-2)" } as const;

export function MarginChart({ rows }: { rows: BreakdownRow[] }) {
  return <div className="h-72 w-full" aria-hidden="true">
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 16, bottom: 4, left: 8 }}>
        <CartesianGrid horizontal={false} stroke={GRID} />
        <XAxis type="number" tick={AXIS} tickLine={false} axisLine={false} tickFormatter={value => `R$ ${compact.format(value)}`} />
        <YAxis type="category" dataKey="label" tick={AXIS} tickLine={false} axisLine={false} width={150} />
        <Tooltip cursor={{ fill: "var(--color-accent)" }} content={({ active, payload }) => {
          const row = active ? payload?.[0]?.payload as BreakdownRow | undefined : undefined;
          return row ? <Box title={row.label}><p className="m-0">{formatBRL(row.amountText)}</p></Box> : null;
        }} />
        <Bar dataKey="amount" radius={[0, 4, 4, 0]} maxBarSize={28} isAnimationActive={false}>
          {rows.map(row => <Cell key={row.label} fill={FILL[row.kind]} />)}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  </div>;
}
