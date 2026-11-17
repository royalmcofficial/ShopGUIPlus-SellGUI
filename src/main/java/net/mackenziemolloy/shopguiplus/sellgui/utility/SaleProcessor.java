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
        if(batch==null||!batch.begin())return;
        Map<EconomyType,List<Candidate>> groups=new EnumMap<>(EconomyType.class);
        for(int i=0;i<batch.size();i++) {
            ItemStack source=batch.getInput(i);
            try {
                if(source==null||source.getType().isAir())continue;
                if(isShulker(source)) planBox(i,source,groups); else {
                    SellOffer o=resolve(source); if(o!=null){double total=o.getPricePerItem()*source.getAmount(); if(Double.isFinite(total)&&total>0)groups.computeIfAbsent(o.getEconomyType(),x->new ArrayList<>()).add(new Candidate(i,-1,source,o,total));}
                }
            } catch(RuntimeException|LinkageError ex) { log("Unable to plan input",ex); }
        }
        for(Map.Entry<EconomyType,List<Candidate>> group:groups.entrySet()) {
            EconomyType currency=group.getKey(); List<Candidate> candidates=group.getValue(); double total=0; for(Candidate c:candidates)total+=c.price;
            if(!Double.isFinite(total)||total<=0)continue;
            Map<Integer,SaleResult> prepared=new java.util.LinkedHashMap<>(); List<SaleLine> lines=new ArrayList<>(); double preparedTotal=0;
            for(Candidate c:candidates) {
                try {
                    ItemStack current=batch.getResult(c.source).getReturnedItem();
                    if(c.slot<0) { SaleLine line=new SaleLine(c.item,c.item.getAmount(),c.price,currency,c.offer); prepared.put(c.source,new SaleResult(null,List.of(line))); lines.add(line); preparedTotal+=c.price; continue; }
                    if(current==null||!isShulker(current))continue;
                    ItemMeta raw=current.getItemMeta(); if(!(raw instanceof BlockStateMeta meta)||!(meta.getBlockState() instanceof ShulkerBox state))continue;
                    ItemStack staged=current.clone(); ItemMeta stagedRaw=staged.getItemMeta(); if(!(stagedRaw instanceof BlockStateMeta stagedMeta)||!(stagedMeta.getBlockState() instanceof ShulkerBox stagedBox))continue;
                    for(Candidate same:candidates)if(same.source==c.source&&same.slot>=0)stagedBox.getInventory().setItem(same.slot,null);
                    stagedMeta.setBlockState(stagedBox); if(!staged.setItemMeta(stagedMeta))continue;
                    // Build all lines for this source once, before any payment.
                    List<SaleLine> sourceLines=new ArrayList<>(); for(Candidate same:candidates)if(same.source==c.source&&same.slot>=0)sourceLines.add(new SaleLine(same.item,same.item.getAmount(),same.price,currency,same.offer));
                    double sourceTotal=sourceLines.stream().mapToDouble(SaleLine::getTotalPrice).sum();
                    prepared.put(c.source,new SaleResult(staged,sourceLines)); lines.addAll(sourceLines); preparedTotal+=sourceTotal;
                } catch(RuntimeException|LinkageError ex) { log("Unable to stage source for currency",ex); }
            }
            if(prepared.isEmpty()||!Double.isFinite(preparedTotal)||preparedTotal<=0)continue;
            boolean paid; try { paid=payment.pay(currency,preparedTotal); } catch(RuntimeException|LinkageError ex) { log("Payment outcome uncertain; no retry",ex); continue; }
            if(!paid)continue;
            prepared.forEach(batch::commit);
            for(SaleLine line:lines)try{observer.accept(line);}catch(RuntimeException|LinkageError ex){log("Observer failed after payment",ex);}
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
