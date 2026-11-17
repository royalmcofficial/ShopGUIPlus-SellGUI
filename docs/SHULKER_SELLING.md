# Shulker selling

`/sellgui` now evaluates eligible contents of single shulker boxes using the same validated ShopGUI+ sell offers as loose items. The box itself is never quoted or sold. Unsellable stacks and nested shulkers remain in their slots; item metadata on the box is preserved. A box stack with amount greater than one or without a valid shulker block state is returned unchanged.

Content sales are grouped by economy provider. SellGUI prepares the replacement block state before requesting payment, removes a group's slots only after that provider call returns successfully, and records receipt values from the captured offer. DynaShop receives one aggregated notification per item/offer after all prices for that GUI close have been captured, keeping the existing quote snapshot behavior for repeated loose and boxed items. Provider failures do not trigger automatic retries; the economy API exposes a `void` deposit call, so it cannot distinguish a silent failure from an external credit followed by an exception. Staging with the production provider remains required.

The GUI close callback drains its source slots once before sale processing and returns each remaining or failed stack once. If inventory space is insufficient, overflow is dropped at the location captured during close and the full-inventory warning is shown.

## Staging checklist

- Test mixed sellable and disabled contents, including display names, lore, custom model data and persistent data; confirm unsold contents and box metadata survive serialization/relog.
- Test DynaShop dynamic sell prices, disabled `sellPrice: -1`, multiple economies, and repeated identical items in loose slots and boxes; confirm exact payouts and one aggregated external transaction per item/offer.
- Test provider unavailable, provider exceptions, shutdown/disconnect during close, full inventory, Folia scheduling, nested shulkers and malformed box state; verify no duplicate payment or lost container.
- The provider API is not transactional. Do not automatically retry a sale after an ambiguous provider exception; reconcile balances and contents manually if a provider reports an uncertain outcome.

## Fork comparison notes

The desktop artifact `ShopGUIPlus-SellGUI-1.1.8-SNAPSHOT-all.jar` has SHA-256 `AF0540AD4B056353C548FB04464E7F3869E452313C765AA8B4275CE5F16E69BC`, matching the successful staging artifact from [PR #1 validation](https://github.com/royalmcofficial/ShopGUIPlus-SellGUI/actions/runs/43741665041/job/132895953819). Its Java 25 class version matches the checked staging build; the JAR does not embed a Git commit identifier. There were no independent local JAR changes to merge.

PR #1 remains a separate pending staging/production gate and provides the required finite price checks, DynaShop disabled-sell protection, validated offers and inventory overflow fix. This feature branch is based on its head `0599372890f05e9e5b4d5d66a6e21701572863ea`.

The upstream commits were not blindly merged:

- `ce4b9b79e8aa3fbd0032c4f3ad3ed55d405989f2` changes the overflow warning expression in a way that can clear a prior overflow flag; the fork already uses the accumulating `|=` form.
- `6bd17e7a738f8203ea5a9ce907f045cfeb42ceea` adds component item-name rendering.
- `908c7883a693c579478728d660c990d35c7ca8b1` changes the default trailing-zero option and hover colors.
- `e29b32aa47b233f506d987565b2b4f2be9956088` adds receipt line separators.

These presentation changes are independent of shulker sales; existing configuration defaults remain as in the RoyalMC fork.
