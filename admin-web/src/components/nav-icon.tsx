import { ChartColumn, CreditCard, LayoutDashboard, Package, ReceiptText, Sparkles, Store, Tags, Truck, User, Wallet, Building2 } from "lucide-react";
import type { NavIcon as Name } from "@/lib/navigation";

const ICONS = {
  home: LayoutDashboard, box: Package, tags: Tags, receipt: ReceiptText, store: Store, truck: Truck,
  card: CreditCard, chart: ChartColumn, wallet: Wallet, spark: Sparkles, stores: Building2, user: User,
} as const;

// Decorative: every navigation link also carries its text label.
export default function NavIcon({ name, className }: { name: Name; className?: string }) {
  const Component = ICONS[name];
  return <Component className={className} aria-hidden="true" />;
}
