import assert from "node:assert/strict";
import { accountItems, parseCollapsed, searchScore, storeItems } from "../src/lib/navigation.ts";
import { EMPTY_FILTERS, filterProducts, filtersActive, filtersFromQuery, filtersQuery, NO_CATEGORY, resultText } from "../src/lib/product-filters.ts";
import { billingText, CYCLES, monthlyPrice, PLANS, priceText } from "../src/lib/plans.ts";

// Design system Dashtail (phases 1, 3 and 5): pure helpers of the navigation, the global search and the
// product list filters. Imported by smoke.mjs.
// Design system Dashtail: navigation shared by the sidebar and the search; strict, accent-insensitive search.
const navigation = storeItems("t 1", "/lojas/t%201/produtos/abc");
assert.equal(navigation.length, 13);
assert.ok(navigation.every(item => item.href.startsWith("/lojas/t%201")), "Links só do tenant da rota");
assert.deepEqual(navigation.filter(item => item.current).map(item => item.label), ["Produtos"]);
assert.deepEqual(storeItems("t", "/lojas/t").filter(item => item.current).map(item => item.label), ["Visão geral"]);
assert.deepEqual(storeItems("t", "/lojas/t/categorias").filter(item => item.current).map(item => item.label), ["Categorias"]);
assert.deepEqual(storeItems("t", "/lojas/t/conteudo").filter(item => item.current).map(item => item.label), ["Aparência e conteúdo"]);
assert.deepEqual(storeItems("t", "/lojas/t/marcas").filter(item => item.current).map(item => item.label), ["Marcas"]);
assert.deepEqual(accountItems("/conta").filter(item => item.current).map(item => item.href), ["/conta"]);
assert.equal(searchScore("produto Boné de algodão SKU-3", "bone"), 1);
assert.equal(searchScore("Visão geral painel dashboard", "bone"), 0, "Sem correspondência difusa");
assert.equal(searchScore("pedido 1042 Maria Silva", "1042 maria"), 1);
assert.equal(searchScore("pedido 1041 João", "1042"), 0);
assert.equal(searchScore("Entrega frete retirada", "  "), 1);
assert.equal(parseCollapsed("true"), true); assert.equal(parseCollapsed(null), false); assert.equal(parseCollapsed("1"), false);
// Design system phase 3: product list filters run on the loaded catalog; prices sort as exact cents.
const sample = [
  { name: "Camiseta Básica", sku: "CAM-1", price: "49.90", published: true, categoryId: "c1", variants: [] },
  { name: "Boné", sku: "BON-1", price: "9.99", published: false, categoryId: null, variants: [] },
  { name: "Caneca", sku: "CAN-1", price: "100.00", published: true, categoryId: "c2", variants: [{ sku: "CAN-AZUL" }] },
];
const names = (filters) => filterProducts(sample, { ...EMPTY_FILTERS, ...filters }).map(item => item.name);
assert.deepEqual(names({}), ["Camiseta Básica", "Boné", "Caneca"]);
assert.deepEqual(names({ query: "bone" }), ["Boné"]);
assert.deepEqual(names({ query: "azul" }), ["Caneca"], "Busca também pelo SKU da combinação");
assert.deepEqual(names({ query: "camiseta basica" }), ["Camiseta Básica"]);
assert.deepEqual(names({ visibility: "hidden" }), ["Boné"]);
assert.deepEqual(names({ category: "c2" }), ["Caneca"]);
assert.deepEqual(names({ category: NO_CATEGORY }), ["Boné"]);
assert.deepEqual(names({ sort: "price-asc" }), ["Boné", "Camiseta Básica", "Caneca"], "100.00 vem depois de 49.90");
assert.deepEqual(names({ sort: "price-desc" }), ["Caneca", "Camiseta Básica", "Boné"]);
assert.deepEqual(names({ sort: "name" }), ["Boné", "Camiseta Básica", "Caneca"]);
assert.equal(filtersActive(EMPTY_FILTERS), false); assert.equal(filtersActive({ ...EMPTY_FILTERS, query: " x " }), true);
// Phase 5: filters live in the URL; unexpected values fall back to the defaults.
assert.equal(filtersQuery(EMPTY_FILTERS), "");
assert.equal(filtersQuery({ query: " boné ", category: NO_CATEGORY, visibility: "hidden", sort: "price-asc" }), "?busca=bon%C3%A9&categoria=__none__&vitrine=hidden&ordem=price-asc");
assert.deepEqual(filtersFromQuery({ busca: "boné", categoria: "__none__", vitrine: "hidden", ordem: "price-asc" }), { query: "boné", category: NO_CATEGORY, visibility: "hidden", sort: "price-asc" });
assert.deepEqual(filtersFromQuery({ categoria: "<script>", vitrine: "todos", ordem: "DROP" }), EMPTY_FILTERS);
assert.equal(filtersFromQuery({ busca: "x".repeat(500) }).query.length, 200);
assert.equal(resultText(3, 3), "3 produtos"); assert.equal(resultText(1, 1), "1 produto"); assert.equal(resultText(1, 3), "1 de 3 produtos");

// SaaS plans (2026-10-26): every cycle total divides into whole reais per month and never exceeds the
// approved discount; the landing shows the per-month price and what is charged per cycle.
for (const plan of PLANS) for (const cycle of CYCLES) {
  const total = plan.prices[cycle.id];
  assert.ok(Number.isInteger(total) && Number.isInteger(monthlyPrice(plan, cycle.id)), `${plan.id} ${cycle.id}: reais inteiros`);
  assert.ok(total <= Math.floor(plan.prices.monthly * cycle.months * (100 - cycle.discount) / 100), `${plan.id} ${cycle.id}: desconto aprovado`);
  assert.ok(total >= plan.prices.monthly * cycle.months * (100 - cycle.discount - 1) / 100, `${plan.id} ${cycle.id}: sem desconto além do aprovado`);
}
assert.deepEqual(PLANS.map(plan => plan.prices.monthly), [59, 129, 299]);
assert.deepEqual(PLANS.map(plan => plan.stores), [1, 2, 5]);
assert.equal(billingText(PLANS[1], "monthly"), "cobrado todo mês");
assert.equal(billingText(PLANS[1], "annual").replace(/\s/g, " "), "R$ 1.236 a cada 12 meses");
assert.equal(priceText(monthlyPrice(PLANS[0], "annual")).replace(/\s/g, " "), "R$ 47");
