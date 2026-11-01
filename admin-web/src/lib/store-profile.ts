// Store name, public contacts and logo (MVP block 1b, PRD item 10). Pure helpers, free of React and
// Next imports for the smoke test. The backend normalizes and validates again; these checks only
// give the owner an answer before the request.

export type StoreProfile = {
  name: string; contactEmail: string | null; whatsapp: string | null; instagram: string | null;
  hasLogo: boolean; logoWidth?: number | null; logoHeight?: number | null; version: number;
};
export type ProfileFields = { name: string; contactEmail: string; whatsapp: string; instagram: string };
export type ProfileErrors = Partial<Record<keyof ProfileFields, string>>;

export function profileFields(profile: StoreProfile): ProfileFields {
  return {
    name: profile.name,
    contactEmail: profile.contactEmail ?? "",
    whatsapp: profile.whatsapp ? whatsappText(profile.whatsapp) : "",
    instagram: profile.instagram ? `@${profile.instagram}` : "",
  };
}

/** "5511988887777" → "(11) 98888-7777"; anything else is shown as it came. */
export function whatsappText(digits: string): string {
  const match = /^55(\d{2})(\d{4,5})(\d{4})$/.exec(digits);
  return match ? `(${match[1]}) ${match[2]}-${match[3]}` : digits;
}

export function profileErrors(fields: ProfileFields): ProfileErrors {
  const errors: ProfileErrors = {};
  const name = fields.name.trim();
  if (!name || [...name].length > 120 || /[\p{Cc}]/u.test(name)) errors.name = "Informe o nome da loja, com até 120 caracteres, em uma linha.";
  const email = fields.contactEmail.trim();
  if (email && (email.length > 254 || !/^[^@\s]{1,64}@[^@\s.]+(\.[^@\s.]+)+$/.test(email))) errors.contactEmail = "Informe um e-mail válido, como contato@sualoja.com.br, ou deixe em branco.";
  const phone = fields.whatsapp.trim();
  if (phone) {
    const digits = phone.replace(/\D/g, "");
    const foreign = phone.startsWith("+") && !digits.startsWith("55");
    const valid = /^[0-9+()\s.-]{8,25}$/.test(phone) && !foreign
      && (/^55[1-9]\d{9,10}$/.test(digits) || (!phone.startsWith("+") && /^[1-9]\d{9,10}$/.test(digits)));
    if (!valid) errors.whatsapp = "Informe um número brasileiro com DDD, como (11) 98888-7777, ou deixe em branco.";
  }
  const instagram = fields.instagram.trim();
  if (instagram && !/^[A-Za-z0-9._]{1,30}$/.test(instagramHandle(instagram))) errors.instagram = "Informe o usuário, como @sualoja, ou o link do perfil, ou deixe em branco.";
  return errors;
}

/** "@loja", "loja" or "https://instagram.com/loja/" → "loja". */
export function instagramHandle(value: string): string {
  const url = /^(?:https?:\/\/)?(?:www\.)?instagram\.com\/([A-Za-z0-9._]{1,30})\/?$/i.exec(value.trim());
  if (url) return url[1];
  return value.trim().replace(/^@/, "");
}

export function profilePayload(fields: ProfileFields, version: number) {
  return {
    name: fields.name.trim(),
    contactEmail: fields.contactEmail.trim() || null,
    whatsapp: fields.whatsapp.trim() || null,
    instagram: fields.instagram.trim() || null,
    expectedVersion: version,
  };
}

/** The logo file the backend accepts: JPEG or PNG up to 5 MiB. */
export function logoFileError(file: File | null): string | null {
  if (!file) return "Selecione uma imagem.";
  if (!["image/jpeg", "image/png"].includes(file.type)) return "Use uma imagem JPEG ou PNG.";
  if (file.size === 0 || file.size > 5 * 1024 * 1024) return "Use uma imagem de até 5 MiB.";
  return null;
}
