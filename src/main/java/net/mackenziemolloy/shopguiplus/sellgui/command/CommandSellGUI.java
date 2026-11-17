package net.mackenziemolloy.shopguiplus.sellgui.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Logger;

import com.tcoded.folialib.impl.PlatformScheduler;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.provider.economy.EconomyProvider;
import net.mackenziemolloy.shopguiplus.sellgui.SellGUI;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SellOffer;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SaleLine;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SaleResult;
import net.mackenziemolloy.shopguiplus.sellgui.objects.SaleBatch;
import net.mackenziemolloy.shopguiplus.sellgui.utility.SaleProcessor;
import net.mackenziemolloy.shopguiplus.sellgui.utility.*;
import net.mackenziemolloy.shopguiplus.sellgui.utility.sirblobman.HexColorUtility;
import net.mackenziemolloy.shopguiplus.sellgui.utility.sirblobman.MessageUtility;
import net.mackenziemolloy.shopguiplus.sellgui.utility.sirblobman.VersionUtility;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

@SuppressWarnings("deprecation")
public final class CommandSellGUI implements TabExecutor {

    private final SellGUI plugin;
    private final PlatformScheduler scheduler;
    private final List<ItemStack> failedReturnRecovery = new java.util.concurrent.CopyOnWriteArrayList<>();
    public List<ItemStack> getFailedReturnRecoverySnapshot() { return List.copyOf(failedReturnRecovery); }

    public CommandSellGUI(SellGUI plugin) {
        this.plugin = Objects.requireNonNull(plugin, "The plugin must not be null!");
        this.scheduler = SellGUI.scheduler();
    }

    public void register() {
        PluginCommand pluginCommand = this.plugin.getCommand("sellgui");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(this);
            pluginCommand.setTabCompleter(this);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            List<String> valueSet = new ArrayList<>(Arrays.asList("rl", "reload", "debug", "dump"));
            if (!(sender instanceof Player)) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    valueSet.add(online.getName());
                }
            }
            return StringUtil.copyPartialMatches(args[0], valueSet, new ArrayList<>());
        }

        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!plugin.compatible) {
            String message = MessageUtility.color("&7\n&7\n&a&lUPDATE REQUIRED \n&7\n&7Unfortunately &fSellGUI &7will not work until you update &cShopGUIPlus&7 to version &c1.78.0&7 or above.\n&7\n&eDownload: https://spigotmc.org/resources/6515/\n&7\n&7");
            sender.sendMessage(message);
            return false;
        }

        if (args.length == 0) {
            return commandBase(sender);
        }

        String sub = args[0].toLowerCase(Locale.US);
        return switch (sub) {
            case "rl", "reload" -> commandReload(sender);
            case "debug", "dump" -> commandDebug(sender);
            default -> commandOpenOther(sender, args[0]);
        };
    }

    private boolean commandReload(CommandSender sender) {
        if (!sender.hasPermission("sellgui.reload")) {
            sendMessage(sender, "no_permission");
            return true;
        }

        CompletableFuture.runAsync(this.plugin::generateFiles).whenComplete((success, error) -> {
            if (error != null) {
                sender.sendMessage(ChatColor.RED + "An error occurred, please check the server console.");
                error.printStackTrace();
                return;
            }

            if (!this.plugin.getConfiguration().getBoolean("options.transaction_log.enabled", false)) this.plugin.closeLogger();
            else if (this.plugin.fileLogger == null) this.plugin.initLogger();

            sendMessage(sender, "reloaded_config");
            if (sender instanceof Player player) {
                PlayerHandler.playSound(player, "success");
            }
        });
        return true;
    }

    private boolean commandDebug(CommandSender sender) {
        if (!sender.hasPermission("sellgui.dump")) {
            sendMessage(sender, "no_permission");
            return true;
        }

        CommentedConfiguration configuration = this.plugin.getConfiguration();
        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin[] pluginArray = pluginManager.getPlugins();
        List<String> pluginInfoList = new ArrayList<>();

        for (Plugin plugin : pluginArray) {
            String pluginName = plugin.getName();
            String pluginVersion = plugin.getDescription().getVersion();
            String pluginAuthorList = String.join(", ", plugin.getDescription().getAuthors());

            String pluginInfo = String.format(Locale.US, "- %s v%s by %s", pluginName, pluginVersion,
                    pluginAuthorList);
            pluginInfoList.add(pluginInfo);
        }

        String pasteRaw = "| System Information\n\n" + "- OS Type: " + System.getProperty("os.name") + "\n" +
                "- OS Version: " + System.getProperty("os.version") +
                " (" + System.getProperty("os.arch") + ")\n" +
                "- Processor: " + System.getenv("PROCESSOR_IDENTIFIER") +
                "\n\n| Server Information\n\n" +
                "- Version: " + Bukkit.getBukkitVersion() + "\n" +
                "- Online Mode: " + Bukkit.getOnlineMode() + "\n" +
                "- Memory Usage: " + getMemoryUsage() +
                "\n\n| Plugins\n" + String.join("\n", pluginInfoList) +
                "\n\n| Plugin Configuration\n\n" + configuration.saveToString();
        try {
            String pasteUrl = new Hastebin().post(pasteRaw, true);
            String pastedDumpMsg = ChatColor.translateAlternateColorCodes('&',
                    "&c[ShopGUIPlus-SellGUI] Successfully dumped server information here: %s.");

            String message = String.format(Locale.US, pastedDumpMsg, pasteUrl);
            Bukkit.getConsoleSender().sendMessage(message);
            if (sender instanceof Player) sender.sendMessage(message);

            if (sender instanceof Player player) {
                PlayerHandler.playSound(player, "success");
            }
        } catch (IOException ex) {
            sender.sendMessage(ChatColor.RED + "An error occurred, please check the console:");
            ex.printStackTrace();
        }

        return true;
    }

    private boolean commandBase(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can execute this command.");
            return true;
        }

        if (!player.hasPermission("sellgui.use")) {
            sendMessage(player, "no_permission");
            return true;
        }

        if (!checkGameMode(player)) {
            return true;
        }

        openSellGuiFor(player);
        return true;
    }

    private boolean commandOpenOther(CommandSender sender, String targetName) {
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.RED + "The Sell GUI can only be opened for another player from the console.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + targetName + "' is not online.");
            return true;
        }

        openSellGuiFor(target);
        sender.sendMessage(ChatColor.GREEN + "Opened the Sell GUI for " + target.getName() + ".");
        return true;
    }

    private void openSellGuiFor(Player player) {
        CommentedConfiguration configuration = this.plugin.getConfiguration();
        int guiSize = configuration.getInt("options.rows", 6);
        if (guiSize > 6 || guiSize < 1) {
            guiSize = 6;
        }

        String sellGuiTitle = getMessage("sellgui_title", null);
        Component sellGuiTitleComponent = LegacyComponentSerializer.legacySection().deserialize(sellGuiTitle);
        Gui gui = Gui.gui().title(sellGuiTitleComponent).rows(guiSize).create();
        PlayerHandler.playSound(player, "open");

        Set<Integer> ignoredSlotSet = new HashSet<>();
        setDecorationItems(configuration, gui, ignoredSlotSet);
        AtomicBoolean closeHandled = new AtomicBoolean();
        gui.setCloseGuiAction(event -> {
            if (closeHandled.compareAndSet(false, true)) onGuiClose(player, event, ignoredSlotSet);
        });

        scheduler.runAtEntity(player, task -> gui.open(player));
    }

    private boolean checkGameMode(Player player) {
        ShopGuiPlugin shopGui = ShopGuiPlusApi.getPlugin();
        FileConfiguration configuration = shopGui.getConfigMain().getConfig();
        List<String> disabledGameModeList = configuration.getStringList("disableShopsInGamemodes");

        GameMode gameMode = player.getGameMode();
        String gameModeName = gameMode.name();
        if (disabledGameModeList.contains(gameModeName)) {
            String gameModeFormatted = StringFormatter.capitalize(gameModeName);
            sendMessage(player, "gamemode_not_allowed", message ->
                    message.replace("{gamemode}", gameModeFormatted));
            return false;
        }

        return true;
    }

    private void setDecorationItems(ConfigurationSection configuration, Gui gui, Set<Integer> ignoredSlotSet) {
        ConfigurationSection sectionDecorations = configuration.getConfigurationSection("options.decorations");
        if (sectionDecorations == null) {
            return;
        }

        Set<String> sectionDecorationsKeys = sectionDecorations.getKeys(false);
        for (String key : sectionDecorationsKeys) {
            ConfigurationSection section = sectionDecorations.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            Material material;
            String materialName = section.getString("item.material");
            if (materialName == null || (material = Material.matchMaterial(materialName)) == null
                    || !section.isInt("slot") || section.getInt("slot") > ((gui.getRows() * 9) - 1)
                    || section.getInt("slot") < 0) {
                Logger logger = this.plugin.getLogger();
                logger.warning("Failed to load decoration item with id '" + key + "'.");
                continue;
            }

            ItemStack item = new ItemStack(material);
            setItemDamage(item, section.getInt("item.damage", 0));
            item.setAmount(section.getInt("item.quantity", 1));

            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta != null) {
                String displayName = section.getString("item.name");
                if (displayName != null) {
                    displayName = MessageUtility.color(displayName);
                    itemMeta.setDisplayName(displayName);
                }

                List<String> loreList = section.getStringList("item.lore");
                if (!loreList.isEmpty()) {
                    List<String> processedLore = new ArrayList<>(loreList.size());
                    for (String line : loreList) {
                        processedLore.add(MessageUtility.color(HexColorUtility.replaceHexColors('&', line)));
                    }

                    itemMeta.setLore(processedLore);
                }

                int customModelData = section.getInt("item.customModelData");
                if (customModelData != 0) {
                    itemMeta.setCustomModelData(customModelData);
                }

                item.setItemMeta(itemMeta);
            }

            List<String> consoleCommandList = section.getStringList("commandsOnClickConsole");
            List<String> playerCommandList = section.getStringList("commandsOnClick");

            GuiItem guiItem = new GuiItem(item, e -> {
                e.setCancelled(true);
                HumanEntity human = e.getWhoClicked();
                String humanName = human.getName();

                for (String consoleCommand : consoleCommandList) {
                    String command = consoleCommand.replace("%PLAYER%", humanName);
                    scheduler.runNextTick(task -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
                }

                for (String playerCommand : playerCommandList) {
                    String command = playerCommand.replace("%PLAYER%", humanName);
                    scheduler.runAtEntity(human, task -> Bukkit.dispatchCommand(human, command));
                }

                if (section.getBoolean("item.sellinventory")) {
                    scheduler.runAtEntity(human, task -> human.closeInventory());
                    commandBase(Bukkit.getPlayer(humanName));
                }
            });

            int slot = section.getInt("slot");
            gui.setItem(slot, guiItem);

            ignoredSlotSet.add(slot);
        }
    }

    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        long usedMemory = (totalMemory - freeMemory);
        long usedMemoryMB = (usedMemory / 1_048_576L);
        long maxMemoryMB = (maxMemory / 1_048_576L);

        return String.format(Locale.US, "%s / %s MiB", usedMemoryMB, maxMemoryMB);
    }

    private void setItemDamage(ItemStack item, int damage) {
        int minorVersion = VersionUtility.getMinorVersion();
        if (minorVersion < 13) {
            short durability = (short) damage;
            item.setDurability(durability);
            return;
        }

        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta instanceof Damageable) {
            ((Damageable) itemMeta).setDamage(damage);
            item.setItemMeta(itemMeta);
        }
    }

    private void onGuiClose(Player player, InventoryCloseEvent event, Set<Integer> ignoredSlotSet) {
        int minorVersion = VersionUtility.getMinorVersion();
        CommentedConfiguration configuration = this.plugin.getConfiguration();
        Map<ItemStack, SellOffer> bestOfferCache = new HashMap<>();
        List<SaleLine> paidLines = new ArrayList<>();
        SaleBatch batch = new SaleBatch();
        Map<EconomyType, Double> moneyMap = new EnumMap<>(EconomyType.class);
        double totalPrice = 0;
        int itemAmount = 0;
        boolean excessItems = false;
        boolean itemsPlacedInGui = false;
        Inventory inventory = event.getInventory();

        // Drain every input slot before any payment, preventing re-close from processing it twice.
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || ignoredSlotSet.contains(slot)) continue;
            itemsPlacedInGui = true;
            inventory.setItem(slot, null);
            batch.addInput(item);
        }

        SaleProcessor processor = new SaleProcessor(item -> {
            ItemStack key = item.clone();
            key.setAmount(1);
            if (bestOfferCache.containsKey(key)) return bestOfferCache.get(key);
            SellOffer offer = ShopHandler.findBestSellOffer(player, key);
            bestOfferCache.put(key, offer);
            return offer;
        }, (economy, amount) -> {
            EconomyProvider provider = ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(economy);
            if (provider == null || !Double.isFinite(amount) || amount <= 0) return false;
            try { provider.deposit(player, amount); return true; }
            catch (RuntimeException | LinkageError ex) {
                plugin.getLogger().warning("SellGUI payment failed; contents are preserved without retry: " + ex.getMessage());
                return false;
            }
        }, line -> { });

        Location returnLocation = player.getLocation().add(0.0D, 0.5D, 0.0D);
        World returnWorld = returnLocation.getWorld();
        try {
            processor.processAll(batch);
        } finally {
            for (int index = 0; index < batch.size(); index++) {
                SaleResult result = batch.getResult(index);
                paidLines.addAll(result.getLines());
                ItemStack ownedReturn = result.getReturnedItem();
                if (ownedReturn == null) continue;
                try {
                    Map<Integer, ItemStack> overflow = player.getInventory().addItem(ownedReturn);
                    if (!overflow.isEmpty()) {
                        excessItems = true;
                        List<ItemStack> overflowItems = new ArrayList<>(overflow.values());
                        try {
                            if (returnWorld != null) scheduler.runAtLocation(returnLocation, task -> overflowItems.forEach(stack -> returnWorld.dropItemNaturally(returnLocation, stack)));
                            else failedReturnRecovery.addAll(overflowItems);
                        } catch (RuntimeException | LinkageError dropFailure) { failedReturnRecovery.addAll(overflowItems); safeWarn("Could not schedule overflow drop", dropFailure); }
                    }
                } catch (RuntimeException | LinkageError returnFailure) { failedReturnRecovery.add(ownedReturn); safeWarn("Could not return GUI item for " + player.getName() + " (" + player.getUniqueId() + "); inspect recovery snapshot", returnFailure); }
            }
        }
        if (excessItems) sendMessage(player, "inventory_full");

        for (SaleLine line : paidLines) {
            totalPrice += line.getTotalPrice();
            itemAmount += line.getAmount();
            moneyMap.merge(line.getEconomyType(), line.getTotalPrice(), Double::sum);
        }
        if (totalPrice <= 0) {
            PlayerHandler.playSound(player, "failed");
            sendMessage(player, itemsPlacedInGui ? "no_items_sold" : "no_items_in_gui");
            return;
        }

        PlayerHandler.playSound(player, "success");
        StringBuilder formattedPricing = new StringBuilder();
        for (Entry<EconomyType, Double> entry : moneyMap.entrySet()) {
            EconomyProvider provider = ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(entry.getKey());
            if (provider == null) continue;
            formattedPricing.append(provider.getCurrencyPrefix()).append(StringFormatter.getFormattedNumber(entry.getValue()))
                    .append(provider.getCurrencySuffix()).append(", ");
        }
        if (formattedPricing.toString().endsWith(", "))
            formattedPricing = new StringBuilder(formattedPricing.substring(0, formattedPricing.length() - 2));
        String formattedPricingValue = formattedPricing.toString();
        int soldItemAmount = itemAmount;

        // Notify once per item/offer after all quotes from this close have been captured.
        Map<ItemStack, List<SaleLine>> notificationGroups = new LinkedHashMap<>();
        for (SaleLine line : paidLines) {
            ItemStack key = line.getItem();
            key.setAmount(1);
            notificationGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(line);
        }
        for (List<SaleLine> group : notificationGroups.values()) {
            SaleLine first = group.get(0);
            int amount = group.stream().mapToInt(SaleLine::getAmount).sum();
            double paid = group.stream().mapToDouble(SaleLine::getTotalPrice).sum();
            DynaShopHandler.notifySale(player, first.getShopItem(), first.getItem(), amount, paid);
        }

        List<String> receiptList = new LinkedList<>();
        List<String> itemList = new LinkedList<>();
        if (configuration.getInt("options.receipt_type", 0) == 1
                || configuration.getString("messages.items_sold", "").contains("{list}")) {
            Map<ItemStack, List<SaleLine>> receiptGroups = new LinkedHashMap<>();
            for (SaleLine line : paidLines) {
                ItemStack key = line.getItem(); key.setAmount(1);
                receiptGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(line);
            }
            for (Entry<ItemStack, List<SaleLine>> entry : receiptGroups.entrySet()) {
                ItemStack soldItem = entry.getKey();
                int amount = entry.getValue().stream().mapToInt(SaleLine::getAmount).sum();
                double profits = entry.getValue().stream().mapToDouble(SaleLine::getTotalPrice).sum();
                EconomyProvider provider = ShopGuiPlusApi.getPlugin().getEconomyManager()
                        .getEconomyProvider(entry.getValue().get(0).getEconomyType());
                String price = (provider == null ? "" : provider.getCurrencyPrefix()) + StringFormatter.getFormattedNumber(profits)
                        + (provider == null ? "" : provider.getCurrencySuffix());
                String name = StringFormatter.capitalize(soldItem.getType().name().replace("AETHER_LEGACY_", "")
                        .replace("LOST_AETHER_", "").replace("_", " ").toLowerCase());
                ItemMeta meta = soldItem.getItemMeta();
                if (meta != null && meta.hasDisplayName() && !meta.getDisplayName().isEmpty()) name = meta.getDisplayName();
                if (minorVersion <= 12 && !configuration.getBoolean("options.show_item_damage", false))
                    name += ":" + soldItem.getDurability();
                String finalName = name;
                receiptList.add(getMessage("receipt_item_layout", message -> message.replace("{amount}", String.valueOf(amount))
                        .replace("{item}", finalName).replace("{price}", price)));
                itemList.add(name);
            }
        }

        String itemAmountFormatted = StringFormatter.getFormattedNumber((double) itemAmount);
        if (configuration.getInt("options.receipt_type", 0) == 1) {
            TextComponent itemsSoldComponent = getTextComponentMessage("items_sold", message -> message
                    .replace("{earning}", formattedPricingValue).replace("{receipt}", "")
                    .replace("{list}", String.join(", ", itemList)).replace("{amount}", String.valueOf(soldItemAmount)));
            itemsSoldComponent.addExtra(" ");
            String hover = getMessage("receipt_title", null) + ChatColor.RESET + String.join("\n", receiptList) + ChatColor.RESET;
            TextComponent receiptName = getTextComponentMessage("receipt_text", null);
            receiptName.setHoverEvent(new HoverEvent(Action.SHOW_TEXT, TextComponent.fromLegacyText(hover)));
            sendMessage(player, Arrays.asList(itemsSoldComponent, receiptName));
        } else {
            sendMessage(player, "items_sold", message -> message.replace("{earning}", formattedPricingValue)
                    .replace("{receipt}", "").replace("{list}", String.join(", ", itemList)).replace("{amount}", itemAmountFormatted));
        }

        if (plugin.fileLogger != null) plugin.fileLogger.info(player.getName() + " (" + player.getUniqueId()
                + ") sold: {" + HexColorUtility.purgeAllColor(String.join(", ", receiptList)) + "}");
        if (configuration.getBoolean("options.sell_titles", false)) sendSellTitles(player, formattedPricing, itemAmountFormatted);
        if (configuration.getBoolean("options.action_bar_msgs", false) && minorVersion >= 9)
            sendActionBar(player, formattedPricing, itemAmountFormatted);
    }
    private void safeWarn(String message, Throwable error) { try { plugin.getLogger().warning("SellGUI " + message + ": " + error.getMessage()); } catch (RuntimeException ignored) { } }
    private String getMessage(String path, @Nullable Function<String, String> replacer) {
        CommentedConfiguration configuration = this.plugin.getConfiguration();
        String message = configuration.getString("messages." + path, "");
        if (message.isEmpty()) {
            return "";
        }

        if (replacer != null) {
            message = replacer.apply(message);
        }

        return MessageUtility.color(HexColorUtility.replaceHexColors('&', message));
    }

    private TextComponent getTextComponentMessage(String path, @Nullable Function<String, String> replacer) {
        String message = getMessage(path, replacer);
        if (message.isEmpty()) {
            return new TextComponent("");
        } else {
            BaseComponent[] components = TextComponent.fromLegacyText(message);
            TextComponent root = new TextComponent("");
            for (BaseComponent component : components) {
                root.addExtra(component);
            }

            return root;
        }
    }

    private void sendMessage(CommandSender sender, String path) {
        sendMessage(sender, path, null);
    }

    private void sendMessage(CommandSender sender, String path, @Nullable Function<String, String> replacer) {
        String message = getMessage(path, replacer);
        if (message.isEmpty()) {
            return;
        }

        if (sender instanceof Player player) {
            BaseComponent[] components = TextComponent.fromLegacyText(message);
            player.spigot().sendMessage(components);
        } else {
            sender.sendMessage(message);
        }
    }

    private void sendMessage(Player player, List<TextComponent> textComponents) {
        boolean isTextPresent = textComponents.stream()
                .anyMatch(component -> component.getText() != null && !component.getText().isEmpty()
                        || component.getExtra() != null && !component.getExtra().isEmpty());

        if (!isTextPresent) {
            return;
        }

        player.spigot().sendMessage(textComponents.toArray(new BaseComponent[0]));
    }

    private void sendSellTitles(Player player, CharSequence price, String amount) {
        Function<String, String> replacer = message -> message.replace("{earning}", price)
                .replace("{amount}", amount);

        String title = getMessage("sell_title", replacer);
        String subtitle = getMessage("sell_subtitle", replacer);
        player.sendTitle(title, subtitle);
    }

    private void sendActionBar(Player player, CharSequence price, String amount) {
        Function<String, String> replacer = message -> message.replace("{earning}", price)
                .replace("{amount}", amount);

        TextComponent message = getTextComponentMessage("action_bar_items_sold", replacer);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, message);
    }
}



