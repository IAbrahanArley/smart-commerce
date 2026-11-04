import { Award, CalendarClock, Calculator, ChartColumn, Palette, CreditCard, LayoutDashboard, Landmark, Package, ReceiptText, Sparkles, Store, Tags, Truck, User, Users, Wallet, Building2 } from "lucide-react";
import type { NavIcon as Name } from "@/lib/navigation";

const ICONS = {
  home: LayoutDashboard, box: Package, tags: Tags, award: Award, palette: Palette, receipt: ReceiptText, store: Store, truck: Truck,
  card: CreditCard, chart: ChartColumn, wallet: Wallet, spark: Sparkles, stores: Building2, user: User, users: Users, landmark: Landmark, bills: CalendarClock, register: Calculator,
} as const;

// Decorative: every navigation link also carries its text label.
export default function NavIcon({ name, className }: { name: Name; className?: string }) {
  const Component = ICONS[name];
  return <Component className={className} aria-hidden="true" />;
}
