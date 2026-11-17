package net.mackenziemolloy.shopguiplus.sellgui.objects;

import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Reference ownership ledger for GUI inputs and their latest committed return state. */
public final class SaleBatch {
    private final List<ItemStack> inputs = new ArrayList<>();
    private final List<SaleResult> results = new ArrayList<>();
    private boolean begun;
    public void addInput(ItemStack item) { inputs.add(item); results.add(new SaleResult(item, List.of())); }
    public ItemStack getInput(int index) { return inputs.get(index); }
    public SaleResult getResult(int index) { return results.get(index); }
    public int size() { return inputs.size(); }
    public void commit(int index, SaleResult result) { results.set(index, result); }
    public boolean begin() { if (begun) return false; begun = true; return true; }
}
