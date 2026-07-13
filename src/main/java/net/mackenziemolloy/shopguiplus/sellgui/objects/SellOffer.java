package net.mackenziemolloy.shopguiplus.sellgui.objects;

import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.shop.item.ShopItem;

public class SellOffer {

    private final ShopItem shopItem;
    private final double pricePerItem;
    private final EconomyType economyType;

    public SellOffer(ShopItem shopItem, double pricePerItem, EconomyType economyType) {
        this.shopItem = shopItem;
        this.pricePerItem = pricePerItem;
        this.economyType = economyType;
    }

    public ShopItem getShopItem() {
        return shopItem;
    }

    public double getPricePerItem() {
        return pricePerItem;
    }

    public EconomyType getEconomyType() {
        return economyType;
    }
}
