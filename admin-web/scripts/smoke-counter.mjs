import assert from "node:assert/strict";
import { itemLabel, minus, parseDays, parsePercent, subtotal, withinLimit } from "../src/lib/counter-sales.ts";

// SC-053 (ADR-0019 C6, C8): pure helpers of the counter sales. Imported by smoke.mjs.
const product = (price, available = 5) => ({ variantId: price, name: "Camiseta", options: "", sku: "CAM", price, available });
assert.equal(subtotal([{ product: product("10.00"), quantity: 2 }, { product: product("25.00"), quantity: 1 }]), "45.00");
assert.equal(subtotal([]), "0.00");
assert.equal(minus("45.00", "4.50"), "40.50");
// The employee's limit, like the backend: 10% of 45.00 is 4.50 exactly.
assert.equal(withinLimit("4.50", "45.00", "10.00"), true);
assert.equal(withinLimit("4.51", "45.00", "10.00"), false);
assert.equal(withinLimit("0.00", "45.00", "0.00"), true);
assert.equal(withinLimit("0.01", "45.00", "0.00"), false);
for (const [input, expected] of [["2,5", "2.50"], ["10", "10.00"], ["0", "0.00"], ["99,99%", "99.99"]]) assert.deepEqual(parsePercent(input, 100, false), { percent: expected }, input);
assert.deepEqual(parsePercent("", 100, false), { percent: null });
assert.deepEqual(parsePercent("100", 100, true), { percent: "100.00" });
for (const input of ["100", "-1", "1,234", "abc"]) assert.ok("error" in parsePercent(input, 100, false), input);
assert.deepEqual(parseDays("30"), { days: 30 }); assert.deepEqual(parseDays(""), { days: null });
for (const input of ["121", "-1", "1,5"]) assert.ok("error" in parseDays(input), input);
assert.equal(itemLabel({ name: "Camiseta", options: "Cor: Azul" }), "Camiseta (Cor: Azul)");
assert.equal(itemLabel({ name: "Camiseta", options: "" }), "Camiseta");
