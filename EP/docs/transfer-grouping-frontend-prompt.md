# Payment Transfer — Grouping the Two Legs Into One Line (Front-End Prompt)

A student-to-student payment transfer is stored as **two** finance-transaction rows — an
`EXPENSE` leg on the source student and an `INCOME` leg on the destination student. This is
correct double-entry accounting: each student's own statement must show its side of the move.
The two rows are now linked by a shared `transferGroupId`, so the UI can **collapse them into
a single "Transfer" line** where it makes sense.

This prompt updates the **existing** transaction list/history UI. No API changes are required.

---

## 1. What changed on each transaction

Finance transactions now include a `transferGroupId` field:

- **Transfer legs:** both the `TRF-OUT` (EXPENSE) and `TRF-IN` (INCOME) rows of the same
  transfer carry the **same** `transferGroupId` (the payment-transfer id). Example: rows with
  references `TRF-OUT-...-2` and `TRF-IN-...-2` share `transferGroupId = 2`.
- **Normal transactions:** `transferGroupId` is `null`.

Other fields already present that help: `category === "PAYMENT_TRANSFER"`,
`source === "PAYMENT_TRANSFER"`, references prefixed `TRF-OUT-` / `TRF-IN-`, and descriptions
like "Transferred out to Mary Njoroge (ADM37363) (transfer #2)".

> Legacy note: transfers created before this change have `transferGroupId = null`. For those,
> fall back to grouping by the transfer number parsed from the reference/description (the
> trailing `-N` in `TRF-OUT-...-N` / `TRF-IN-...-N`, or `transfer #N`). New transfers populate
> `transferGroupId` directly.

---

## 2. How to render

Group transactions whose `transferGroupId` is non-null; render one row per group. Everything
else renders individually as today.

Two supported presentations — pick what fits each screen:

- **Global / admin transaction list (both students visible):** show ONE combined row per
  transfer:
  `Transfer • Loise Korir (ADM58177) → Mary Njoroge (ADM37363) • KES 2,000.00 • 25/09/2026`
  Derive source/destination from the two legs: the `EXPENSE`/`TRF-OUT` leg = source (from), the
  `INCOME`/`TRF-IN` leg = destination (to). Amount is the leg amount (both legs are equal).

- **Single-student statement / history:** that student only has ONE of the two legs, so show
  that one row but relabel it as a transfer:
  - On the source student: `Transfer out → Mary Njoroge (ADM37363)`, amount shown as money out.
  - On the destination student: `Transfer in ← Loise Korir (ADM58177)`, amount shown as money in.
  Use the leg's `transactionType` (EXPENSE = out, INCOME = in) and the description for the
  counterparty name.

Keep a subtle "Transfer" badge/icon so users can tell it apart from ordinary payments. Optional:
let the user expand the grouped row to see both underlying legs (ids, references, invoice ids).

---

## 3. Amount / balance display

- Do **not** sum the two legs. A transfer nets to zero across the school; each leg is the same
  amount on a different student. In a combined row show the single transfer amount once.
- On a single-student view, respect the leg direction: EXPENSE reduces that student's paid
  amount (money out), INCOME increases it (money in).

---

## 4. Front-end prompt (paste into your front-end assistant)

> Update the finance **transactions list / history** UI so a student-to-student payment
> transfer shows as a single line instead of two separate rows.
>
> **Data:** each transaction now has a `transferGroupId`. The two legs of one transfer (an
> EXPENSE "TRF-OUT" row on the source student and an INCOME "TRF-IN" row on the destination
> student) share the same non-null `transferGroupId`. Normal transactions have
> `transferGroupId === null`.
>
> **Grouping logic:**
> - Partition the transaction array: items with a non-null `transferGroupId` are grouped by
>   that id; all others stay as individual rows.
> - For legacy rows where `transferGroupId` is null but the reference starts with `TRF-OUT-` or
>   `TRF-IN-`, fall back to grouping by the trailing number in the reference (e.g. the `2` in
>   `TRF-OUT-TXN...-2`).
>
> **Rendering:**
> - In the global/admin list (where both legs are present), render ONE row per group:
>   `Transfer — <source name (adm)> → <dest name (adm)> — KES <amount> — <date>`.
>   Identify source = the EXPENSE/TRF-OUT leg, destination = the INCOME/TRF-IN leg. Use one
>   leg's amount (they are equal); never add the two.
> - In a single-student statement (only one leg is present for that student), render that one
>   row but label it as a transfer: "Transfer out → <counterparty>" for an EXPENSE leg (money
>   out) or "Transfer in ← <counterparty>" for an INCOME leg (money in). Get the counterparty
>   from the row's description.
> - Add a small "Transfer" badge to distinguish these from ordinary payments. Optionally allow
>   expanding a grouped row to reveal the two underlying legs (transaction ids, references,
>   invoice ids).
>
> **Do not** change any API calls — this is purely a presentation change over the existing
> transactions response. Do not sum leg amounts.
