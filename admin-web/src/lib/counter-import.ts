// SC-054 (ADR-0019 C14): counter sales from a spreadsheet. Pure helpers, free of React and Next
// imports for the smoke test. The backend reads and checks the file; this only shows its answer.

import type { PaymentMethod } from "./counter-sales.ts";

export type ImportStatus = "READY" | "IMPORTED" | "ERROR";
export type ImportItem = { line: number; sku: string; name: string | null; quantity: number; unitPrice: string; discount: string };
export type ImportSale = { lines: number[]; date: string; code: string | null; paymentMethod: PaymentMethod; items: ImportItem[]; total: string; status: ImportStatus; messages: string[] };
export type ImportPreview = {
  itemLines: number; ready: number; alreadyImported: number; withErrors: number; readyTotal: string; needsCashAccount: boolean; fileRefused: boolean;
  problems: { line: number; message: string }[]; sales: ImportSale[];
};
export type ImportResult = { importId: string | null; imported: number; preview: ImportPreview };

export const STATUS_LABELS: Record<ImportStatus, string> = { READY: "Pronta", IMPORTED: "Já importada", ERROR: "Com erro" };
export const MAX_FILE_BYTES = 1_048_576;

// The template the OWNER downloads: Excel in Portuguese opens ";" files with a BOM correctly.
export const TEMPLATE = "﻿data;codigo_venda;sku;quantidade;preco_unitario;desconto;forma_pagamento\r\n"
  + "03/11/2026;V1;CAM-AZ-M;2;59,90;5,00;dinheiro\r\n"
  + "03/11/2026;V1;JEA-1;1;149,00;;dinheiro\r\n"
  + "03/11/2026;;CAM-AZ-M;1;59,90;;pix\r\n"
  + "03/11/2026;;JEA-1;1;149,00;;credito\r\n";

export function linesText(lines: number[]): string {
  return lines.length === 1 ? `Linha ${lines[0]}` : `Linhas ${lines.join(", ")}`;
}

// Refused before sending: the backend would refuse it too, and the OWNER learns at once.
export function fileError(file: { name: string; size: number } | null): string | null {
  if (!file) return "Escolha o arquivo da planilha.";
  if (!/\.csv$/i.test(file.name)) return "Salve a planilha como CSV (no Excel: Salvar como → CSV) e escolha esse arquivo.";
  if (file.size === 0) return "O arquivo está vazio.";
  if (file.size > MAX_FILE_BYTES) return "O arquivo passa de 1 MB. Divida a planilha em partes menores.";
  return null;
}
