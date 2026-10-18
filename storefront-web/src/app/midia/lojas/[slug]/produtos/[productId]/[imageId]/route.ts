import { publicImage } from "@/lib/public-store";

export const dynamic = "force-dynamic";

export async function GET(_request: Request, { params }: { params: Promise<{ slug: string; productId: string; imageId: string }> }) {
  const { slug, productId, imageId } = await params;
  return publicImage(slug, productId, imageId);
}
