import type { PublicStore } from "@/lib/public-store";
import { whatsappText } from "@/lib/public-store";

// MVP block 1b: how the buyer reaches the store. Only the contacts the store chose to show; the
// links open the buyer's own e-mail, WhatsApp or Instagram, never a form of this platform.
export function StoreContacts({ store }: { store: PublicStore }) {
  const { contactEmail, whatsapp, instagram } = store.profile;
  if (!contactEmail && !whatsapp && !instagram) return null;
  return (
    <section className="store-contacts" aria-labelledby="store-contacts-title">
      <h2 id="store-contacts-title">Fale com a loja</h2>
      <ul>
        {whatsapp ? <li><a href={`https://wa.me/${whatsapp}`} rel="noopener noreferrer" target="_blank">WhatsApp {whatsappText(whatsapp)}<span className="visually-hidden"> (abre em nova aba)</span></a></li> : null}
        {contactEmail ? <li><a href={`mailto:${contactEmail}`}>E-mail {contactEmail}</a></li> : null}
        {instagram ? <li><a href={`https://www.instagram.com/${instagram}/`} rel="noopener noreferrer" target="_blank">Instagram @{instagram}<span className="visually-hidden"> (abre em nova aba)</span></a></li> : null}
      </ul>
    </section>
  );
}
