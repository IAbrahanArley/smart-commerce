import assert from "node:assert/strict";
import { itemLabel, minus, parseDays, parsePercent, subtotal, withinLimit } from "../src/lib/counter-sales.ts";
import { TEMPLATE, fileError, linesText } from "../src/lib/counter-import.ts";

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

// SC-054 (ADR-0019 C14): the spreadsheet template and the checks before sending the file.
assert.ok(TEMPLATE.startsWith("﻿data;codigo_venda;sku;quantidade;preco_unitario;desconto;forma_pagamento\r\n"));
assert.equal(TEMPLATE.split("\r\n").filter(Boolean).length, 5);
assert.equal(linesText([4]), "Linha 4"); assert.equal(linesText([2, 3]), "Linhas 2, 3");
assert.equal(fileError({ name: "vendas.csv", size: 120 }), null);
assert.equal(fileError({ name: "VENDAS.CSV", size: 120 }), null);
for (const file of [null, { name: "vendas.xlsx", size: 120 }, { name: "vendas.csv", size: 0 }, { name: "vendas.csv", size: 1_048_577 }]) assert.ok(fileError(file), JSON.stringify(file));
