package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import java.util.Set;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoMine+ — Tự động hóa chu kỳ: đào → bán rác → chia kho → lưu Shulker/Ender Chest → lặp lại.
 * Dùng Baritone (#mine, #stop) để di chuyển và đào.
 */
public class AutoMine extends Module {

    // =========================================================
    //  Setting Groups
    // =========================================================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgShop    = settings.createGroup("Shop Settings");
    private final SettingGroup sgTrash   = settings.createGroup("Trash Items");

    // ── General ──────────────────────────────────────────────
    private final Setting<Item> mineBlock = sgGeneral.add(new ItemSetting.Builder()
        .name("mine-block")
        .description("Khối quặng cho Baritone #mine (luôn là ore block: DIAMOND_ORE, IRON_ORE...)"
            + " — dùng để đào, không phải để đếm.")
        .defaultValue(Items.DIAMOND_ORE)
        .build());

    private final Setting<Boolean> autoDetectDrop = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-detect-drop")
        .description("Tự động nhận biết: có Silk Touch trong hotbar → đếm khối ore;"
            + " không có Silk Touch → đếm item rơi ra (DIAMOND, RAW_IRON...).")
        .defaultValue(true)
        .build());

    private final Setting<Item> collectItem = sgGeneral.add(new ItemSetting.Builder()
        .name("collect-item")
        .description("Item cần đếm/cất khi tắt auto-detect."
            + " Ví dụ: DIAMOND nếu Fortune, DIAMOND_ORE nếu Silk Touch.")
        .defaultValue(Items.DIAMOND)
        .visible(() -> !autoDetectDrop.get())
        .build());

    private final Setting<Integer> collectAmount = sgGeneral.add(new IntSetting.Builder()
        .name("collect-amount")
        .description("Số lượng item gom được trước khi dừng và bắt đầu chia kho.")
        .defaultValue(27).min(1).sliderMax(64)
        .build());

    private final Setting<Integer> keepAmount = sgGeneral.add(new IntSetting.Builder()
        .name("keep-amount")
        .description("Số item giữ lại trong người sau khi cất vào Shulker (27–64 để tiếp tục chia kho).")
        .defaultValue(27).min(27).sliderMax(64)
        .build());

    private final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Độ trễ (ticks) giữa các thao tác click slot.")
        .defaultValue(5).min(1).sliderMax(20)
        .build());

    // Ender Chest không cần nhặt lại — mua mới mỗi chu kỳ qua /shop.

    // ── Shop Settings ─────────────────────────────────────────
    private final Setting<Integer> shopCategorySlot = sgShop.add(new IntSetting.Builder()
        .name("shop-category-slot")
        .description("Slot danh mục Ender Chest trong /shop (0-indexed).")
        .defaultValue(11).min(0).sliderMax(53)
        .build());

    private final Setting<Integer> shopEnderChestSlot = sgShop.add(new IntSetting.Builder()
        .name("shop-enderchest-slot")
        .description("Slot item Ender Chest trong shop (0-indexed).")
        .defaultValue(9).min(0).sliderMax(53)
        .build());

    private final Setting<Integer> shopShulkerSlot = sgShop.add(new IntSetting.Builder()
        .name("shop-shulker-slot")
        .description("Slot item Shulker Box trong shop (0-indexed).")
        .defaultValue(17).min(0).sliderMax(53)
        .build());

    private final Setting<Integer> shopConfirmSlot = sgShop.add(new IntSetting.Builder()
        .name("shop-confirm-slot")
        .description("Slot xác nhận mua trong shop (0-indexed).")
        .defaultValue(23).min(0).sliderMax(53)
        .build());

    private final Setting<Integer> shopBackSlot = sgShop.add(new IntSetting.Builder()
        .name("shop-back-slot")
        .description("Slot quay lại trang trước trong shop (0-indexed).")
        .defaultValue(21).min(0).sliderMax(53)
        .build());

    // ── Auto EXP Settings ─────────────────────────────────────
    private final SettingGroup sgExp = settings.createGroup("Auto Mending (EXP)");

    private final Setting<Boolean> autoExpBuy = sgExp.add(new BoolSetting.Builder()
        .name("auto-buy-exp")
        .description("Tự động check và mua 2 stack EXP vào slot 6 và 7.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> xpSlot1 = sgExp.add(new IntSetting.Builder()
        .name("xp-slot-1")
        .description("Slot hotbar chứa EXP stack 1 (mặc định 6 tức là index 5).")
        .defaultValue(5).min(0).sliderMax(8)
        .build());

    private final Setting<Integer> xpSlot2 = sgExp.add(new IntSetting.Builder()
        .name("xp-slot-2")
        .description("Slot hotbar chứa EXP stack 2 (mặc định 7 tức là index 6).")
        .defaultValue(6).min(0).sliderMax(8)
        .build());

    private final Setting<Integer> shopExpCategorySlot = sgExp.add(new IntSetting.Builder()
        .name("shop-exp-category-slot")
        .description("Slot danh mục bán EXP trong /shop.")
        .defaultValue(13).min(0).sliderMax(53)
        .build());

    private final Setting<Integer> shopExpItemSlot = sgExp.add(new IntSetting.Builder()
        .name("shop-exp-item-slot")
        .description("Slot bình EXP trong danh mục.")
        .defaultValue(16).min(0).sliderMax(53)
        .build());

    // ── Trash Items ───────────────────────────────────────────
    private final Setting<List<Item>> trashItems = sgTrash.add(new ItemListSetting.Builder()
        .name("trash-items")
        .description("Các item rác sẽ được shift-click vào GUI /sell để bán.")
        .defaultValue(
            Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL,
            Items.STONE, Items.NETHERRACK, Items.DIORITE, Items.ANDESITE,
            Items.GRANITE, Items.COBBLED_DEEPSLATE, Items.DEEPSLATE,
            Items.ROTTEN_FLESH, Items.BONE, Items.ARROW,
            Items.GUNPOWDER, Items.STRING, Items.SPIDER_EYE,
            Items.BLAZE_ROD, Items.ENDER_PEARL
        )
        .build());

    // =========================================================
    //  State Machine
    // =========================================================
    private enum State {
        IDLE,
        CHECK_FULL,
        CHECK_EXP,
        OPEN_SELL_EXP_SLOTS,
        SELL_EXP_EXP_SLOTS_GUI,
        OPEN_SHOP_EXP,
        SHOP_EXP_CATEGORY,
        SHOP_EXP_ITEM,
        SHOP_EXP_BUY,
        START_MINE,
        MINING,
        WAIT_REPAIR,
        STOP_MINE,
        WAIT_STOP,
        OPEN_SELL_TRASH,
        SELL_TRASH_GUI,
        DISTRIBUTE_ITEMS,
        CHECK_HOTBAR_89,
        THROW_EXP_HOTBAR78,
        OPEN_SELL_HOTBAR89,
        SELL_HOTBAR89_GUI,
        OPEN_SHOP,
        SHOP_CLICK_CATEGORY,
        SHOP_CLICK_ENDER,
        SHOP_FIX_SLOT7,
        SHOP_CONFIRM_ENDER,
        SHOP_CLICK_BACK,
        SHOP_CLICK_SHULKER,
        SHOP_FIX_SLOT8,
        SHOP_CONFIRM_SHULKER,
        CLOSE_SHOP,
        FIND_SHULKER_PLACE,
        PLACE_SHULKER,
        OPEN_SHULKER,
        STORE_IN_SHULKER_GUI,
        BREAK_SHULKER,
        WAIT_SHULKER_PICKUP,
        RETRY_SHULKER_PICKUP,
        SELL_TRASH_FOR_SHULKER_GUI,
        FIND_ENDER_PLACE,
        PLACE_ENDER,
        OPEN_ENDER,
        STORE_SHULKER_ENDER_GUI,
        RESTART_CYCLE,
    }

    private State currentState = State.IDLE;
    private int   timer        = 0;
    private int          distSubState   = 0;
    private int          distSourceSlot = -1;
    private List<Integer> distTargetSlots = new ArrayList<>();
    private int          distTargetIdx  = 0;
    private boolean      hasDistributed = false;
    private BlockPos placedShulkerPos = null;
    private BlockPos placedEnderPos   = null;
    private int lastXpCount = 0;
    private int repairWaitTicks = 0;
    private int shulkerPickupTimeout = 0;
    private final Set<BlockPos> blacklistedSpots = new java.util.HashSet<>();

    public AutoMine() {
        super(AddonTemplate.CATEGORY, "AutoMine+", "Tự động hóa đào, sửa, bán, cất kho.");
    }

    @Override
    public void onActivate() {
        currentState = State.IDLE;
        timer = 0;
        blacklistedSpots.clear();
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null && mc.options.attackKey != null) mc.options.attackKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (timer > 0) { timer--; return; }

        switch (currentState) {
            case IDLE:
                currentState = State.CHECK_EXP;
                break;

            case CHECK_EXP:
                lastXpCount = countXpBottles();
                if (autoExpBuy.get() && lastXpCount < 128) {
                    currentState = expSlotsHaveTrash() ? State.OPEN_SELL_EXP_SLOTS : State.OPEN_SHOP_EXP;
                } else {
                    currentState = State.START_MINE;
                }
                break;

            case OPEN_SELL_EXP_SLOTS:
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                ChatUtils.sendPlayerMsg("/sell");
                timer = 40;
                currentState = State.SELL_EXP_EXP_SLOTS_GUI;
                break;

            case SELL_EXP_EXP_SLOTS_GUI:
                if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) {
                    currentState = State.OPEN_SHOP_EXP;
                    break;
                }
                net.minecraft.screen.ScreenHandler sellExpH = ((net.minecraft.client.gui.screen.ingame.HandledScreen<?>) mc.currentScreen).getScreenHandler();
                int cszExp = sellExpH.slots.size() - 36;
                boolean threwExpTrash = false;
                for (int hIdx : new int[]{xpSlot1.get(), xpSlot2.get()}) {
                    int handlerSlot = cszExp + 27 + hIdx;
                    if (handlerSlot < sellExpH.slots.size() && sellExpH.getSlot(handlerSlot).hasStack() && sellExpH.getSlot(handlerSlot).getStack().getItem() != Items.EXPERIENCE_BOTTLE) {
                        mc.interactionManager.clickSlot(sellExpH.syncId, handlerSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                        timer = actionDelay.get();
                        threwExpTrash = true;
                        break;
                    }
                }
                if (!threwExpTrash) {
                    mc.player.closeHandledScreen();
                    timer = 15;
                    currentState = State.OPEN_SHOP_EXP;
                }
                break;

            case OPEN_SHOP_EXP:
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                ChatUtils.sendPlayerMsg("/shop");
                timer = 40;
                currentState = State.SHOP_EXP_CATEGORY;
                break;

            case SHOP_EXP_CATEGORY:
                if (!assertContainerOpen(State.OPEN_SHOP_EXP)) break;
                clickContainerSlot(shopExpCategorySlot.get());
                timer = actionDelay.get() * 4;
                currentState = State.SHOP_EXP_ITEM;
                break;

            case SHOP_EXP_ITEM:
                if (!assertContainerOpen(State.OPEN_SHOP_EXP)) break;
                clickContainerSlot(shopExpItemSlot.get());
                timer = actionDelay.get() * 4;
                currentState = State.SHOP_EXP_BUY;
                break;

            case SHOP_EXP_BUY:
                if (!assertContainerOpen(State.OPEN_SHOP_EXP)) break;
                if (countXpBottles() < 128) {
                    clickContainerSlot(shopConfirmSlot.get());
                    timer = actionDelay.get() * 3;
                } else {
                    mc.player.closeHandledScreen();
                    timer = 15;
                    currentState = State.START_MINE;
                }
                break;

            case START_MINE:
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                ChatUtils.sendPlayerMsg("#mine " + Registries.ITEM.getId(mineBlock.get()).toString());
                timer = 60;
                currentState = State.MINING;
                break;

            case MINING:
                if (!isDistributedProperly()) {
                    if (countTargetItems() >= collectAmount.get()) currentState = State.STOP_MINE;
                } else {
                    if (isMainInventoryFull()) currentState = State.STOP_MINE;
                }
                int currentXp = countXpBottles();
                if (currentXp < lastXpCount || mc.player.getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE) {
                    ChatUtils.sendPlayerMsg("#stop");
                    repairWaitTicks = 0;
                    currentState = State.WAIT_REPAIR;
                }
                lastXpCount = currentXp;
                break;

            case WAIT_REPAIR:
                int curXp = countXpBottles();
                if (curXp >= lastXpCount) repairWaitTicks++;
                lastXpCount = curXp;
                if (repairWaitTicks >= 100 || curXp == 0) currentState = State.START_MINE;
                break;

            case STOP_MINE:
                ChatUtils.sendPlayerMsg("#stop");
                timer = 40;
                currentState = State.WAIT_STOP;
                break;

            case WAIT_STOP:
                currentState = hasTrashInMainInv() ? State.OPEN_SELL_TRASH : State.DISTRIBUTE_ITEMS;
                break;

            case OPEN_SELL_TRASH:
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                ChatUtils.sendPlayerMsg("/sell");
                timer = 40;
                currentState = State.SELL_TRASH_GUI;
                break;

            case SELL_TRASH_GUI:
                if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) {
                    currentState = State.DISTRIBUTE_ITEMS;
                    break;
                }
                if (!doSellTrashFromPlayerInv((net.minecraft.client.gui.screen.ingame.HandledScreen<?>) mc.currentScreen)) {
                    mc.player.closeHandledScreen();
                    timer = 15;
                    currentState = State.DISTRIBUTE_ITEMS;
                }
                break;

            case DISTRIBUTE_ITEMS:
                doDistribute();
                break;

            case CHECK_FULL:
                if (isMainInventoryFull()) currentState = State.CHECK_HOTBAR_89;
                else currentState = State.CHECK_EXP;
                break;

            case CHECK_HOTBAR_89:
                if (getExpInHotbar78() != -1) {
                    currentState = State.THROW_EXP_HOTBAR78;
                } else {
                    currentState = hotbar78HasUnwantedItems() ? State.OPEN_SELL_HOTBAR89 : State.OPEN_SHOP;
                }
                break;

            case THROW_EXP_HOTBAR78:
                int expSlot = getExpInHotbar78();
                if (expSlot != -1) {
                    InvUtils.swap(expSlot, false);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    timer = 4;
                } else {
                    currentState = State.CHECK_HOTBAR_89;
                }
                break;

            case OPEN_SELL_HOTBAR89:
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                ChatUtils.sendPlayerMsg("/sell");
                timer = 40;
                currentState = State.SELL_HOTBAR89_GUI;
                break;

            case SELL_HOTBAR89_GUI:
                if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) {
                    currentState = State.OPEN_SHOP;
                    break;
                }
                net.minecraft.screen.ScreenHandler sellH = ((net.minecraft.client.gui.screen.ingame.HandledScreen<?>) mc.currentScreen).getScreenHandler();
                int csz = sellH.slots.size() - 36;
                boolean threwAny = false;
                for (int hIdx : new int[]{7, 8}) {
                    int handlerSlot = csz + 27 + hIdx;
                    if (handlerSlot < sellH.slots.size() && sellH.getSlot(handlerSlot).hasStack()) {
                        mc.interactionManager.clickSlot(sellH.syncId, handlerSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                        timer = actionDelay.get();
                        threwAny = true;
                        break;
                    }
                }
                if (!threwAny) {
                    mc.player.closeHandledScreen();
                    timer = 15;
                    currentState = State.OPEN_SHOP;
                }
                break;

            case OPEN_SHOP:
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                ChatUtils.sendPlayerMsg("/shop");
                timer = 50;
                currentState = State.SHOP_CLICK_CATEGORY;
                break;

            case SHOP_CLICK_CATEGORY:
                if (!assertContainerOpen(State.OPEN_SHOP)) break;
                clickContainerSlot(shopCategorySlot.get());
                timer = actionDelay.get() * 4;
                currentState = hasEnderChestInHotbar() ? State.SHOP_CLICK_SHULKER : State.SHOP_CLICK_ENDER;
                break;

            case SHOP_CLICK_ENDER:
                if (!assertContainerOpen(State.OPEN_SHOP)) break;
                clickContainerSlot(shopEnderChestSlot.get());
                timer = actionDelay.get() * 2;
                currentState = State.SHOP_FIX_SLOT7;
                break;

            case SHOP_FIX_SLOT7:
                if (isSlotUnwanted(7)) { currentState = State.OPEN_SELL_TRASH; break; }
                currentState = State.SHOP_CONFIRM_ENDER;
                break;

            case SHOP_CONFIRM_ENDER:
                if (!assertContainerOpen(State.OPEN_SHOP)) break;
                clickContainerSlot(shopConfirmSlot.get());
                timer = actionDelay.get() * 4;
                currentState = State.SHOP_CLICK_BACK;
                break;

            case SHOP_CLICK_BACK:
                if (!assertContainerOpen(State.OPEN_SHOP)) break;
                clickContainerSlot(shopBackSlot.get());
                timer = actionDelay.get() * 4;
                currentState = State.SHOP_CLICK_SHULKER;
                break;

            case SHOP_CLICK_SHULKER:
                if (!assertContainerOpen(State.OPEN_SHOP)) break;
                clickContainerSlot(shopShulkerSlot.get());
                timer = actionDelay.get() * 2;
                currentState = State.SHOP_FIX_SLOT8;
                break;

            case SHOP_FIX_SLOT8:
                if (isSlotUnwanted(8)) { currentState = State.OPEN_SELL_TRASH; break; }
                currentState = State.SHOP_CONFIRM_SHULKER;
                break;

            case SHOP_CONFIRM_SHULKER:
                if (!assertContainerOpen(State.OPEN_SHOP)) break;
                clickContainerSlot(shopConfirmSlot.get());
                timer = actionDelay.get() * 4;
                currentState = State.CLOSE_SHOP;
                break;

            case CLOSE_SHOP:
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                timer = 15;
                currentState = State.FIND_SHULKER_PLACE;
                break;

            case FIND_SHULKER_PLACE:
                placedShulkerPos = findPlacementSpot();
                if (placedShulkerPos == null) { timer = 60; break; }
                currentState = State.PLACE_SHULKER;
                break;

            case PLACE_SHULKER:
                if (placedShulkerPos == null) { currentState = State.FIND_SHULKER_PLACE; break; }
                meteordevelopment.meteorclient.utils.player.FindItemResult shulker = meteordevelopment.meteorclient.utils.player.InvUtils.findInHotbar(s -> isShulkerItem(s));
                if (!shulker.found()) { toggle(); return; }
                meteordevelopment.meteorclient.utils.player.InvUtils.swap(shulker.slot(), false);
                rotateAndPlace(placedShulkerPos);
                timer = 15;
                currentState = State.OPEN_SHULKER;
                break;

            case OPEN_SHULKER:
                if (placedShulkerPos == null || !isShulkerBlock(placedShulkerPos)) {
                    if (placedShulkerPos != null) blacklistedSpots.add(placedShulkerPos);
                    placedShulkerPos = null;
                    currentState = State.FIND_SHULKER_PLACE;
                    break;
                }
                interactBlock(placedShulkerPos);
                timer = 25;
                currentState = State.STORE_IN_SHULKER_GUI;
                break;

            case STORE_IN_SHULKER_GUI:
                if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) break;
                if (!doStoreItemsToContainer((net.minecraft.client.gui.screen.ingame.HandledScreen<?>) mc.currentScreen)) {
                    mc.player.closeHandledScreen();
                    timer = 15;
                    currentState = State.BREAK_SHULKER;
                }
                break;

            case BREAK_SHULKER:
                ChatUtils.sendPlayerMsg("#mine minecraft:shulker_box");
                shulkerPickupTimeout = 200;
                currentState = State.WAIT_SHULKER_PICKUP;
                break;

            case WAIT_SHULKER_PICKUP:
                if (hasShulkerInInventory()) {
                    ChatUtils.sendPlayerMsg("#stop");
                    currentState = State.FIND_ENDER_PLACE;
                } else if (--shulkerPickupTimeout <= 0) currentState = State.RETRY_SHULKER_PICKUP;
                break;

            case RETRY_SHULKER_PICKUP:
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                ChatUtils.sendPlayerMsg("/sell");
                timer = 40;
                currentState = State.SELL_TRASH_FOR_SHULKER_GUI;
                break;

            case SELL_TRASH_FOR_SHULKER_GUI:
                if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) {
                    shulkerPickupTimeout = 100;
                    currentState = State.WAIT_SHULKER_PICKUP;
                    break;
                }
                if (!doSellTrashFromPlayerInv((net.minecraft.client.gui.screen.ingame.HandledScreen<?>) mc.currentScreen)) {
                    mc.player.closeHandledScreen();
                    timer = 15;
                    shulkerPickupTimeout = 100;
                    currentState = State.WAIT_SHULKER_PICKUP;
                }
                break;

            case FIND_ENDER_PLACE:
                placedEnderPos = findPlacementSpot();
                if (placedEnderPos == null) { timer = 60; break; }
                currentState = State.PLACE_ENDER;
                break;

            case PLACE_ENDER:
                if (placedEnderPos == null) { currentState = State.FIND_ENDER_PLACE; break; }
                meteordevelopment.meteorclient.utils.player.FindItemResult ender = meteordevelopment.meteorclient.utils.player.InvUtils.findInHotbar(s -> s.getItem() == Items.ENDER_CHEST);
                if (!ender.found()) { toggle(); return; }
                meteordevelopment.meteorclient.utils.player.InvUtils.swap(ender.slot(), false);
                rotateAndPlace(placedEnderPos);
                timer = 25;
                currentState = State.OPEN_ENDER;
                break;

            case OPEN_ENDER:
                if (placedEnderPos == null || mc.world.getBlockState(placedEnderPos).getBlock() != net.minecraft.block.Blocks.ENDER_CHEST) {
                    if (placedEnderPos != null) blacklistedSpots.add(placedEnderPos);
                    placedEnderPos = null;
                    currentState = State.FIND_ENDER_PLACE;
                    break;
                }
                interactBlock(placedEnderPos);
                timer = 25;
                currentState = State.STORE_SHULKER_ENDER_GUI;
                break;

            case STORE_SHULKER_ENDER_GUI:
                if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) break;
                net.minecraft.screen.ScreenHandler h = ((net.minecraft.client.gui.screen.ingame.HandledScreen<?>) mc.currentScreen).getScreenHandler();
                int cSz = h.slots.size() - 36;
                boolean stored = false;
                for (int i = cSz; i < h.slots.size(); i++) {
                    if (isShulkerItem(h.getSlot(i).getStack())) {
                        mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                        timer = actionDelay.get();
                        stored = true;
                        break;
                    }
                }
                if (!stored) {
                    mc.player.closeHandledScreen();
                    timer = 15;
                    currentState = State.RESTART_CYCLE;
                }
                break;

            case RESTART_CYCLE:
                blacklistedSpots.clear();
                currentState = State.DISTRIBUTE_ITEMS;
                break;
        }
    }

    private void doDistribute() {
        if (isDistributedProperly()) { currentState = State.CHECK_FULL; return; }
        switch (distSubState) {
            case 0:
                distTargetSlots.clear();
                distTargetIdx = 0;
                distSourceSlot = -1;
                int maxCount = 1;

                // Tìm các slot rỗng (chỉ cho phép chia vào main inventory 9-35)
                for (int i = 9; i <= 35; i++) {
                    ItemStack st = mc.player.getInventory().getStack(i);
                    if (st.isEmpty()) distTargetSlots.add(i);
                }

                // Tìm nguồn item lớn nhất (có thể ở hotbar 0-8 hoặc main inventory 9-35)
                for (int i = 0; i <= 35; i++) {
                    ItemStack st = mc.player.getInventory().getStack(i);
                    if (st.getItem() == getEffectiveCollectItem() && st.getCount() > maxCount) {
                        maxCount = st.getCount();
                        // Handler index: 0-8 (hotbar) map to 36-44, 9-35 (main inv) map to 9-35.
                        distSourceSlot = (i < 9) ? (i + 36) : i;
                    }
                }
                if (distTargetSlots.isEmpty() || distSourceSlot == -1) { 
                    distSubState = 0; 
                    currentState = State.CHECK_FULL; 
                    return; 
                }
                distSubState = 1;
                break;
            case 1:
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, distSourceSlot, 0, SlotActionType.PICKUP, mc.player);
                distSubState = 2;
                timer = actionDelay.get();
                break;
            case 2:
                if (distTargetIdx < distTargetSlots.size()) {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, distTargetSlots.get(distTargetIdx), 1, SlotActionType.PICKUP, mc.player);
                    distTargetIdx++;
                    timer = actionDelay.get();
                } else distSubState = 3;
                break;
            case 3:
                if (!mc.player.playerScreenHandler.getCursorStack().isEmpty())
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, distSourceSlot, 0, SlotActionType.PICKUP, mc.player);
                distSubState = 4;
                break;
            case 4:
                boolean anyEmpty = false;
                for (int i = 9; i <= 35; i++) {
                    if (mc.player.getInventory().getStack(i).isEmpty()) { anyEmpty = true; break; }
                }
                if (anyEmpty) {
                    distSubState = 0;
                    distSourceSlot = -1;
                    distTargetSlots.clear();
                    distTargetIdx = 0;
                    timer = actionDelay.get();
                } else {
                    distSubState = 0; // BẢO HIỂM CUỐI CÙNG: Luôn reset subState
                    currentState = State.CHECK_FULL;
                }
                break;
        }
    }

    // =========================================================
    //  GUI Helpers
    // =========================================================

    private boolean doSellTrashFromPlayerInv(net.minecraft.client.gui.screen.ingame.HandledScreen<?> screen) {
        net.minecraft.screen.ScreenHandler h = screen.getScreenHandler();
        int cSz = h.slots.size() - 36;
        for (int i = cSz; i < h.slots.size(); i++) {
            ItemStack st = h.getSlot(i).getStack();
            if (!st.isEmpty() && trashItems.get().contains(st.getItem())) {
                mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                timer = actionDelay.get();
                return true;
            }
        }
        return false;
    }

    private boolean doStoreItemsToContainer(net.minecraft.client.gui.screen.ingame.HandledScreen<?> screen) {
        net.minecraft.screen.ScreenHandler h = screen.getScreenHandler();
        int cSz = h.slots.size() - 36;
        
        // Kiểm tra xem Shulker còn chỗ trống không
        boolean hasSpace = false;
        for (int i = 0; i < cSz; i++) {
            ItemStack st = h.getSlot(i).getStack();
            if (st.isEmpty() || (st.getItem() == getEffectiveCollectItem() && st.getCount() < st.getMaxCount())) {
                hasSpace = true;
                break;
            }
        }
        if (!hasSpace) return false;

        int keep = Math.max(27, keepAmount.get());

        for (int i = cSz; i < h.slots.size(); i++) {
            ItemStack st = h.getSlot(i).getStack();
            if (st.getItem() == getEffectiveCollectItem()) {
                int totalInMainInv = countTargetItems();
                if (totalInMainInv - st.getCount() >= keep) {
                    mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    timer = actionDelay.get();
                    return true;
                }
            }
        }
        return false;
    }

    private void clickContainerSlot(int slot) {
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) return;
        net.minecraft.screen.ScreenHandler h = ((net.minecraft.client.gui.screen.ingame.HandledScreen<?>) mc.currentScreen).getScreenHandler();
        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    /**
     * Assert a GenericContainerScreen is open.
     * If not, goes back to `retryState` and returns false.
     */
    private boolean assertContainerOpen(State retryState) {
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) {
            currentState = retryState;
            timer = 10;
            return false;
        }
        return true;
    }

    // =========================================================
    //  Block Placement Helpers
    // =========================================================

    /** Place a block at position (click the block below it, UP face). */
    private void rotateAndPlace(BlockPos pos) {
        Vec3d target = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        double dX = target.x - mc.player.getX();
        double dY = target.y - mc.player.getEyeY();
        double dZ = target.z - mc.player.getZ();
        double diffXZ = Math.sqrt(dX * dX + dZ * dZ);
        float yaw = (float) Math.toDegrees(Math.atan2(dZ, dX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dY, diffXZ));
        
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        placeBlock(pos);
    }

    private void placeBlock(BlockPos pos) {
        BlockPos below  = pos.down();
        Vec3d    hitVec = Vec3d.ofCenter(pos).subtract(0, 0.5, 0);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
            new BlockHitResult(hitVec, Direction.UP, below, false));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Interact (open) a block at position. */
    private void interactBlock(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
            new BlockHitResult(center, Direction.UP, pos, false));
    }

    /**
     * Find a nearby position suitable for placing a block:
     *   - block below must be solid
     *   - position itself must be air
     *   - block above must be air (so shulker lid can open)
     *   - not the player's current or head position
     */
    private BlockPos findPlacementSpot() {
        BlockPos origin = mc.player.getBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (pos.equals(origin) || pos.equals(origin.up())) continue;

                    BlockState at    = mc.world.getBlockState(pos);
                    BlockState above = mc.world.getBlockState(pos.up());

                    if (!mc.world.getBlockState(pos.down()).isAir()
                        && at.isAir()
                        && above.isAir()
                        && !blacklistedSpots.contains(pos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    // =========================================================
    //  Inventory Query Helpers
    // =========================================================

    private int countTargetItems() {
        Item item = getEffectiveCollectItem();
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == item) count += s.getCount();
        }
        return count;
    }

    private int countTargetItemsInMainInv() {
        Item item = getEffectiveCollectItem();
        int count = 0;
        for (int i = 9; i <= 35; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == item) count += s.getCount();
        }
        return count;
    }

    private boolean isDistributedProperly() {
        Item target = getEffectiveCollectItem();
        for (int i = 9; i <= 35; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (st.isEmpty() || st.getItem() != target) return false;
        }
        return true;
    }

    /** Main inventory (slots 9-35) are all occupied with max-stack items. */
    private boolean isMainInventoryFull() {
        for (int i = 9; i <= 35; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || s.getCount() < s.getMaxCount()) return false;
        }
        return true;
    }

    /** Any trash item exists in main inventory (not hotbar). */
    private boolean hasTrashInMainInv() {
        for (int i = 9; i <= 35; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && trashItems.get().contains(s.getItem())) return true;
        }
        return false;
    }

    /**
     * Hotbar slots index 7 and 8 (user's slot 8 and 9) have unwanted items.
     * "Unwanted" = not empty, not Ender Chest, not Shulker Box.
     */
    private boolean hotbar78HasUnwantedItems() {
        for (int idx : new int[]{7, 8}) {
            ItemStack s = mc.player.getInventory().getStack(idx);
            if (!s.isEmpty() && s.getItem() != Items.ENDER_CHEST && !isShulkerItem(s)) {
                return true;
            }
        }
        return false;
    }

    private int getExpInHotbar78() {
        for (int idx : new int[]{7, 8}) {
            ItemStack s = mc.player.getInventory().getStack(idx);
            if (!s.isEmpty() && s.getItem() == Items.EXPERIENCE_BOTTLE) {
                return idx;
            }
        }
        return -1;
    }

    private boolean hasShulkerInInventory() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (isShulkerItem(mc.player.getInventory().getStack(i))) return true;
        }
        return false;
    }

    private boolean hasEnderChestInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_CHEST) return true;
        }
        return false;
    }

    private int countXpBottles() {
        int count = 0;
        for (int i : new int[]{xpSlot1.get(), xpSlot2.get()}) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.EXPERIENCE_BOTTLE) count += s.getCount();
        }
        return count;
    }

    private boolean expSlotsHaveTrash() {
        for (int i : new int[]{xpSlot1.get(), xpSlot2.get()}) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() != Items.EXPERIENCE_BOTTLE) return true;
        }
        return false;
    }

    private boolean isSlotUnwanted(int hotbarIndex) {
        ItemStack s = mc.player.getInventory().getStack(hotbarIndex);
        if (s.isEmpty()) return false;
        if (s.getItem() == Items.ENDER_CHEST) return false;
        if (isShulkerItem(s)) return false;
        if (s.getItem() == Items.EXPERIENCE_BOTTLE) return false;
        return true;
    }


    // =========================================================
    //  Auto-detect Drop Item (Silk Touch vs Fortune)
    // =========================================================

    /**
     * Trả về item cần đếm/cất:
     *   - auto-detect OFF → dùng collectItem setting
     *   - auto-detect ON + có Silk Touch trong hotbar → đếm khối ore (mineBlock)
     *   - auto-detect ON + không có Silk Touch → đếm raw drop (DIAMOND, RAW_IRON...)
     */
    private Item getEffectiveCollectItem() {
        if (!autoDetectDrop.get()) return collectItem.get();
        return hasSilkTouchInHotbar() ? mineBlock.get() : getOreDrop(mineBlock.get());
    }

    /**
     * Kiểm tra hotbar (slot 0-8) xem có công cụ Silk Touch không.
     */
    private boolean hasSilkTouchInHotbar() {
        if (mc.player == null || mc.world == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            
            Set<RegistryEntry<Enchantment>> enchants = EnchantmentHelper.getEnchantments(s).getEnchantments();
            for (RegistryEntry<Enchantment> entry : enchants) {
                if (entry.getIdAsString().toLowerCase().contains("silk_touch")) return true;
            }
        }
        return false;
    }

    /**
     * Map: khối ore → item rơi ra khi đào bằng Fortune/tay không.
     * Nếu không tìm thấy (custom ore server) → trả về chính khối đó.
     */
    private static Item getOreDrop(Item oreBlock) {
        if (oreBlock == Items.DIAMOND_ORE      || oreBlock == Items.DEEPSLATE_DIAMOND_ORE)  return Items.DIAMOND;
        if (oreBlock == Items.IRON_ORE         || oreBlock == Items.DEEPSLATE_IRON_ORE)     return Items.RAW_IRON;
        if (oreBlock == Items.GOLD_ORE         || oreBlock == Items.DEEPSLATE_GOLD_ORE
                                               || oreBlock == Items.NETHER_GOLD_ORE)        return Items.RAW_GOLD;
        if (oreBlock == Items.COPPER_ORE       || oreBlock == Items.DEEPSLATE_COPPER_ORE)   return Items.RAW_COPPER;
        if (oreBlock == Items.COAL_ORE         || oreBlock == Items.DEEPSLATE_COAL_ORE)     return Items.COAL;
        if (oreBlock == Items.EMERALD_ORE      || oreBlock == Items.DEEPSLATE_EMERALD_ORE)  return Items.EMERALD;
        if (oreBlock == Items.LAPIS_ORE        || oreBlock == Items.DEEPSLATE_LAPIS_ORE)    return Items.LAPIS_LAZULI;
        if (oreBlock == Items.REDSTONE_ORE     || oreBlock == Items.DEEPSLATE_REDSTONE_ORE) return Items.REDSTONE;
        if (oreBlock == Items.NETHER_QUARTZ_ORE)                                            return Items.QUARTZ;
        if (oreBlock == Items.ANCIENT_DEBRIS)                                               return Items.NETHERITE_SCRAP;
        return oreBlock; // fallback: custom ore hoặc silk touch block
    }

    private boolean isShulkerItem(ItemStack s) {
        if (s.isEmpty()) return false;
        return s.getItem() instanceof BlockItem
            && ((BlockItem) s.getItem()).getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean isShulkerBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock;
    }
}
