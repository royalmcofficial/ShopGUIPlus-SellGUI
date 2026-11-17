# SellGUI: disabled SellPrice enforcement (RoyalMC)

## Bug and cause

A ShopGUI+ item with `sellPrice: -1` (RoyalMC Nether Wart `drops:20`)
can still be sold through SellGUI. `ShopHandler.findBestSellOffer`
prefers the positive DynaShop dynamic price without first checking
ShopGUI+'s static sell permission. The `getItemSellPrice` and GUI
close fallbacks can also pay for an item without a verified offer.

This is a distinct issue from the pending DynaShop configuration bounds
reconciliation PR, but blocks the same Nether Wart emergency nerf.

## Fix and compatibility notes

- Query `shopItem.getSellPriceForAmount(player, 1)` **first** for each
  matching shop entry; only positive finite prices are eligible.
- For allowed entries, use DynaShop's dynamic price when available.
  When DynaShop knows the entry but has no positive finite price, it
  reports `-1` (do not fall back to the base price).
- SellGUI may pay **only** for an accessible, sell-enabled `SellOffer`.
  The raw `ShopGuiPlusApi.getItemStackPriceSell` fallback is removed
  as it bypasses this policy.
- Preserve Vihara's best-price-across-shops behavior for allowed offers
  and external DynaShop transaction notification on successful sales.
- An unsold item is returned; `inventory_full` is shown only if it
  actually overflows inventory.

**Tradeoff:** entries not found by the explicit ITEM / `isSimilar`
offer matcher are now rejected rather than sold via unvalidated raw
API fallback. Check custom items/hooks during staging.

**Multiple shops:** disabling one entry does NOT globally ban the
material while another matching accessible shop still allows sale.
To temporarily ban Nether Wart completely, disable the sell side
for every shop entry or implement a separate SellGUI denylist.

**Reload requirement:** changes to ShopGUI+ YAML must be loaded by
ShopGUI+ before querying the live ShopItem objects; editing the
file alone cannot update the runtime representation.

## Staging tests (production clone)

- [ ] Set `drops:20.sellPrice=-1`, but retain a **positive stored**
      dynamic sellPrice in DynaShop `prices.json`. Restart or reload
      ShopGUI+ appropriately. Sell one Nether Wart via SellGUI:
      it must be **returned**, player balance unchanged, and no
      DynaShop transaction/price change logged.
- [ ] Repeat when DynaShop is disabled/absent, and with no matching
      ShopGUI+ item. All should be unsellable.
- [ ] Change ShopGUI+ `sellPrice` to a positive value; verify normal
      SellGUI sale, correct dynamic payout, and DynaShop price decay.
- [ ] Test an enabled ShopGUI+ entry with invalid/nonpositive dynamic
      price; it must not fall back to the static positive price.
- [ ] With two shops containing an identical item, disable one and
      enable the other. SellGUI may use only the eligible offer. When
      both are disabled, there must be **no** sale.
- [ ] Test item metadata/variants, custom ShopGUI+ items/hooks,
      currency types, per-shop permission restrictions and mixed
      allowed+disabled item stacks in a single GUI.
- [ ] Return unsold items to player (or drop only when inventory full).
      Confirm no spurious `inventory_full` message.
- [ ] Verify SellGUI GUI/console opening pathways, and normal closing.
- [ ] Verify that ShopGUI+ and SellGUI list/execution prices agree and
      that disabled items never call `notifySale`.

## Production controls and rollout

Before a tested patch is active, `sellPrice: -1` is **not** a reliable
safeguard in the current SellGUI fork. If no selective block exists,
temporarily revoke `sellgui.use` from players / close the selling path,
checking for console-open and existing GUIs. This also disables sales of
otherwise allowed items. Preserve current DynaShop price data.

Only merge and deploy after successful build and staging QA.
Keep Nether Wart disabled until **both** DynaShop bounds and SellGUI
sell-eligibility issues are fixed and validated in production.

Related Notion issues:
- [SellGUI bug](https://app.notion.com/p/419ef567992281bea29ddf35f970886b)
- [DynaShop bounds bug](https://app.notion.com/p/419ef5679922817f8720e83aedf88584)
