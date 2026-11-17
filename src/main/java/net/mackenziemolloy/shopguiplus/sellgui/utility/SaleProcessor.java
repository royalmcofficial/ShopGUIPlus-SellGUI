package net.mackenziemolloy.shopguiplus.sellgui.utility;

import net.mackenziemolloy.shopguiplus.sellgui.SellGUI;
import net.brcdev.shopgui.economy.EconomyType;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SaleLine;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SaleResult;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SellOffer;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Converts validated ShopGUI+ offers into paid sale lines. A payment returning false or throwing
 * leaves the corresponding input untouched; callers must not retry ambiguous provider failures.
 */
public final class SaleProcessor {
    @FunctionalInterface public interface OfferResolver { SellOffer resolve(ItemStack item); }
    @FunctionalInterface public interface Payment { boolean pay(EconomyType economy, double amount); }

    private final OfferResolver offers;
    private final Payment payment;
    private final Consumer<SaleLine> observer;
    private final BiPredicate<ItemStack, SellOffer> eligibility;

    public SaleProcessor(OfferResolver offers, Payment payment, Consumer<SaleLine> observer) {
        this(offers, payment, observer, (item, offer) -> true);
    }

    public SaleProcessor(OfferResolver offers, Payment payment, Consumer<SaleLine> observer,
                         BiPredicate<ItemStack, SellOffer> eligibility) {
        this.offers = Objects.requireNonNull(offers);
        this.payment = Objects.requireNonNull(payment);
        this.observer = observer == null ? line -> { } : observer;
        this.eligibility = Objects.requireNonNull(eligibility);
    }

    public static boolean isShulker(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.equals("SHULKER_BOX") || name.endsWith("_SHULKER_BOX");
    }

    public SaleResult process(ItemStack source) {
        if (source == null || source.getType().isAir()) return new SaleResult(null, List.of());
        if (isShulker(source)) return processShulker(source);
        SellOffer offer = resolve(source);
        if (offer == null) return new SaleResult(source, List.of());
        EconomyType economy = offer.getEconomyType();
        double total = offer.getPricePerItem() * source.getAmount();
        if (economy == null || !Double.isFinite(total) || total <= 0) return new SaleResult(source, List.of());
        try {
            if (!payment.pay(economy, total)) return new SaleResult(source, List.of());
        } catch (RuntimeException | LinkageError ex) {
            SellGUI.getInstance().getLogger().warning("SellGUI payment failed; preserving the item without retry: " + ex.getMessage());
            return new SaleResult(source, List.of());
        }
        SaleLine line = new SaleLine(source, source.getAmount(), total, economy, offer);
        notifyObserver(line);
        return new SaleResult(null, List.of(line));
    }

    private SaleResult processShulker(ItemStack source) {
        if (source.getAmount() != 1 || !(source.getItemMeta() instanceof BlockStateMeta originalMeta)) {
            return new SaleResult(source, List.of());
        }
        try { if (!(originalMeta.getBlockState() instanceof ShulkerBox)) return new SaleResult(source, List.of()); }
        catch (RuntimeException | LinkageError ex) { return new SaleResult(source, List.of()); }

        ItemStack working = source.clone();
        ItemMeta workingRaw = working.getItemMeta();
        if (!(workingRaw instanceof BlockStateMeta workingMeta)) return new SaleResult(source, List.of());
        ShulkerBox workingBox;
        try {
            if (!(workingMeta.getBlockState() instanceof ShulkerBox state)) return new SaleResult(source, List.of());
            workingBox = state;
        } catch (RuntimeException | LinkageError ex) { return new SaleResult(source, List.of()); }

        Map<EconomyType, List<SlotSale>> groups = new EnumMap<>(EconomyType.class);
        for (int slot = 0; slot < workingBox.getInventory().getSize(); slot++) {
            ItemStack content = workingBox.getInventory().getItem(slot);
            if (content == null || content.getType().isAir() || isShulker(content)) continue;
            SellOffer offer = resolve(content);
            if (offer == null || offer.getEconomyType() == null) continue;
            double total = offer.getPricePerItem() * content.getAmount();
            if (!Double.isFinite(total) || total <= 0) continue;
            groups.computeIfAbsent(offer.getEconomyType(), ignored -> new ArrayList<>())
                    .add(new SlotSale(slot, content.clone(), offer, total));
        }

        List<SaleLine> lines = new ArrayList<>();
        for (Map.Entry<EconomyType, List<SlotSale>> entry : groups.entrySet()) {
            EconomyType economy = entry.getKey();
            List<SlotSale> sales = entry.getValue();
            double total = 0;
            for (SlotSale sale : sales) total += sale.totalPrice;
            if (!Double.isFinite(total) || total <= 0) continue;

            // Prepare a complete replacement state before calling a provider that may alter balances.
            ItemStack staged = working.clone();
            ItemMeta stagedRawMeta = staged.getItemMeta();
            if (!(stagedRawMeta instanceof BlockStateMeta stagedMeta)
                    || !(stagedMeta.getBlockState() instanceof ShulkerBox stagedBox)) continue;
            for (SlotSale sale : sales) stagedBox.getInventory().setItem(sale.slot, null);
            if (!stagedBox.update(true, false)) continue;
            stagedMeta.setBlockState(stagedBox);
            if (!staged.setItemMeta(stagedMeta)) continue;

            boolean paid;
            try {
                paid = payment.pay(economy, total);
            } catch (RuntimeException | LinkageError ex) {
                SellGUI.getInstance().getLogger().warning("SellGUI payment failed; preserving this shulker group without retry: " + ex.getMessage());
                continue;
            }
            if (!paid) continue;

            working = staged;
            for (SlotSale sale : sales) {
                SaleLine line = new SaleLine(sale.item, sale.item.getAmount(), sale.totalPrice, economy, sale.offer);
                lines.add(line);
                notifyObserver(line);
            }
        }
        return new SaleResult(working, lines);
    }

    private SellOffer resolve(ItemStack item) {
        try {
            SellOffer offer = offers.resolve(item.clone());
            if (offer == null || offer.getEconomyType() == null
                    || !Double.isFinite(offer.getPricePerItem()) || offer.getPricePerItem() <= 0
                    || !eligibility.test(item.clone(), offer)) return null;
            return offer;
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }

    private void notifyObserver(SaleLine line) {
        try {
            observer.accept(line);
        } catch (RuntimeException | LinkageError ex) {
            SellGUI.getInstance().getLogger().warning("SellGUI sale observer failed after payment: " + ex.getMessage());
        }
    }

    private record SlotSale(int slot, ItemStack item, SellOffer offer, double totalPrice) { }
}


