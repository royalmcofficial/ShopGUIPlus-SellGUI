package net.mackenziemolloy.shopguiplus.sellgui.utility;

import java.util.HashMap;
import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.tylwen.satyria.dynashop.DynaShopPlugin;
import fr.tylwen.satyria.dynashop.data.param.DynaShopType;
import fr.tylwen.satyria.dynashop.price.DynamicPrice;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.ShopManager.ShopAction;
import net.brcdev.shopgui.shop.item.ShopItem;

public final class DynaShopHandler {

    private static final String PLUGIN_NAME = "ShopGUIPlus-DynaShop";

    private static Boolean present;

    private DynaShopHandler() {
    }

    public static boolean isPresent() {
        if (present == null) {
            present = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) != null && isLoadable();
        }
        return present;
    }

    private static boolean isLoadable() {
        try {
            Class.forName("fr.tylwen.satyria.dynashop.DynaShopPlugin");
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

    public static Double getSellPricePerItem(Player player, ItemStack itemStack) {
        if (itemStack == null || !isPresent()) {
            return null;
        }

        try {
            ShopItem shopItem = ShopGuiPlusApi.getItemStackShopItem(player, itemStack);
            if (shopItem == null || shopItem.getShop() == null) {
                return null;
            }

            return getSellPricePerItem(player, shopItem, itemStack);
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    public static Double getSellPricePerItem(Player player, ShopItem shopItem, ItemStack itemStack) {
        if (shopItem == null || shopItem.getShop() == null || itemStack == null || !isPresent()) {
            return null;
        }

        try {
            String shopId = shopItem.getShop().getId();
            String itemId = shopItem.getId();

            DynaShopPlugin dynaShop = DynaShopPlugin.getInstance();
            if (dynaShop == null) {
                return null;
            }

            DynaShopType sellType = dynaShop.getShopConfigManager().resolveTypeDynaShop(shopId, itemId, false);
            if (sellType == null || sellType == DynaShopType.NONE || sellType == DynaShopType.UNKNOWN) {
                return null;
            }

            ItemStack singleItem = itemStack.clone();
            singleItem.setAmount(1);

            DynamicPrice price = dynaShop.getDynaShopListener()
                    .getOrLoadPrice(player, shopId, itemId, singleItem, new HashSet<>(), new HashMap<>());
            if (price == null || price.getSellPrice() <= 0) {
                return null;
            }

            return price.getSellPrice();
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    public static void notifySale(Player player, ShopItem shopItem, ItemStack itemStack, int amount,
            double totalPrice) {
        if (itemStack == null || amount <= 0 || !isPresent()) {
            return;
        }

        try {
            if (shopItem == null || shopItem.getShop() == null) {
                shopItem = ShopGuiPlusApi.getItemStackShopItem(player, itemStack);
            }
            if (shopItem == null || shopItem.getShop() == null) {
                return;
            }

            DynaShopPlugin dynaShop = DynaShopPlugin.getInstance();
            if (dynaShop == null) {
                return;
            }

            ItemStack singleItem = itemStack.clone();
            singleItem.setAmount(1);

            dynaShop.getDynaShopListener().handleExternalTransaction(player, shopItem.getShop().getId(),
                    shopItem.getId(), singleItem, amount, ShopAction.SELL, totalPrice);
        } catch (RuntimeException | LinkageError exception) {
            return;
        }
    }
}
