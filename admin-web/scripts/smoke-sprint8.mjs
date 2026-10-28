import assert from "node:assert/strict";
import { brandError, compareAtInput, parseBrands, storefrontError } from "../src/lib/brands.ts";
import { cnpjError, contentError, formatCnpj, parseCompany, parsePages } from "../src/lib/content.ts";
import { bannerBody, bannerErrors, EMPTY_BANNER, fromLocalInput, homeError, move, parseBanners, parseSections, sectionError, toLocalInput } from "../src/lib/home-content.ts";
import { installmentsText, parseInstallments } from "../src/lib/payments.ts";
import { categoryError, categoryPath, categorySlugError, parentOptions } from "../src/lib/categories.ts";

// Sprint 8 (SC-041 to SC-043): pure admin helpers, checked before any server starts.
const category = (id, name, parentId, depth) => ({ id, name, slug: id, parentId, depth, position: 0, published: true, products: 2 });
// SC-041: the tree of up to 3 levels, its paths and the parents a category can move under.
const tree = [category("f", "Feminino", null, 1), category("r", "Roupas", "f", 2), category("c", "Camisetas", "r", 3), category("o", "Outlet", null, 1)];
assert.equal(categoryPath(tree, "c"), "Feminino › Roupas › Camisetas");
assert.deepEqual(parentOptions(tree).map(item => item.id), ["f", "r", "o"]);
assert.deepEqual(parentOptions(tree, "r").map(item => item.id), ["f", "o"]);
assert.deepEqual(parentOptions(tree, "f").map(item => item.id), []);
assert.equal(categorySlugError(" Moda-Praia "), null);
assert.match(categorySlugError("moda praia"), /hífens/); assert.match(categorySlugError(""), /Informe/);
assert.match(categoryError(409, "DEPTH"), /3 níveis/); assert.match(categoryError(409, "HAS_CHILDREN"), /subcategorias/);
// SC-042: installment limit (null: Mercado Pago default; anything else unexpected).
assert.equal(parseInstallments({ maxInstallments: null }), null); assert.equal(parseInstallments({ maxInstallments: 6 }), 6);
assert.equal(parseInstallments({ maxInstallments: 13 }), undefined); assert.equal(parseInstallments({ maxInstallments: "6" }), undefined);
assert.equal(installmentsText(1), "somente à vista"); assert.equal(installmentsText(6), "em até 6×");
// SC-043: company details and institutional pages.
assert.equal(cnpjError(""), null); assert.equal(cnpjError("11.222.333/0001-81"), null);
assert.match(cnpjError("11.222.333/0001-82"), /inválido/); assert.match(cnpjError("11111111111111"), /14 números/);
assert.equal(formatCnpj("11222333000181"), "11.222.333/0001-81");
assert.equal(parseCompany({ cnpj: "11222333000181", legalName: null }).cnpj, "11.222.333/0001-81");
assert.equal(parseCompany({ cnpj: 5 }), null);
assert.equal(parsePages([{ kind: "ABOUT", slug: "sobre", title: "Sobre a loja", bodyHtml: "" }]).length, 1);
assert.equal(parsePages([{ kind: "ABOUT", slug: "../x", title: "x", bodyHtml: "" }]), null);
assert.match(contentError(400), /formato/);
// SC-041: brands and the product "Vitrine" card.
assert.deepEqual(parseBrands([{ id: "b-1", name: "Ágil", slug: "agil", products: 1, extra: 1 }]), [{ id: "b-1", name: "Ágil", slug: "agil", products: 1 }]);
assert.equal(parseBrands([{ id: "b-1", name: "Ágil" }]), null);
assert.deepEqual(compareAtInput("", "199.90"), { value: null });
assert.deepEqual(compareAtInput("249,9", "199.90"), { value: "249.90" });
assert.match(compareAtInput("199,90", "199.90").error, /maior/);
assert.match(compareAtInput("0", "199.90").error, /reais/);
assert.match(compareAtInput("abc", "199.90").error, /reais/);
assert.deepEqual(compareAtInput("1000000000000000.00", "999999999999999.99"), { value: "1000000000000000.00" });
assert.match(brandError(409, "NAME_CONFLICT"), /Já existe/); assert.match(storefrontError(409, "SLUG_CONFLICT"), /endereço/);
assert.match(storefrontError(400, "COMPARE_AT_NOT_ABOVE"), /maior/);

// SC-043: banners and sections of the home.
const banner = { id: "b1", position: 0, alt: "Coleção", title: null, linkKind: "CATEGORY", linkTarget: "camisetas", startsAt: null, endsAt: null, desktopImage: true, mobileImage: false };
assert.equal(parseBanners([banner]).length, 1);
assert.equal(parseBanners([{ ...banner, linkKind: "URL" }]), null);
assert.equal(parseSections([{ kind: "CATEGORY", title: "X", categoryId: "c" }]).length, 1);
assert.equal(parseSections([{ kind: "OTHER", title: "X", categoryId: null }]), null);
assert.ok(bannerErrors(EMPTY_BANNER).alt);
assert.ok(bannerErrors({ ...EMPTY_BANNER, alt: "A", linkKind: "PRODUCT", linkTarget: "https://evil.example" }).linkTarget);
assert.ok(bannerErrors({ ...EMPTY_BANNER, alt: "A", startsAt: "2026-12-01T10:00", endsAt: "2026-11-01T10:00" }).endsAt);
assert.deepEqual(bannerErrors({ ...EMPTY_BANNER, alt: "A", linkKind: "SEARCH", linkTarget: "tênis" }), {});
assert.equal(bannerBody({ ...EMPTY_BANNER, alt: " A ", linkKind: "PRODUCT", linkTarget: " Camiseta-Azul " }).linkTarget, "camiseta-azul");
assert.equal(bannerBody({ ...EMPTY_BANNER, alt: "A", linkTarget: "ignorado" }).linkTarget, null);
assert.equal(toLocalInput(fromLocalInput("2026-11-01T10:30")), "2026-11-01T10:30");
assert.equal(sectionError({ kind: "CATEGORY", title: "Camisetas", categoryId: null }), "Escolha a categoria da seção.");
assert.equal(sectionError({ kind: "NEW", title: " ", categoryId: null }), "Dê um título à seção.");
assert.deepEqual(move(["a", "b", "c"], 0, 1), ["b", "a", "c"]);
assert.deepEqual(move(["a", "b"], 0, -1), ["a", "b"]);
assert.match(homeError(409, "BANNER_LIMIT"), /5 banners/);
