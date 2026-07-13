package net.mackenziemolloy.shopguiplus.sellgui.utility;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.economy.EconomyManager;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.exception.shop.ShopsNotLoadedException;
import net.brcdev.shopgui.provider.economy.EconomyProvider;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;
import net.brcdev.shopgui.shop.item.ShopItemType;
import net.mackenziemolloy.shopguiplus.sellgui.SellGUI;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SellOffer;
import org.jetbrains.annotations.NotNull;

public class ShopHandler {

    @NotNull
    public static EconomyType getEconomyType(ItemStack material) {
        EconomyType economyType = ShopGuiPlusApi.getItemStackShop(material).getEconomyType();
        if (economyType != null) {
            return economyType;
        }

        EconomyManager economyManager = ShopGuiPlusApi.getPlugin().getEconomyManager();
        EconomyProvider defaultEconomyProvider = economyManager.getDefaultEconomyProvider();
        if (defaultEconomyProvider != null) {
            String defaultEconomyTypeName = defaultEconomyProvider.getName().toUpperCase(Locale.US);
            try {
                return EconomyType.valueOf(defaultEconomyTypeName);
            } catch (IllegalArgumentException ex) {
                return EconomyType.CUSTOM;
            }
        }

        return EconomyType.CUSTOM;
    }

    public static Double getItemSellPrice(ItemStack material, Player player) {
        SellOffer bestOffer = findBestSellOffer(player, material);
        if (bestOffer != null) {
            int amount = Math.max(material.getAmount(), 1);
            return bestOffer.getPricePerItem() * amount;
        }

        return ShopGuiPlusApi.getItemStackPriceSell(player, material);
    }

    /**
     * Shop#hasAccess has changed shape between ShopGUI+ releases, and calling a signature the
     * running server does not have throws NoSuchMethodError mid-sale. Resolve it once against
     * whatever is actually installed; if no known signature exists, do not block the shop.
     */
    private static Method hasAccessMethod;
    private static boolean hasAccessResolved;

    private static synchronized boolean hasAccess(Shop shop, Player player, ShopItem shopItem) {
        if (!hasAccessResolved) {
            hasAccessResolved = true;
            try {
                hasAccessMethod = Shop.class.getMethod("hasAccess", Player.class, ShopItem.class, boolean.class);
            } catch (NoSuchMethodException first) {
                try {
                    hasAccessMethod = Shop.class.getMethod("hasAccess", Player.class, ShopItem.class);
                } catch (NoSuchMethodException second) {
                    try {
                        hasAccessMethod = Shop.class.getMethod("hasAccess", Player.class);
                    } catch (NoSuchMethodException third) {
                        hasAccessMethod = null;
                    }
                }
            }
        }

        if (hasAccessMethod == null) {
            return true;
        }

        try {
            Object result;
            int parameters = hasAccessMethod.getParameterCount();
            if (parameters == 3) {
                result = hasAccessMethod.invoke(shop, player, shopItem, false);
            } else if (parameters == 2) {
                result = hasAccessMethod.invoke(shop, player, shopItem);
            } else {
                result = hasAccessMethod.invoke(shop, player);
            }
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return true;
        }
    }

    /**
     * The shop that pays the most for this item, across every shop the player may sell in.
     * ShopGUI+ on its own stops at the first shop containing the item, so an item listed in several
     * shops (a black market next to a food shop, say) would never fetch its best price.
     */
    public static SellOffer findBestSellOffer(Player player, ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        ItemStack singleItem = itemStack.clone();
        singleItem.setAmount(1);

        SellOffer bestOffer = null;

        try {
            Set<Shop> shops = ShopGuiPlusApi.getPlugin().getShopManager().getShops();
            if (shops == null) {
                return null;
            }

            for (Shop shop : shops) {
                for (ShopItem shopItem : shop.getShopItems()) {
                    if (shopItem.getType() != ShopItemType.ITEM || shopItem.getItem() == null) {
                        continue;
                    }
                    if (!shopItem.getItem().isSimilar(singleItem)) {
                        continue;
                    }
                    if (!hasAccess(shop, player, shopItem)) {
                        continue;
                    }

                    Double pricePerItem = DynaShopHandler.getSellPricePerItem(player, shopItem, singleItem);
                    if (pricePerItem == null) {
                        pricePerItem = shopItem.getSellPriceForAmount(player, 1);
                    }

                    if (pricePerItem <= 0) {
                        continue;
                    }

                    if (bestOffer == null || pricePerItem > bestOffer.getPricePerItem()) {
                        bestOffer = new SellOffer(shopItem, pricePerItem, shop.getEconomyType());
                    }
                }
            }
        } catch (ShopsNotLoadedException | RuntimeException | LinkageError exception) {
            return null;
        }

        return bestOffer;
    }

    public static String getFormattedPrice(Double priceToFormat, EconomyType economyType) {
        SellGUI plugin = JavaPlugin.getPlugin(SellGUI.class);
        CommentedConfiguration configuration = plugin.getConfiguration();
        String priceToReturn = priceToFormat.toString();

        if (configuration.getBoolean("options.rounded_pricing")) {
            DecimalFormat formatToApplyRaw = new DecimalFormat("0.00");
            priceToReturn = formatToApplyRaw.format(priceToFormat);
        }

        if (configuration.getBoolean("options.remove_trailing_zeros")) {
            if (Double.valueOf(priceToReturn.split("\\.")[1]) == 0) {
                priceToReturn = priceToReturn.split("\\.")[0];
            }
        }

        return priceToReturn;
    }
}
