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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaleProcessorTest {
    private Material material(String name) {
        Material material = mock(Material.class);
        when(material.name()).thenReturn(name);
        when(material.isAir()).thenReturn(false);
        return material;
    }

    private SellOffer offer(double price, EconomyType economy) {
        return new SellOffer(mock(ShopItem.class), price, economy);
    }

    private ItemStack item(Material material, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(stack.getAmount()).thenReturn(amount);
        when(stack.clone()).thenAnswer(invocation -> item(material, amount));
        return stack;
    }

    private static final class BoxModel {
        private final AtomicReference<List<ItemStack>> contents;
        private final ItemStack box;

        private BoxModel(List<ItemStack> initial, Material shulkerType) {
            contents = new AtomicReference<>(new ArrayList<>(initial));
            box = createBox(contents, 1, shulkerType);
        }

        private static ItemStack createBox(AtomicReference<List<ItemStack>> contents, int amount, Material shulkerType) {
            ItemStack box = mock(ItemStack.class);
            when(box.getType()).thenReturn(shulkerType);
            when(box.getAmount()).thenReturn(amount);
            when(box.clone()).thenAnswer(invocation -> createBox(new AtomicReference<>(cloneSlots(contents.get())), amount, shulkerType));
            BlockStateMeta meta = mock(BlockStateMeta.class);
            ShulkerBox state = mock(ShulkerBox.class);
            Inventory inventory = mock(Inventory.class);
            when(box.getItemMeta()).thenReturn(meta);
            when(meta.getBlockState()).thenReturn(state);
            when(state.getInventory()).thenReturn(inventory);
            when(inventory.getSize()).thenAnswer(invocation -> contents.get().size());
            when(inventory.getContents()).thenAnswer(invocation -> contents.get().toArray(new ItemStack[0]));
            when(inventory.getItem(anyInt())).thenAnswer(invocation -> {
                int slot = invocation.getArgument(0);
                List<ItemStack> slots = contents.get();
                return slot >= 0 && slot < slots.size() ? slots.get(slot) : null;
            });
            doAnswer(invocation -> {
                int slot = invocation.getArgument(0);
                ItemStack value = invocation.getArgument(1);
                List<ItemStack> slots = new ArrayList<>(contents.get());
                while (slots.size() <= slot) slots.add(null);
                slots.set(slot, value);
                contents.set(slots);
                return null;
            }).when(inventory).setItem(anyInt(), nullable(ItemStack.class));
            when(state.update(true, false)).thenReturn(true);
            when(box.setItemMeta(meta)).thenReturn(true);
            return box;
        }

        private static List<ItemStack> cloneSlots(List<ItemStack> slots) {
            List<ItemStack> copy = new ArrayList<>(slots.size());
            for (ItemStack slot : slots) copy.add(slot == null ? null : slot.clone());
            return copy;
        }

        List<ItemStack> contents() { return contents.get(); }
    }

    private BoxModel box(ItemStack... contents) {
        Material shulker = material("SHULKER_BOX");
        return new BoxModel(Arrays.asList(contents), shulker);
    }

    private List<ItemStack> returnedContents(ItemStack box) {
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        return Arrays.asList(((ShulkerBox) meta.getBlockState()).getInventory().getContents());
    }

    @Test void twoStacksOfSameCurrencyAreAggregatedOnce() {
        BoxModel box = box(item(material("DIAMOND"), 2), item(material("DIAMOND"), 3));
        AtomicInteger payments = new AtomicInteger();
        AtomicInteger notifications = new AtomicInteger();
        SaleProcessor processor = new SaleProcessor(i -> offer(1, EconomyType.VAULT), (e, total) -> {
            assertEquals(EconomyType.VAULT, e);
            assertEquals(5.0, total);
            payments.incrementAndGet();
            return true;
        }, line -> notifications.incrementAndGet());

        SaleResult result = processor.process(box.box);

        assertEquals(1, payments.get());
        assertEquals(2, notifications.get());
        assertEquals(2, result.getLines().size());
        assertEquals(5, result.getLines().stream().mapToInt(line -> line.getAmount()).sum());
        assertEquals(5.0, result.getLines().stream().mapToDouble(line -> line.getTotalPrice()).sum());
        assertEquals(2, box.contents().get(0).getAmount());
        assertEquals(3, box.contents().get(1).getAmount());
        assertEquals(2, returnedContents(result.getReturnedItem()).size());
        assertTrue(returnedContents(result.getReturnedItem()).stream().allMatch(java.util.Objects::isNull));
    }

    @Test void multipleCurrenciesKeepEachPaidLineAndNotifyOncePerGroup() {
        BoxModel box = box(item(material("DIAMOND"), 2), item(material("EMERALD"), 3));
        AtomicInteger vaultPayments = new AtomicInteger();
        AtomicInteger customPayments = new AtomicInteger();
        AtomicInteger notifications = new AtomicInteger();
        List<Double> totals = new ArrayList<>();
        SaleProcessor processor = new SaleProcessor(i -> "DIAMOND".equals(i.getType().name())
                ? offer(2, EconomyType.VAULT) : offer(2, EconomyType.CUSTOM), (e, total) -> {
            if (e == EconomyType.VAULT) vaultPayments.incrementAndGet();
            else if (e == EconomyType.CUSTOM) customPayments.incrementAndGet();
            else fail("unexpected currency " + e);
            totals.add(total);
            return true;
        }, line -> notifications.incrementAndGet());

        SaleResult result = processor.process(box.box);

        assertEquals(1, vaultPayments.get());
        assertEquals(1, customPayments.get());
        assertEquals(2, totals.size());
        assertTrue(totals.containsAll(List.of(4.0, 6.0)));
        assertEquals(2, notifications.get());
        assertEquals(2, result.getLines().size());
        assertTrue(result.getLines().stream().anyMatch(line -> line.getTotalPrice() == 4.0));
        assertTrue(result.getLines().stream().anyMatch(line -> line.getTotalPrice() == 6.0));
        assertTrue(returnedContents(result.getReturnedItem()).stream().allMatch(java.util.Objects::isNull));
    }

    @Test void boxedAndLooseItemsUseTheSameSinglePassPayments() {
        BoxModel box = box(item(material("DIAMOND"), 2), item(material("DIAMOND"), 3));
        ItemStack loose = item(material("DIAMOND"), 4);
        AtomicInteger payments = new AtomicInteger();
        List<Double> totals = new ArrayList<>();
        SaleProcessor processor = new SaleProcessor(i -> offer(1, EconomyType.VAULT), (e, total) -> {
            payments.incrementAndGet(); totals.add(total); return true;
        }, line -> { });

        SaleResult boxed = processor.process(box.box);
        SaleResult looseResult = processor.process(loose);

        assertEquals(2, payments.get());
        assertEquals(List.of(5.0, 4.0), totals);
        assertEquals(2, boxed.getLines().size());
        assertEquals(1, looseResult.getLines().size());
        assertEquals(5.0, boxed.getLines().stream().mapToDouble(line -> line.getTotalPrice()).sum());
        assertEquals(4.0, looseResult.getLines().get(0).getTotalPrice());
    }

    @Test void mixedEligibleAndIneligibleContentsPreserveUnsoldSlot() {
        ItemStack unsellable = item(material("STONE"), 7);
        BoxModel box = box(item(material("DIAMOND"), 2), unsellable);
        SaleProcessor processor = new SaleProcessor(i -> "DIAMOND".equals(i.getType().name())
                ? offer(2, EconomyType.VAULT) : null, (e, total) -> true, line -> { });

        SaleResult result = processor.process(box.box);

        assertEquals(1, result.getLines().size());
        List<ItemStack> returned = returnedContents(result.getReturnedItem());
        assertEquals("STONE", returned.get(1).getType().name());
        assertEquals(7, returned.get(1).getAmount());
    }

    @Test void containerIsNeverResolvedAndBoxInsideBoxStaysNested() {
        ItemStack nested = item(material("SHULKER_BOX"), 1);
        BoxModel box = box(nested);
        AtomicInteger resolutions = new AtomicInteger();
        SaleProcessor processor = new SaleProcessor(i -> { resolutions.incrementAndGet(); return null; }, (e, total) -> fail("must not pay"), line -> { });
        SaleResult result = processor.process(box.box);
        assertEquals(0, resolutions.get());
        assertNotNull(result.getReturnedItem());
        assertEquals("SHULKER_BOX", returnedContents(result.getReturnedItem()).get(0).getType().name());
    }
    @Test void stackedShulkerIsReturnedWithoutResolvingContents() {
        ItemStack stacked = item(material("SHULKER_BOX"), 2);
        AtomicInteger resolutions = new AtomicInteger();
        SaleProcessor processor = new SaleProcessor(i -> {
            resolutions.incrementAndGet(); return offer(1, EconomyType.VAULT);
        }, (e, total) -> true, line -> { });

        assertNotNull(processor.process(stacked).getReturnedItem());
        assertEquals(0, resolutions.get());
    }

    @Test void oneBatchAggregatesAcrossMultipleBoxesAndLooseItems() {
        BoxModel first = box(item(material("DIAMOND"), 2));
        BoxModel second = box(item(material("DIAMOND"), 3));
        ItemStack loose = item(material("DIAMOND"), 4);
        net.mackenziemolloy.shopguiplus.sellgui.objects.SaleBatch batch = new net.mackenziemolloy.shopguiplus.sellgui.objects.SaleBatch();
        batch.addInput(first.box); batch.addInput(loose); batch.addInput(second.box);
        AtomicInteger payments = new AtomicInteger();
        List<Double> totals = new ArrayList<>();
        SaleProcessor processor = new SaleProcessor(i -> offer(1, EconomyType.VAULT), (e, total) -> { payments.incrementAndGet(); totals.add(total); return true; }, line -> { });
        processor.processAll(batch);
        assertEquals(1, payments.get());
        assertEquals(List.of(9.0), totals);
        assertEquals(1, batch.getResult(0).getLines().size());
        assertEquals(1, batch.getResult(1).getLines().size());
        assertEquals(1, batch.getResult(2).getLines().size());
        assertNull(returnedContents(batch.getResult(0).getReturnedItem()).get(0));
        assertEquals(3, batch.getResult(2).getLines().get(0).getAmount());
    }

    @Test void boxCloneFailurePreservesBoxAndStillSettlesLaterLooseSource() {
        BoxModel broken = box(item(material("DIAMOND"), 2));
        ItemStack brokenBox = broken.box;
        doThrow(new IllegalStateException("clone staging failed")).when(brokenBox).clone();
        ItemStack loose = item(material("DIAMOND"), 3);
        net.mackenziemolloy.shopguiplus.sellgui.objects.SaleBatch batch = new net.mackenziemolloy.shopguiplus.sellgui.objects.SaleBatch();
        batch.addInput(brokenBox); batch.addInput(loose);
        AtomicInteger payments = new AtomicInteger();
        SaleProcessor processor = new SaleProcessor(i -> offer(2, EconomyType.VAULT), (e, total) -> { assertEquals(6.0, total); payments.incrementAndGet(); return true; }, line -> { });
        processor.processAll(batch);
        assertEquals(1, payments.get());
        verify(brokenBox).clone();
        assertSame(brokenBox, batch.getResult(0).getReturnedItem());
        assertTrue(batch.getResult(0).getLines().isEmpty());
        assertNull(batch.getResult(1).getReturnedItem());
        assertEquals(1, batch.getResult(1).getLines().size());
        assertEquals(6.0, batch.getResult(1).getLines().get(0).getTotalPrice());
    }

}




