package net.mackenziemolloy.shopguiplus.sellgui.utility;

import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.ShopManager;
import net.brcdev.shopgui.shop.item.ShopItem;
import net.brcdev.shopgui.shop.item.ShopItemType;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SellOffer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopHandlerTest {
    private final Player player = mock(Player.class);

    private ShopItem listing(double base, double effective) {
        ShopItem listing = mock(ShopItem.class);
        ItemStack listedItem = mock(ItemStack.class);
        when(listing.getType()).thenReturn(ShopItemType.ITEM);
        when(listing.getItem()).thenReturn(listedItem);
        when(listedItem.isSimilar(any(ItemStack.class))).thenReturn(true);
        when(listing.getSellPrice()).thenReturn(base);
        when(listing.getSellPriceForAmount(player, 1)).thenReturn(effective);
        return listing;
    }

    private SellOffer selectWithDynamic(ShopItem[] listings, Double... prices) throws Exception {
        ItemStack input = mock(ItemStack.class);
        ItemStack single = mock(ItemStack.class);
        when(input.clone()).thenReturn(single);

        Shop shop = mock(Shop.class);
        when(shop.getShopItems()).thenReturn(Arrays.asList(listings));
        for (ShopItem listing : listings) when(shop.hasAccess(player, listing, false)).thenReturn(true);
        when(shop.getEconomyType()).thenReturn(EconomyType.VAULT);
        ShopManager manager = mock(ShopManager.class);
        doReturn(new LinkedHashSet<>(List.of(shop))).when(manager).getShops();
        ShopGuiPlugin plugin = mock(ShopGuiPlugin.class);
        when(plugin.getShopManager()).thenReturn(manager);

        try (MockedStatic<ShopGuiPlusApi> api = mockStatic(ShopGuiPlusApi.class);
                MockedStatic<DynaShopHandler> dynamic = mockStatic(DynaShopHandler.class)) {
            api.when(ShopGuiPlusApi::getPlugin).thenReturn(plugin);
            for (int i = 0; i < listings.length; i++) {
                final Double price = prices[i];
                final ShopItem listing = listings[i];
                dynamic.when(() -> DynaShopHandler.getSellPricePerItem(eq(player), eq(listing), any(ItemStack.class)))
                        .thenReturn(price);
            }
            return ShopHandler.findBestSellOffer(player, input);
        }
    }

    @Test void disabledBasePricesNeverReachDynamicLookup() throws Exception {
        for (double base : new double[] { -1, 0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            ShopItem disabled = listing(base, 99);
            ItemStack input = mock(ItemStack.class);
            ItemStack single = mock(ItemStack.class);
            when(input.clone()).thenReturn(single);
            Shop shop = mock(Shop.class);
            when(shop.getShopItems()).thenReturn(List.of(disabled));
            when(shop.hasAccess(player, disabled, false)).thenReturn(true);
            ShopManager manager = mock(ShopManager.class);
            doReturn(new LinkedHashSet<>(List.of(shop))).when(manager).getShops();
            ShopGuiPlugin plugin = mock(ShopGuiPlugin.class);
            when(plugin.getShopManager()).thenReturn(manager);
            try (MockedStatic<ShopGuiPlusApi> api = mockStatic(ShopGuiPlusApi.class);
                    MockedStatic<DynaShopHandler> dynamic = mockStatic(DynaShopHandler.class)) {
                api.when(ShopGuiPlusApi::getPlugin).thenReturn(plugin);
                assertNull(ShopHandler.findBestSellOffer(player, input));
                verify(disabled).getSellPrice();
                verify(disabled, never()).getSellPriceForAmount(player, 1);
                dynamic.verifyNoInteractions();
            }
        }
    }

    @Test void invalidEffectiveQuoteIsRejectedBeforeDynamicLookup() throws Exception {
        for (double effective : new double[] { 0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            ShopItem listing = listing(10, effective);
            ItemStack input = mock(ItemStack.class);
            when(input.clone()).thenReturn(mock(ItemStack.class));
            Shop shop = mock(Shop.class);
            when(shop.getShopItems()).thenReturn(List.of(listing));
            when(shop.hasAccess(player, listing, false)).thenReturn(true);
            ShopManager manager = mock(ShopManager.class);
            doReturn(new LinkedHashSet<>(List.of(shop))).when(manager).getShops();
            ShopGuiPlugin plugin = mock(ShopGuiPlugin.class);
            when(plugin.getShopManager()).thenReturn(manager);
            try (MockedStatic<ShopGuiPlusApi> api = mockStatic(ShopGuiPlusApi.class);
                    MockedStatic<DynaShopHandler> dynamic = mockStatic(DynaShopHandler.class)) {
                api.when(ShopGuiPlusApi::getPlugin).thenReturn(plugin);
                assertNull(ShopHandler.findBestSellOffer(player, input));
                InOrder order = inOrder(listing);
                order.verify(listing).getSellPrice();
                order.verify(listing).getSellPriceForAmount(player, 1);
                dynamic.verifyNoInteractions();
            }
        }
    }

    @Test void disabledEntryCannotVetoEnabledAlternateAndBestEligiblePriceWins() throws Exception {
        ShopItem disabled = listing(-1, 99);
        ShopItem enabled = listing(5, 5);
        SellOffer offer = selectWithDynamic(new ShopItem[] { disabled, enabled }, 1000.0, 4.0);
        assertNotNull(offer);
        assertSame(enabled, offer.getShopItem());
        assertEquals(4, offer.getPricePerItem());
    }

    @Test void highestDynamicPriceWinsAcrossEligibleEntries() throws Exception {
        ShopItem first = listing(1, 1);
        ShopItem second = listing(5, 5);
        SellOffer offer = selectWithDynamic(new ShopItem[] { first, second }, 7.0, 4.0);
        assertNotNull(offer);
        assertSame(first, offer.getShopItem());
        assertEquals(7, offer.getPricePerItem());
    }

    @Test void invalidDynamicQuoteDoesNotFallBackToStaticQuote() throws Exception {
        for (double dynamicPrice : new double[] { -1, 0, Double.NaN, Double.POSITIVE_INFINITY }) {
            ShopItem listing = listing(10, 8);
            assertNull(selectWithDynamic(new ShopItem[] { listing }, dynamicPrice));
        }
    }

    @Test void unhandledDynamicQuoteFallsBackToEffectivePlayerQuote() throws Exception {
        ShopItem listing = listing(10, 8);
        SellOffer offer = selectWithDynamic(new ShopItem[] { listing }, (Double) null);
        assertNotNull(offer);
        assertEquals(8, offer.getPricePerItem());
    }
}