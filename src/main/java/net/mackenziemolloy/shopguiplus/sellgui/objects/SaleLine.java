package net.mackenziemolloy.shopguiplus.sellgui.objects;

import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Immutable record of one item type paid by one validated ShopGUI+ offer. */
public final class SaleLine {
    private final ItemStack item;
    private final int amount;
    private final double totalPrice;
    private final EconomyType economyType;
    private final SellOffer offer;

    public SaleLine(ItemStack item, int amount, double totalPrice, EconomyType economyType, SellOffer offer) {
        if (item == null || amount <= 0 || !Double.isFinite(totalPrice) || totalPrice <= 0) {
            throw new IllegalArgumentException("A sale line must contain a positive, finite sale.");
        }
        this.item = item.clone();
        this.item.setAmount(1);
        this.amount = amount;
        this.totalPrice = totalPrice;
        this.economyType = Objects.requireNonNull(economyType, "economyType");
        this.offer = Objects.requireNonNull(offer, "offer");
    }

    public ItemStack getItem() { return item.clone(); }
    public int getAmount() { return amount; }
    public double getTotalPrice() { return totalPrice; }
    public EconomyType getEconomyType() { return economyType; }
    public SellOffer getOffer() { return offer; }
    public ShopItem getShopItem() { return offer.getShopItem(); }
    public double getPricePerItem() { return totalPrice / amount; }
}
