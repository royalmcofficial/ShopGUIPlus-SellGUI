package net.mackenziemolloy.shopguiplus.sellgui.utility;

import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.shop.item.ShopItem;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SaleResult;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SellOffer;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaleProcessorTest {
    private SellOffer offer(double price, EconomyType economy) {
        return new SellOffer(mock(ShopItem.class), price, economy);
    }

    private ItemStack box(ItemStack... contents) {
        ItemStack box = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta meta = mock(BlockStateMeta.class);
        ShulkerBox state = mock(ShulkerBox.class);
        Inventory inventory = mock(Inventory.class);
        when(box.getItemMeta()).thenReturn(meta);
        when(meta.getBlockState()).thenReturn(state);
        when(state.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(contents.length);
        for (int i = 0; i < contents.length; i++) when(inventory.getItem(i)).thenReturn(contents[i]);
        ItemStack staged = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta stagedMeta = mock(BlockStateMeta.class);
        ShulkerBox stagedState = mock(ShulkerBox.class);
        Inventory stagedInv = mock(Inventory.class);
        when(staged.getItemMeta()).thenReturn(stagedMeta);
        when(stagedMeta.getBlockState()).thenReturn(stagedState);
        when(stagedState.getInventory()).thenReturn(stagedInv);
        when(stagedInv.getSize()).thenReturn(contents.length);
        for (int i = 0; i < contents.length; i++) when(stagedInv.getItem(i)).thenReturn(contents[i]);
        when(stagedState.update(true, false)).thenReturn(true);
        when(staged.setItemMeta(stagedMeta)).thenReturn(true);
        return box;
    }

    @Test void looseStackUsesCapturedOfferAndPaysOnce() {
        ItemStack item = new ItemStack(Material.DIAMOND, 4);
        SellOffer offer = offer(2.5, EconomyType.VAULT);
        AtomicInteger payments = new AtomicInteger();
        SaleProcessor processor = new SaleProcessor(i -> offer, (e, total) -> { payments.incrementAndGet(); assertEquals(10, total); return true; }, line -> { });
        SaleResult result = processor.process(item);
        assertNull(result.getReturnedItem());
        assertEquals(1, result.getLines().size());
        assertEquals(4, result.getLines().get(0).getAmount());
        assertEquals(10, result.getLines().get(0).getTotalPrice());
        assertEquals(1, payments.get());
    }

    @Test void failedPaymentReturnsLooseItemAndNeverNotifies() {
        ItemStack item = new ItemStack(Material.DIAMOND, 2);
        AtomicInteger notifications = new AtomicInteger();
        SaleProcessor processor = new SaleProcessor(i -> offer(5, EconomyType.VAULT), (e, total) -> false, line -> notifications.incrementAndGet());
        SaleResult result = processor.process(item);
        assertEquals(2, result.getReturnedItem().getAmount());
        assertTrue(result.getLines().isEmpty());
        assertEquals(0, notifications.get());
    }

    @Test void shulkerNeverResolvesContainerAndLeavesIneligibleContents() {
        ItemStack unsellable = new ItemStack(Material.STONE, 7);
        ItemStack box = box(unsellable);
        AtomicInteger resolves = new AtomicInteger();
        SaleProcessor processor = new SaleProcessor(item -> { resolves.incrementAndGet(); return null; }, (e, total) -> fail("must not pay"), line -> { });
        SaleResult result = processor.process(box);
        assertNotNull(result.getReturnedItem());
        assertTrue(result.getLines().isEmpty());
        assertEquals(1, resolves.get());
    }

    @Test void oneEconomyGroupPaysOnceAndRetainsOtherEconomyOnFailure() {
        ItemStack first = new ItemStack(Material.DIAMOND, 2);
        ItemStack second = new ItemStack(Material.EMERALD, 3);
        ItemStack box = box(first, second);
        AtomicInteger payments = new AtomicInteger();
        List<Double> totals = new ArrayList<>();
        SaleProcessor processor = new SaleProcessor(i -> i.getType() == Material.DIAMOND
                ? offer(2, EconomyType.VAULT) : offer(3, EconomyType.CUSTOM), (e, total) -> {
            payments.incrementAndGet(); totals.add(total); return e == EconomyType.VAULT;
        }, line -> { });
        SaleResult result = processor.process(box);
        assertEquals(2, payments.get());
        assertEquals(List.of(4.0, 9.0), totals);
        assertEquals(1, result.getLines().size());
        assertEquals(4, result.getLines().get(0).getTotalPrice());
        assertNotNull(result.getReturnedItem());
    }

    @Test void stackedOrMalformedShulkerIsReturnedWithoutResolving() {
        ItemStack stacked = new ItemStack(Material.SHULKER_BOX, 2);
        AtomicInteger resolves = new AtomicInteger();
        SaleProcessor processor = new SaleProcessor(i -> { resolves.incrementAndGet(); return offer(1, EconomyType.VAULT); }, (e, total) -> true, line -> { });
        assertNotNull(processor.process(stacked).getReturnedItem());
        assertEquals(0, resolves.get());
    }
}
