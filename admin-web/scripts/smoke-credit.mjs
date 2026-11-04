import assert from "node:assert/strict";
import { addMonths, filterFromSlug, firstDueError, installmentText, nameError, phoneDigits, phoneText, splitInstallments, todayInStore, withinOwed } from "../src/lib/credit.ts";

// SC-055 (ADR-0019 C9): pure helpers of the store credit. Imported by smoke.mjs.
// Installments like Credit.split: equal cents, the last one absorbing the rest.
assert.deepEqual(splitInstallments("100.00", 3), ["33.33", "33.33", "33.34"]);
assert.deepEqual(splitInstallments("10.00", 1), ["10.00"]);
assert.deepEqual(splitInstallments("0.12", 12), Array(12).fill("0.01"));
assert.equal(splitInstallments("0.11", 12), null);
// Monthly due dates like LocalDate.plusMonths.
assert.equal(addMonths("2026-01-31", 1), "2026-02-28");
assert.equal(addMonths("2028-01-31", 1), "2028-02-29");
assert.equal(addMonths("2026-11-15", 2), "2027-01-15");
assert.equal(todayInStore(new Date("2026-11-05T02:00:00Z")), "2026-11-04");
assert.equal(firstDueError("2026-11-04", "2026-11-04"), null);
assert.equal(firstDueError("2027-11-04", "2026-11-04"), null);
for (const day of ["2026-11-03", "2027-11-05", "2026-02-30", ""]) assert.ok(firstDueError(day, "2026-11-04"), day);
assert.equal(phoneDigits("(11) 98765-4321"), "11987654321");
assert.equal(phoneDigits("+55 11 98765-4321"), "+5511987654321");
for (const phone of ["1234", "11 9876-543a", ""]) assert.equal(phoneDigits(phone), null, phone);
assert.equal(phoneText("11987654321"), "(11) 98765-4321");
assert.equal(phoneText("1133334444"), "(11) 3333-4444");
assert.equal(phoneText("•••• 4321"), "•••• 4321");
assert.equal(nameError("Maria"), null);
for (const name of ["M", " ", "a\nb"]) assert.ok(nameError(name), JSON.stringify(name));
assert.equal(installmentText({ number: 1, count: 1 }), "Parcela única");
assert.equal(installmentText({ number: 2, count: 3 }), "Parcela 2 de 3");
assert.equal(withinOwed("60.00", "60.00"), true);
assert.equal(withinOwed("60.01", "60.00"), false);
assert.equal(filterFromSlug("vencidos"), "OVERDUE"); assert.equal(filterFromSlug("x"), "ALL"); assert.equal(filterFromSlug(undefined), "ALL");
