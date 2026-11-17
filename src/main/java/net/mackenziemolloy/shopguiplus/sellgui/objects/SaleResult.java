package net.mackenziemolloy.shopguiplus.sellgui.objects;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Result of selling a loose stack or eligible contents of one shulker box. */
public final class SaleResult {
    private final ItemStack returnedItem;
    private final List<SaleLine> lines;

    public SaleResult(ItemStack returnedItem, List<SaleLine> lines) {
        this.returnedItem = returnedItem == null ? null : returnedItem.clone();
        this.lines = List.copyOf(lines);
    }

    public ItemStack getReturnedItem() {
        return returnedItem == null ? null : returnedItem.clone();
    }

    public List<SaleLine> getLines() { return lines; }
}
