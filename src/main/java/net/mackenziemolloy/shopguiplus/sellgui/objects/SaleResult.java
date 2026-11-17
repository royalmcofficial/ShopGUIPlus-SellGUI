package net.mackenziemolloy.shopguiplus.sellgui.objects;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/** Ownership transfer result. Item references are not cloned after a payment. */
public final class SaleResult {
    private final ItemStack returnedItem;
    private final List<SaleLine> lines;
    public SaleResult(ItemStack returnedItem, List<SaleLine> lines) {
        this.returnedItem = returnedItem;
        this.lines = List.copyOf(lines);
    }
    public ItemStack getReturnedItem() { return returnedItem; }
    public List<SaleLine> getLines() { return lines; }
}
