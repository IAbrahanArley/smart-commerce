"use client";

import { formatBRL } from "@/lib/catalog";
import { type Customer, addMonths, phoneText, splitInstallments } from "@/lib/credit";
import { formatDay } from "@/lib/expenses";
import CustomerPicker from "@/components/customer-picker";

type CreditErrors = { customer?: string; installments?: string; firstDue?: string };

// SC-055 (ADR-0019 C9): the store credit part of the counter sale: the customer (found or
// registered on the spot), 1 to 12 monthly installments and the first due date, with a preview of
// the installments. The backend checks the customer's limit and splits the total the same way.
export default function CounterCredit({ tenantId, busy, total, errors, clear, customer, setCustomer, installments, setInstallments, firstDue, setFirstDue }: {
  tenantId: string; busy: boolean; total: string | null; errors: CreditErrors; clear: (field: keyof CreditErrors) => void;
  customer: Customer | null; setCustomer: (customer: Customer | null) => void;
  installments: number; setInstallments: (count: number) => void; firstDue: string; setFirstDue: (day: string) => void;
}) {
  return <>
          {customer ? <div className="form-field">
            <p className="m-0">Cliente: <strong>{customer.name}</strong> · {phoneText(customer.phone)}</p>
            <button type="button" className="button-secondary" disabled={busy} onClick={() => setCustomer(null)}>Trocar cliente</button>
          </div> : <CustomerPicker tenantId={tenantId} idPrefix="counter-customer" disabled={busy} invalid={Boolean(errors.customer)} describedBy={errors.customer ? "counter-customer-error" : undefined}
            onSelect={chosen => { setCustomer(chosen); clear("customer"); }} />}
          {errors.customer ? <p id="counter-customer-error" className="field-error">{errors.customer}</p> : null}
          <div className="form-field">
            <label htmlFor="counter-installments">Parcelas</label>
            <select id="counter-installments" disabled={busy} value={installments} onChange={event => { setInstallments(Number(event.target.value)); clear("installments"); }}
              aria-invalid={errors.installments ? true : undefined} aria-describedby={errors.installments ? "counter-installments-error" : undefined}>
              {Array.from({ length: 12 }, (_, index) => index + 1).map(count => <option key={count} value={count}>{count === 1 ? "Parcela única" : `${count} parcelas mensais`}</option>)}
            </select>
            {errors.installments ? <p id="counter-installments-error" className="field-error">{errors.installments}</p> : null}
          </div>
          <div className="form-field">
            <label htmlFor="counter-first-due">Vencimento da primeira parcela</label>
            <p id="counter-first-due-help" className="field-help">As seguintes vencem no mesmo dia dos meses seguintes. Se o cliente tiver limite de crédito, a venda é recusada acima dele.</p>
            <input id="counter-first-due" type="date" disabled={busy} value={firstDue} onChange={event => { setFirstDue(event.target.value); clear("firstDue"); }}
              aria-invalid={errors.firstDue ? true : undefined} aria-describedby={["counter-first-due-help", errors.firstDue ? "counter-first-due-error" : null].filter(Boolean).join(" ")} />
            {errors.firstDue ? <p id="counter-first-due-error" className="field-error">{errors.firstDue}</p> : null}
          </div>
          {(() => {
            const parts = total && /^\d{4}-\d{2}-\d{2}$/.test(firstDue) ? splitInstallments(total, installments) : null;
            return parts && installments > 1 ? <ul className="m-0 grid list-none gap-1 p-0 text-sm" aria-label="Prévia das parcelas">
              {parts.map((amount, index) => <li key={index}>{index + 1}ª · {formatDay(addMonths(firstDue, index))} · {formatBRL(amount)}</li>)}
            </ul> : null;
          })()}
  </>;
}
