package net.mackenziemolloy.shopguiplus.sellgui.utility;

import net.brcdev.shopgui.economy.EconomyType;
import net.mackenziemolloy.shopguiplus.sellgui.SellGUI;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SaleBatch;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SaleLine;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SaleResult;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SellOffer;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public final class SaleProcessor {
    @FunctionalInterface public interface OfferResolver { SellOffer resolve(ItemStack item); }
    @FunctionalInterface public interface Payment { boolean pay(EconomyType economy, double amount); }
    private final OfferResolver offers;
    private final Payment payment;
    private final Consumer<SaleLine> observer;
    private final BiPredicate<ItemStack, SellOffer> eligibility;
    public SaleProcessor(OfferResolver offers, Payment payment, Consumer<SaleLine> observer) { this(offers,payment,observer,(item,offer)->true); }
    public SaleProcessor(OfferResolver offers, Payment payment, Consumer<SaleLine> observer, BiPredicate<ItemStack,SellOffer> eligibility) {
        this.offers=Objects.requireNonNull(offers); this.payment=Objects.requireNonNull(payment); this.observer=observer==null?line->{}:observer; this.eligibility=Objects.requireNonNull(eligibility);
    }
    public static boolean isShulker(ItemStack item) { if(item==null)return false; String n=item.getType().name(); return n.equals("SHULKER_BOX")||n.endsWith("_SHULKER_BOX"); }
    public SaleResult process(ItemStack item) { SaleBatch b=new SaleBatch(); if(item!=null)b.addInput(item); processAll(b); return b.size()==0?new SaleResult(null,List.of()):b.getResult(0); }
    public void processAll(SaleBatch batch) {
        if (batch == null || !batch.begin()) return;
        Map<EconomyType, List<Candidate>> groups = new EnumMap<>(EconomyType.class);
        for (int sourceIndex = 0; sourceIndex < batch.size(); sourceIndex++) {
            try {
                ItemStack source = batch.getInput(sourceIndex);
                if (source == null || source.getType().isAir()) continue;
                if (isShulker(source)) {
                    planBox(sourceIndex, source, groups);
                } else {
                    SellOffer offer = resolve(source);
                    if (offer == null) continue;
                    double price = offer.getPricePerItem() * source.getAmount();
                    if (Double.isFinite(price) && price > 0) {
                        groups.computeIfAbsent(offer.getEconomyType(), ignored -> new ArrayList<>())
                                .add(new Candidate(sourceIndex, -1, source, offer, price));
                    }
                }
            } catch (RuntimeException | LinkageError failure) {
                log("Unable to plan GUI source; preserving it", failure);
            }
        }

        for (Map.Entry<EconomyType, List<Candidate>> currencyGroup : groups.entrySet()) {
            EconomyType currency = currencyGroup.getKey();
            Map<Integer, List<Candidate>> bySource = new java.util.LinkedHashMap<>();
            for (Candidate candidate : currencyGroup.getValue()) {
                bySource.computeIfAbsent(candidate.source, ignored -> new ArrayList<>()).add(candidate);
            }

            Map<Integer, SaleResult> preparedResults = new java.util.LinkedHashMap<>();
            List<SaleLine> observerLines = new ArrayList<>();
            double preparedTotal = 0;
            for (Map.Entry<Integer, List<Candidate>> sourceGroup : bySource.entrySet()) {
                int sourceIndex = sourceGroup.getKey();
                List<Candidate> sourceCandidates = sourceGroup.getValue();
                try {
                    ItemStack current = batch.getResult(sourceIndex).getReturnedItem();
                    List<SaleLine> freshLines = new ArrayList<>();
                    ItemStack replacement;
                    Candidate first = sourceCandidates.get(0);
                    if (first.slot < 0) {
                        if (sourceCandidates.size() != 1) continue;
                        freshLines.add(new SaleLine(first.item, first.item.getAmount(), first.price, currency, first.offer));
                        replacement = null;
                    } else {
                        if (current == null || !isShulker(current)) continue;
                        ItemStack staged = current.clone();
                        ItemMeta raw = staged.getItemMeta();
                        if (!(raw instanceof BlockStateMeta meta)) continue;
                        if (!(meta.getBlockState() instanceof ShulkerBox state)) continue;
                        for (Candidate candidate : sourceCandidates) {
                            if (candidate.slot >= 0) state.getInventory().setItem(candidate.slot, null);
                            freshLines.add(new SaleLine(candidate.item, candidate.item.getAmount(), candidate.price, currency, candidate.offer));
                        }
                        meta.setBlockState(state);
                        if (!staged.setItemMeta(meta)) continue;
                        replacement = staged;
                    }
                    double sourceTotal = 0;
                    for (SaleLine line : freshLines) sourceTotal += line.getTotalPrice();
                    if (freshLines.isEmpty() || !Double.isFinite(sourceTotal) || sourceTotal <= 0) continue;
                    List<SaleLine> combinedLines = new ArrayList<>(batch.getResult(sourceIndex).getLines());
                    combinedLines.addAll(freshLines);
                    SaleResult prepared = new SaleResult(replacement, combinedLines);
                    preparedResults.put(sourceIndex, prepared);
                    preparedTotal += sourceTotal;
                    observerLines.addAll(freshLines);
                } catch (RuntimeException | LinkageError failure) {
                    log("Unable to stage source for currency; preserving current state", failure);
                }
            }
            if (preparedResults.isEmpty() || !Double.isFinite(preparedTotal) || preparedTotal <= 0) continue;
            boolean paid;
            try {
                paid = payment.pay(currency, preparedTotal);
            } catch (RuntimeException | LinkageError failure) {
                log("Payment outcome uncertain; no retry", failure);
                continue;
            }
            if (!paid) continue;
            for (Map.Entry<Integer, SaleResult> prepared : preparedResults.entrySet()) {
                batch.commit(prepared.getKey(), prepared.getValue());
            }
            for (SaleLine line : observerLines) {
                try { observer.accept(line); }
                catch (RuntimeException | LinkageError failure) { log("Observer failed after payment", failure); }
            }
        }
    }
    private void planBox(int index,ItemStack source,Map<EconomyType,List<Candidate>> groups) {
        if(source.getAmount()!=1)return;
        try {
            ItemMeta raw=source.getItemMeta(); if(!(raw instanceof BlockStateMeta meta)||!(meta.getBlockState() instanceof ShulkerBox box))return;
            for(int slot=0;slot<box.getInventory().getSize();slot++) {
                ItemStack item=box.getInventory().getItem(slot); if(item==null||item.getType().isAir()||isShulker(item))continue;
                SellOffer offer=resolve(item); if(offer==null)continue; double p=offer.getPricePerItem()*item.getAmount(); if(Double.isFinite(p)&&p>0)groups.computeIfAbsent(offer.getEconomyType(),x->new ArrayList<>()).add(new Candidate(index,slot,item.clone(),offer,p));
            }
        } catch(RuntimeException|LinkageError ex){log("Unable to inspect shulker; preserving it",ex);}
    }
    private SellOffer resolve(ItemStack item){try{SellOffer o=offers.resolve(item.clone());return o==null||o.getEconomyType()==null||!Double.isFinite(o.getPricePerItem())||o.getPricePerItem()<=0||!eligibility.test(item.clone(),o)?null:o;}catch(RuntimeException|LinkageError ex){return null;}}
    private static void log(String message,Throwable ex){SellGUI p=SellGUI.getInstance();if(p!=null)try{p.getLogger().warning("SellGUI "+message+": "+ex.getMessage());}catch(RuntimeException ignored){}}
    private record Candidate(int source,int slot,ItemStack item,SellOffer offer,double price){}
}

