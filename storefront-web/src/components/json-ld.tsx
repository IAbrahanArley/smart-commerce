import { jsonLd } from "@/lib/seo";

// SC-047: structured data for search engines. jsonLd escapes "<", so no value can close the tag.
export function JsonLd({ data }: { data: unknown }) {
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: jsonLd(data) }} />;
}
