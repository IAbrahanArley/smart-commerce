"use client";

import Image from "next/image";
import { useState } from "react";
import type { PublicImage } from "@/lib/public-store";

export function imageUrl(slug: string, productId: string, imageId = "primary") {
  return `/midia/lojas/${encodeURIComponent(slug)}/produtos/${encodeURIComponent(productId)}/${encodeURIComponent(imageId)}`;
}

// SC-047: resized by this server's image optimizer for the slot it fills ("sizes"). With the photo
// id the address is immutable and cached for a day; "primary" only where the id is unknown.
export function ProductCover({ slug, productId, imageId, name, sizes = "(max-width: 40rem) 50vw, (max-width: 64rem) 33vw, 25vw" }: {
  slug: string; productId: string; imageId?: string | null; name: string; sizes?: string;
}) {
  const [failed, setFailed] = useState(imageId === null);
  return <div className="product-cover">{failed ? <p className="image-unavailable">Imagem indisponível</p> : <Image src={imageUrl(slug, productId, imageId ?? "primary")} alt={`Foto de ${name}`} width={600} height={600} sizes={sizes} onError={() => setFailed(true)}
    // An error that fires before hydration never reaches onError, so check the settled image on mount.
    ref={node => { if (node?.complete && node.naturalWidth === 0) setFailed(true); }} />}</div>;
}

export default function ProductGallery({ slug, productId, images }: { slug: string; productId: string; images: PublicImage[] }) {
  const [selected, setSelected] = useState(0);
  const [failed, setFailed] = useState<string | null>(null);
  const image = images[selected];
  return <section className="product-gallery" aria-label="Fotos do produto">
    {!image ? <p className="image-unavailable">Este produto ainda não possui fotos.</p> : <>
      <div className="gallery-main">{failed === image.id ? <p className="image-unavailable" role="status">Não foi possível carregar esta foto.</p> : <Image src={imageUrl(slug, productId, image.id)} alt={image.alt} width={image.width} height={image.height} sizes="(max-width: 64rem) 100vw, 50vw" preload onError={() => setFailed(image.id)} />}</div>
      {images.length > 1 ? <div className="gallery-thumbnails" role="group" aria-label="Escolher foto">{images.map((photo, index) => <button key={photo.id} type="button" aria-pressed={selected === index} onClick={() => { setSelected(index); setFailed(null); }} aria-label={`Ver foto ${index + 1}: ${photo.alt}`}><Image src={imageUrl(slug, productId, photo.id)} alt="" width={80} height={80} sizes="80px" /></button>)}</div> : null}
    </>}
  </section>;
}
