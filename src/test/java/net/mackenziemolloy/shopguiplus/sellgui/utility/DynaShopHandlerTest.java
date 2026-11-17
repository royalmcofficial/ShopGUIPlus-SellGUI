package net.mackenziemolloy.shopguiplus.sellgui.utility;

import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DynaShopHandlerTest {
    private double resolve(double base, double effective) {
        Player player = mock(Player.class);
        Shop shop = mock(Shop.class);
        ShopItem listing = mock(ShopItem.class);
        ItemStack stack = mock(ItemStack.class);
        when(listing.getShop()).thenReturn(shop);
        when(listing.getSellPrice()).thenReturn(base);
        when(listing.getSellPriceForAmount(player, 1)).thenReturn(effective);
        try (MockedStatic<DynaShopHandler> handler = mockStatic(DynaShopHandler.class, CALLS_REAL_METHODS)) {
            handler.when(DynaShopHandler::isPresent).thenReturn(true);
            Double result = DynaShopHandler.getSellPricePerItem(player, listing, stack);
            InOrder order = inOrder(listing);
            order.verify(listing).getSellPrice();
            order.verify(listing).getSellPriceForAmount(player, 1);
            return result;
        }
    }

    @Test void invalidBasePriceBlocksDirectResolutionBeforeComputedQuote() throws Exception {
        for (double base : new double[] { -1, 0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            Player player = mock(Player.class);
            Shop shop = mock(Shop.class);
            ShopItem listing = mock(ShopItem.class);
            ItemStack stack = mock(ItemStack.class);
            when(listing.getShop()).thenReturn(shop);
            when(listing.getSellPrice()).thenReturn(base);
            when(listing.getSellPriceForAmount(player, 1)).thenReturn(99.0);
            try (MockedStatic<DynaShopHandler> handler = mockStatic(DynaShopHandler.class, CALLS_REAL_METHODS)) {
                handler.when(DynaShopHandler::isPresent).thenReturn(true);
                assertEquals(-1.0, DynaShopHandler.getSellPricePerItem(player, listing, stack));
                verify(listing).getSellPrice();
                verify(listing, never()).getSellPriceForAmount(player, 1);
            }
        }
    }

    @Test void invalidEffectivePriceBlocksDirectResolutionAfterValidBase() throws Exception {
        for (double effective : new double[] { -1, 0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertEquals(-1.0, resolve(10, effective));
        }
    }
}