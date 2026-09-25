# Manual Stock-In (Add Stock Without Procurement) — Design Doc

**Status:** Proposal — for validation before implementation.
**Author:** Kiro
**Scope:** Backend only (a follow-up front-end prompt can be produced once approved).

---

## 1. Goal

Allow an authorised user to **add stock to an inventory item manually**, without going
through the full procurement chain (Supplier → Purchase Order → Delivery Note approval).
The manual add must:

- Increase the item's `currentQuantity`.
- Record a movement in the stock ledger (`inventory_transactions`) so the audit trail
  stays complete and reconcilable.
- Optionally create the inventory item if it does not exist yet (mirroring how delivery-note
  approval auto-creates items).
- Be tenant-scoped and permission-gated.

This is net-new: today stock only increases via `DeliveryNote` approval and only decreases
via `StockRequisition` approval. There is no manual / opening-balance / adjustment path.

---

## 2. How stock works today (context)

- **Stock item:** `InventoryItem` (`inventory_items`) — key fields `itemName`,
  `unitOfMeasure`, `currentQuantity` (Integer), `reorderLevel`, `lastRestockedAt`,
  `createdAt`, `updatedAt`. Unique on `(item_name, unit_of_measure)`. Tenant-scoped.
- **Stock ledger:** `InventoryTransaction` (`inventory_transactions`) records every movement
  with `transactionType` (String: `STOCK_IN` / `STOCK_OUT`), `quantity`, `previousQuantity`,
  `newQuantity`, `referenceType` (String), `referenceId` (Long, **NOT NULL**), `remarks`,
  `createdBy`, `createdAt`. Tenant-scoped.
- **Stock-in path (to mirror):** `InventoryServiceImpl.processDeliveryNoteApproval(...)` —
  looks up the item (auto-creates if missing), `newQty = previous + delivered`, saves the
  item, then writes an `InventoryTransaction` with `transactionType="STOCK_IN"`,
  `referenceType="DELIVERY_NOTE"`, `referenceId=<deliveryNoteId>`.
- **Stock-out path:** `approveRequisition(...)` — `newQty = previous - requested`, writes
  `STOCK_IN`... no, `STOCK_OUT` with `referenceType="STOCK_REQUISITION"`.
- **Conventions:** services return `CustomResponse<?>`; `@Transactional` on mutating
  methods; `@Audit(module=..., action=...)` for audit; current user via `getCurrentUser()`;
  repositories extend `TenantAwareRepository`; `tenant_id` set automatically by the entity
  listener (never set manually).

---

## 3. Proposed change

### 3.1 New endpoint

```
POST /api/v1/inventory/stock-in
```

Secured with `@PreAuthorize("hasPermission(null, 'inventory:stock_in')")`.

**Request body — `ManualStockInRequestDTO`:**

```json
{
  "inventoryItemId": 12,        // optional: target an existing item by id
  "itemName": "Printer Paper",  // used when inventoryItemId is absent (create-or-find)
  "unitOfMeasure": "Ream",      // used with itemName
  "description": "A4 80gsm",    // optional; only applied when creating a new item
  "quantity": 50,               // required, must be > 0
  "reason": "Opening balance count"  // required, stored as the ledger remark
}
```

Resolution rule:
- If `inventoryItemId` is provided → load that item (404 if not found in tenant).
- Else if `itemName` + `unitOfMeasure` provided → find by
  `findByItemNameIgnoreCaseAndUnitOfMeasureIgnoreCase`; **create it if missing**
  (currentQuantity 0), consistent with the delivery-note behaviour.
- Else → 400 "Provide either inventoryItemId or itemName + unitOfMeasure".

**Response:** `CustomResponse<InventoryItemResponseDTO>` (the updated item, echoing the new
`currentQuantity`), status 200.

### 3.2 Service method

Add to `InventoryService`:

```java
CustomResponse<?> addStockManually(ManualStockInRequestDTO request);
```

Implement in `InventoryServiceImpl`, `@Transactional`, annotated
`@Audit(module = "INVENTORY", action = "MANUAL_STOCK_IN")`. Logic:

1. Validate: `quantity != null && quantity > 0` (else 400); `reason` present (else 400).
2. Resolve/create the `InventoryItem` per the rule above.
3. `previousQty = item.getCurrentQuantity()` (treat null as 0).
4. `newQty = previousQty + quantity`.
5. Set `currentQuantity = newQty`, `lastRestockedAt = now`, `updatedAt = now`; save item.
6. Build & save an `InventoryTransaction`:
   - `transactionType = "STOCK_IN"`
   - `quantity`, `previousQuantity`, `newQuantity`
   - `referenceType = "MANUAL_ADJUSTMENT"` (new String value; no enum change needed)
   - `referenceId = item.getId()` (see §4 for the NOT-NULL note)
   - `remarks = reason`
   - `createdBy = getCurrentUser()`, `createdAt = now`
7. Return the updated item as `InventoryItemResponseDTO`.

### 3.3 New permission

Add to `com.EduePoa.EP.Authentication.Enum.Permissions`:

```java
INVENTORY_STOCK_IN("inventory:stock_in", "Manually add stock to inventory items");
```

(If you prefer a broader gate, `INVENTORY_MANAGE("inventory:manage", ...)` could cover this
plus future inventory admin actions. Recommendation: start with the specific
`inventory:stock_in`.)

### 3.4 New DTO

`Procurement/Inventory/Requests/ManualStockInRequestDTO.java` — Lombok `@Data`, fields as in
§3.1. Validation done in-service (matching the Inventory domain's existing style).

---

## 4. Key decision to confirm: `referenceId` is NOT NULL

`InventoryTransaction.referenceId` is `@Column(nullable = false)`. A manual add has no source
document. Options:

- **Option A (recommended, no schema change):** set `referenceId = item.getId()`. The
  `referenceType = "MANUAL_ADJUSTMENT"` already disambiguates it from delivery notes /
  requisitions, and the row still points at something meaningful (the item).
- **Option B:** set `referenceId = 0L` as a sentinel. Simple, but `0` is less meaningful.
- **Option C:** make `referenceId` nullable (schema change + entity change). Cleanest
  semantically, but touches the shared ledger entity and requires a migration.

**Proposed:** Option A. Please confirm.

---

## 5. Behavioural rules & edge cases

- Quantity must be a positive integer; `0` or negative → 400.
- `reason` is required (keeps the ledger auditable). If you want it optional, say so.
- Creating a missing item requires `itemName` + `unitOfMeasure`; `description` optional.
- The whole operation is `@Transactional` — if the ledger write fails, the quantity change
  rolls back, so item and ledger never diverge.
- Tenant isolation is automatic (both entities are tenant-scoped; `tenant_id` set by the
  listener; repositories are tenant-aware). A user can only add stock to their own tenant's
  items.
- Concurrency: two simultaneous manual adds could interleave read-modify-write on
  `currentQuantity`. Current procurement code does not lock either, so this proposal matches
  existing behaviour. If strict correctness is needed later, a `@Lock(PESSIMISTIC_WRITE)`
  find or an atomic `UPDATE ... SET current_quantity = current_quantity + :qty` could be
  added — **out of scope unless you want it.**

---

## 6. Security note (existing gap)

`InventoryController` currently has **no `@PreAuthorize` annotations** — every existing
inventory endpoint is unsecured, unlike the sibling Supplier controllers which gate every
method. This proposal adds `@PreAuthorize` to the **new** endpoint only. Securing the
existing inventory endpoints is a separate hardening task — flag if you want it bundled in.

---

## 7. Files to add / change

**New:**
- `Procurement/Inventory/Requests/ManualStockInRequestDTO.java`

**Changed:**
- `Procurement/Inventory/InventoryService.java` — add `addStockManually(...)`.
- `Procurement/Inventory/InventoryServiceImpl.java` — implement it (mirror
  `processDeliveryNoteApproval`), reuse `getCurrentUser()` and `toItemDTO(...)`.
- `Procurement/Inventory/InventoryController.java` — add `POST /stock-in` with
  `@PreAuthorize`.
- `Authentication/Enum/Permissions.java` — add `INVENTORY_STOCK_IN`.

No new tables. No migration (with Option A). Existing `inventory_transactions` /
`inventory_items` tables are reused.

---

## 8. Example

Request:
```
POST /api/v1/inventory/stock-in
{ "itemName": "Printer Paper", "unitOfMeasure": "Ream", "quantity": 50, "reason": "Opening balance count" }
```

Effect:
- `Printer Paper / Ream` found or created.
- `currentQuantity`: 0 → 50 (or previous + 50).
- Ledger row: `STOCK_IN`, qty 50, previous 0, new 50, `referenceType=MANUAL_ADJUSTMENT`,
  `referenceId=<itemId>`, remarks "Opening balance count", createdBy <user>.

Response `200`:
```json
{
  "message": "Stock added successfully",
  "statusCode": 200,
  "entity": { "id": 12, "itemName": "Printer Paper", "unitOfMeasure": "Ream", "currentQuantity": 50, ... }
}
```

---

## 9. Open questions for you

1. **`referenceId` handling** — confirm Option A (use item id) vs B (0) vs C (make nullable).
2. **`transactionType`** — keep `"STOCK_IN"` (reuses existing type; distinguished by
   `referenceType="MANUAL_ADJUSTMENT"`), or introduce a distinct `"ADJUSTMENT"` type?
3. **Permission** — specific `inventory:stock_in` (recommended) or broader `inventory:manage`?
4. **`reason`** — required (recommended) or optional?
5. **Auto-create missing items** — keep (mirrors delivery-note behaviour) or require the item
   to already exist?
6. **Secure existing inventory endpoints** too, or leave that as a separate task?

Once you confirm these, I'll implement, compile, and verify.
