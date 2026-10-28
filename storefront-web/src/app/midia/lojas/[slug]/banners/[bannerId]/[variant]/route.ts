import { publicBanner } from "@/lib/public-store";

export const dynamic = "force-dynamic";

export async function GET(_request: Request, { params }: { params: Promise<{ slug: string; bannerId: string; variant: string }> }) {
  const { slug, bannerId, variant } = await params;
  return publicBanner(slug, bannerId, variant);
}
