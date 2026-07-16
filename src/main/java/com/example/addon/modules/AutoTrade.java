package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;

import java.util.ArrayList;
import java.util.List;

public class AutoTrade extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgSetup = settings.createGroup("Setup");
    
    public final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("rotation-mode")
        .description("How to rotate when looking at villagers/chests.")
        .defaultValue(RotationMode.Legit)
        .build()
    );

    public final Setting<Integer> waitTime = sgGeneral.add(new IntSetting.Builder()
        .name("wait-time-seconds")
        .description("Seconds to wait before restarting the loop.")
        .defaultValue(30)
        .min(0)
        .sliderMax(120)
        .build()
    );

    public final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Delay in ticks for inventory clicks (higher = more legit).")
        .defaultValue(6)
        .min(1)
        .sliderMax(20)
        .build()
    );

    public final Setting<Item> tradeInput1 = sgInventory.add(new ItemSetting.Builder()
        .name("trade-input-1")
        .description("The primary item you need to trade (e.g. Emerald).")
        .defaultValue(Items.EMERALD)
        .build()
    );

    public final Setting<Integer> minInput1 = sgInventory.add(new IntSetting.Builder()
        .name("min-input-1")
        .description("Minimum Input 1 items before going to restock.")
        .defaultValue(32)
        .min(1)
        .build()
    );

    public final Setting<Integer> restockStacks1 = sgInventory.add(new IntSetting.Builder()
        .name("restock-stacks-1")
        .description("How many stacks of Input 1 to grab from restock chests.")
        .defaultValue(4)
        .min(1)
        .build()
    );

    public final Setting<Item> tradeInput2 = sgInventory.add(new ItemSetting.Builder()
        .name("trade-input-2")
        .description("The secondary item you need to trade (e.g. Book). Leave as Air if not needed.")
        .defaultValue(Items.AIR)
        .build()
    );

    public final Setting<Integer> minInput2 = sgInventory.add(new IntSetting.Builder()
        .name("min-input-2")
        .description("Minimum Input 2 items before going to restock.")
        .defaultValue(32)
        .min(0)
        .build()
    );

    public final Setting<Integer> restockStacks2 = sgInventory.add(new IntSetting.Builder()
        .name("restock-stacks-2")
        .description("How many stacks of Input 2 to grab from restock chests.")
        .defaultValue(1)
        .min(0)
        .build()
    );

    public final Setting<List<Item>> tradeOutputs = sgInventory.add(new ItemListSetting.Builder()
        .name("trade-outputs")
        .description("The items you expect to receive (e.g. Enchanted Book).")
        .defaultValue(Items.ENCHANTED_BOOK)
        .build()
    );

    public final Setting<String> enchantmentFilter = sgInventory.add(new StringSetting.Builder()
        .name("enchantment-filter")
        .description("Only accept Enchanted Books with this enchantment (e.g. 'mending'). Leave blank for any.")
        .defaultValue("")
        .build()
    );

    public final Setting<Integer> maxOutputStacks = sgInventory.add(new IntSetting.Builder()
        .name("max-output-stacks")
        .description("How many output stacks to hold before depositing.")
        .defaultValue(20)
        .min(1)
        .build()
    );

    public final Setting<Boolean> setupMode = sgSetup.add(new BoolSetting.Builder()
        .name("setup-mode")
        .description("Enable to add villagers and chests.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Keybind> addVillagerKey = sgSetup.add(new KeybindSetting.Builder()
        .name("add-villager")
        .description("Key to add the villager you are looking at.")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> addDepositChestKey = sgSetup.add(new KeybindSetting.Builder()
        .name("add-deposit-chest")
        .description("Key to add the deposit chest.")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> addRestockChestKey = sgSetup.add(new KeybindSetting.Builder()
        .name("add-restock-chest")
        .description("Key to add a restock chest.")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> clearAllKey = sgSetup.add(new KeybindSetting.Builder()
        .name("clear-all")
        .description("Xóa toàn bộ dữ liệu cài đặt tọa độ.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final List<TradePoint> villagerPoints = new ArrayList<>();
    private final List<TradePoint> chestPoints = new ArrayList<>();
    private TradePoint depositChest = null;
    
    private State currentState = State.IDLE;
    private int currentVillagerIndex = 0;
    private int currentRestockChestIndex = 0;
    private int waitTicks = 0;
    private int lastRecipeIndex = -1;
    
    private boolean addVillagerWasPressed = false;
    private boolean clearAllWasPressed = false;
    private boolean addDepositWasPressed = false;
    private boolean addRestockWasPressed = false;

    private int getDelay() {
        return actionDelay.get() + (int)(Math.random() * 3);
    }

    private boolean isAiming = false;
    private Vec3d aimTarget = null;
    private Runnable aimCallback = null;
    private int aimTicks = 0;
    private int pressTicks = 0;

    public AutoTrade() {
        super(AddonTemplate.CATEGORY, "auto-trade", "Automatically walks and trades with villagers.");
    }

    @Override
    public void onActivate() {
        currentState = State.IDLE;
        currentVillagerIndex = 0;
        currentRestockChestIndex = 0;
        waitTicks = 0;
        isAiming = false;
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        
        NbtList vPoints = new NbtList();
        for (TradePoint p : villagerPoints) {
            NbtCompound pTag = new NbtCompound();
            pTag.putLong("standPos", p.standPos.asLong());
            pTag.putLong("targetPos", p.targetPos.asLong());
            vPoints.add(pTag);
        }
        tag.put("villagerPoints", vPoints);
        
        NbtList cPoints = new NbtList();
        for (TradePoint p : chestPoints) {
            NbtCompound pTag = new NbtCompound();
            pTag.putLong("standPos", p.standPos.asLong());
            pTag.putLong("targetPos", p.targetPos.asLong());
            cPoints.add(pTag);
        }
        tag.put("chestPoints", cPoints);
        
        if (depositChest != null) {
            NbtCompound dTag = new NbtCompound();
            dTag.putLong("standPos", depositChest.standPos.asLong());
            dTag.putLong("targetPos", depositChest.targetPos.asLong());
            tag.put("depositChest", dTag);
        }
        
        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        super.fromTag(tag);
        
        villagerPoints.clear();
        if (tag.contains("villagerPoints")) {
            NbtList vPoints = (NbtList) tag.get("villagerPoints");
            for (int i = 0; i < vPoints.size(); i++) {
                NbtCompound pTag = (NbtCompound) vPoints.get(i);
                villagerPoints.add(new TradePoint(BlockPos.fromLong(pTag.getLong("standPos").orElse(0L)), BlockPos.fromLong(pTag.getLong("targetPos").orElse(0L))));
            }
        }
        
        chestPoints.clear();
        if (tag.contains("chestPoints")) {
            NbtList cPoints = (NbtList) tag.get("chestPoints");
            for (int i = 0; i < cPoints.size(); i++) {
                NbtCompound pTag = (NbtCompound) cPoints.get(i);
                chestPoints.add(new TradePoint(BlockPos.fromLong(pTag.getLong("standPos").orElse(0L)), BlockPos.fromLong(pTag.getLong("targetPos").orElse(0L))));
            }
        }
        
        if (tag.contains("depositChest")) {
            NbtCompound dTag = (NbtCompound) tag.get("depositChest");
            depositChest = new TradePoint(BlockPos.fromLong(dTag.getLong("standPos").orElse(0L)), BlockPos.fromLong(dTag.getLong("targetPos").orElse(0L)));
        } else {
            depositChest = null;
        }
        
        return this;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (setupMode.get()) {
            handleSetup();
            return;
        }

        if (pressTicks > 0) {
            mc.options.useKey.setPressed(true);
            pressTicks--;
            if (pressTicks == 0) {
                mc.options.useKey.setPressed(false);
            }
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (isAiming && aimTarget != null) {
            aimTicks++;
            if (aimTicks > 40) {
                isAiming = false;
                if (aimCallback != null) {
                    aimCallback.run();
                    aimCallback = null;
                }
                return;
            }

            double dX = aimTarget.x - mc.player.getX();
            double dY = aimTarget.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
            double dZ = aimTarget.z - mc.player.getZ();
            
            double targetYaw = Math.toDegrees(Math.atan2(dZ, dX)) - 90;
            double targetPitch = -Math.toDegrees(Math.atan2(dY, Math.sqrt(dX * dX + dZ * dZ)));
            
            float diffYaw = MathHelper.wrapDegrees((float) (targetYaw - mc.player.getYaw()));
            float diffPitch = MathHelper.wrapDegrees((float) (targetPitch - mc.player.getPitch()));
            
            float stepYaw = diffYaw * 0.4f;
            float stepPitch = diffPitch * 0.4f;
            
            if (Math.abs(stepYaw) < 1.0f) stepYaw = Math.signum(stepYaw) * 1.0f;
            if (Math.abs(stepPitch) < 1.0f) stepPitch = Math.signum(stepPitch) * 1.0f;
            
            if (Math.abs(diffYaw) > 0.5f) mc.player.setYaw(mc.player.getYaw() + stepYaw);
            if (Math.abs(diffPitch) > 0.5f) mc.player.setPitch(mc.player.getPitch() + stepPitch);
            
            if (Math.abs(diffYaw) <= 0.5f && Math.abs(diffPitch) <= 0.5f) {
                isAiming = false;
                if (aimCallback != null) {
                    aimCallback.run();
                    aimCallback = null;
                }
            }
            return;
        }

        switch (currentState) {
            case IDLE:
                if (!villagerPoints.isEmpty()) {
                    if (currentVillagerIndex == 0) {
                        long timeOfDay = mc.world.getTimeOfDay() % 24000;
                        if (timeOfDay > 13000 && timeOfDay < 23000) {
                            info("Trời tối, đang đứng chờ tới sáng...");
                            waitTicks = 200;
                            return;
                        }
                    }

                    if (needsDeposit()) {
                        info("Kho đồ đầy hoặc đạt giới hạn! Đi cất đồ.");
                        currentState = State.MOVING_TO_DEPOSIT;
                        if (depositChest != null) {
                            moveToPoint(depositChest);
                        } else {
                            error("Chưa cài đặt rương cất đồ (Deposit Chest)!");
                            toggle();
                        }
                        return;
                    }

                    if (needsRestock()) {
                        info("Hết nguyên liệu! Đi lấy đồ.");
                        currentState = State.MOVING_TO_RESTOCK;
                        currentRestockChestIndex = 0;
                        if (!chestPoints.isEmpty()) {
                            moveToPoint(chestPoints.get(currentRestockChestIndex));
                        } else {
                            error("Chưa cài đặt rương lấy đồ (Restock Chest)!");
                            toggle();
                        }
                        return;
                    }
                    
                    currentState = State.MOVING_TO_VILLAGER;
                    moveToPoint(villagerPoints.get(currentVillagerIndex));
                }
                break;
                
            case MOVING_TO_VILLAGER:
                if (isAtPoint(villagerPoints.get(currentVillagerIndex).standPos)) {
                    currentState = State.LOOK_AT_VILLAGER;
                }
                break;
                
            case LOOK_AT_VILLAGER:
                lookAndInteractVillager(villagerPoints.get(currentVillagerIndex));
                break;
                
            case TRADING:
                doTradeLogic();
                break;
                
            case MOVING_TO_DEPOSIT:
                if (depositChest != null && isAtPoint(depositChest.standPos)) {
                    lookAndInteractChest(depositChest);
                    currentState = State.DEPOSITING;
                    waitTicks = 30;
                }
                break;
                
            case MOVING_TO_RESTOCK:
                if (!chestPoints.isEmpty() && isAtPoint(chestPoints.get(currentRestockChestIndex).standPos)) {
                    lookAndInteractChest(chestPoints.get(currentRestockChestIndex));
                    currentState = State.RESTOCKING;
                    waitTicks = 30;
                }
                break;
                
            case DEPOSITING:
                doDepositLogic();
                break;
                
            case RESTOCKING:
                doRestockLogic();
                break;
        }
    }

    private boolean isAtPoint(BlockPos pos) {
        return mc.player.getBlockPos().isWithinDistance(pos, 1.5);
    }

    private void doTradeLogic() {
        if (!(mc.currentScreen instanceof MerchantScreen)) {
            if (waitTicks <= 0) {
                error("GUI dân làng không mở!");
                nextVillager();
            }
            return;
        }

        if (needsRestock()) {
            info("Không đủ đồ trade! Tạm ngắt để đi lấy thêm...");
            mc.player.closeHandledScreen();
            lastRecipeIndex = -1;
            currentState = State.MOVING_TO_RESTOCK;
            currentRestockChestIndex = 0;
            if (!chestPoints.isEmpty()) {
                moveToPoint(chestPoints.get(currentRestockChestIndex));
            } else {
                error("Chưa cài đặt rương lấy đồ!");
                toggle();
            }
            return;
        }
        
        MerchantScreen screen = (MerchantScreen) mc.currentScreen;
        MerchantScreenHandler handler = screen.getScreenHandler();
        
        int recipeIndex = -1;
        for (int i = 0; i < handler.getRecipes().size(); i++) {
            TradeOffer offer = handler.getRecipes().get(i);
            if (offer.isDisabled()) continue;
            
            Item sellItem = offer.getSellItem().getItem();
            Item buyItem1 = offer.getOriginalFirstBuyItem().getItem();
            
            if (!tradeOutputs.get().contains(sellItem)) continue;
            
            boolean validInputs = false;
            if (offer.getSecondBuyItem().isPresent()) {
                if (tradeInput1.get() != Items.AIR && tradeInput2.get() != Items.AIR) {
                    if (buyItem1 == tradeInput1.get() || buyItem1 == tradeInput2.get()) validInputs = true;
                }
            } else {
                if (buyItem1 == tradeInput1.get() || buyItem1 == tradeInput2.get()) validInputs = true;
            }
            
            if (!validInputs) continue;

            if (sellItem == Items.ENCHANTED_BOOK && !enchantmentFilter.get().trim().isEmpty()) {
                String filter = enchantmentFilter.get().trim().toLowerCase();
                ComponentMap components = offer.getSellItem().getComponents();
                boolean hasEnchant = false;
                for (ComponentType<?> type : components.getTypes()) {
                    if (type.toString().toLowerCase().contains("enchantment")) {
                        Object comp = components.get(type);
                        if (comp != null && comp.toString().toLowerCase().contains(filter)) {
                            hasEnchant = true;
                            break;
                        }
                    }
                }
                if (!hasEnchant) continue;
            }
            
            recipeIndex = i;
            break;
        }
        
        if (recipeIndex == -1) {
            info("Dân làng đã hết hàng (bị khóa) hoặc không bán Sách/Món đồ phù hợp. Bỏ qua.");
            mc.player.closeHandledScreen();
            lastRecipeIndex = -1;
            nextVillager();
            return;
        }

        if (lastRecipeIndex != recipeIndex) {
            mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(recipeIndex));
            lastRecipeIndex = recipeIndex;
            waitTicks = getDelay();
            return;
        }

        if (handler.getSlot(2).hasStack()) {
            mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.QUICK_MOVE, mc.player);
            waitTicks = getDelay(); 
            lastRecipeIndex = -1;
            
            if (needsDeposit()) {
                info("Kho đồ đã đầy! Tạm ngắt đi cất đồ...");
                mc.player.closeHandledScreen();
                lastRecipeIndex = -1;
                currentState = State.MOVING_TO_DEPOSIT;
                if (depositChest != null) {
                    moveToPoint(depositChest);
                }
            }
            return; 
        }

        TradeOffer offer = handler.getRecipes().get(recipeIndex);
        Item buyItem1 = offer.getOriginalFirstBuyItem().getItem();

        boolean moved = moveItemToSlot(handler, buyItem1, 0);
        if (!moved && offer.getSecondBuyItem().isPresent()) {
            Item buyItem2 = (buyItem1 == tradeInput1.get()) ? tradeInput2.get() : tradeInput1.get();
            moved = moveItemToSlot(handler, buyItem2, 1);
        }
        
        if (moved) {
            waitTicks = getDelay();
        }
    }

    private boolean moveItemToSlot(MerchantScreenHandler handler, Item item, int targetSlot) {
        if (handler.getSlot(targetSlot).hasStack() && handler.getSlot(targetSlot).getStack().getCount() >= handler.getSlot(targetSlot).getStack().getMaxCount()) return false;
        
        for (int i = 3; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).hasStack() && handler.getSlot(i).getStack().getItem() == item) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true; 
            }
        }
        return false;
    }

    private void doDepositLogic() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) return;
        GenericContainerScreen screen = (GenericContainerScreen) mc.currentScreen;
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        
        boolean depositedAnything = false;
        int containerSize = handler.getInventory().size(); 
        
        for (int i = containerSize; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).hasStack()) {
                Item item = handler.getSlot(i).getStack().getItem();
                if (tradeOutputs.get().contains(item)) {
                    mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    depositedAnything = true;
                    waitTicks = getDelay();
                    return; 
                }
            }
        }
        
        if (!depositedAnything) {
            info("Đã cất đồ xong! Quay lại dân làng cũ.");
            mc.player.closeHandledScreen();
            lastRecipeIndex = -1;
            currentState = State.IDLE; 
        }
    }

    private void doRestockLogic() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) return;
        GenericContainerScreen screen = (GenericContainerScreen) mc.currentScreen;
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        
        int containerSize = handler.getInventory().size();
        boolean grabbedAnything = false;
        
        if (tradeInput1.get() != Items.AIR && countItemsInInventory(tradeInput1.get()) < restockStacks1.get() * tradeInput1.get().getMaxCount()) {
            for (int i = 0; i < containerSize; i++) {
                if (handler.getSlot(i).hasStack() && handler.getSlot(i).getStack().getItem() == tradeInput1.get() && handler.getSlot(i).getStack().getCount() == tradeInput1.get().getMaxCount()) {
                    mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    grabbedAnything = true;
                    waitTicks = getDelay();
                    return; 
                }
            }
        }

        if (tradeInput2.get() != Items.AIR && countItemsInInventory(tradeInput2.get()) < restockStacks2.get() * tradeInput2.get().getMaxCount()) {
            for (int i = 0; i < containerSize; i++) {
                if (handler.getSlot(i).hasStack() && handler.getSlot(i).getStack().getItem() == tradeInput2.get() && handler.getSlot(i).getStack().getCount() == tradeInput2.get().getMaxCount()) {
                    mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    grabbedAnything = true;
                    waitTicks = getDelay();
                    return; 
                }
            }
        }
        
        if (!grabbedAnything) {
            if (needsRestock()) {
                mc.player.closeHandledScreen();
                lastRecipeIndex = -1;
                currentRestockChestIndex++;
                if (currentRestockChestIndex < chestPoints.size()) {
                    info("Rương này đã hết nguyên liệu cần thiết! Di chuyển sang rương tiếp theo...");
                    currentState = State.MOVING_TO_RESTOCK;
                    moveToPoint(chestPoints.get(currentRestockChestIndex));
                } else {
                    error("Đã lục soát toàn bộ rương nhưng vẫn thiếu nguyên liệu! Tắt AutoTrade.");
                    toggle();
                }
                return;
            }
            info("Đã lấy đủ đồ! Quay lại dân làng cũ.");
            mc.player.closeHandledScreen();
            lastRecipeIndex = -1;
            currentState = State.IDLE;
        }
    }

    private int countItemsInInventory(Item item) {
        int items = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                items += mc.player.getInventory().getStack(i).getCount();
            }
        }
        return items;
    }

    private boolean needsDeposit() {
        if (mc.player.getInventory().getEmptySlot() == -1) return true;
        
        int outputCount = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (tradeOutputs.get().contains(mc.player.getInventory().getStack(i).getItem())) {
                outputCount += mc.player.getInventory().getStack(i).getCount();
            }
        }
        return outputCount >= (maxOutputStacks.get() * 64);
    }

    private boolean needsRestock() {
        if (tradeInput1.get() != Items.AIR) {
            int count = countItemsInInventory(tradeInput1.get());
            if (count < minInput1.get()) return true;
        }
        if (tradeInput2.get() != Items.AIR) {
            int count = countItemsInInventory(tradeInput2.get());
            if (count < minInput2.get()) return true;
        }
        return false;
    }

    private void handleSetup() {
        boolean addVillagerIsPressed = addVillagerKey.get().isPressed();
        if (addVillagerIsPressed && !addVillagerWasPressed) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
                if (entity.getType().toString().toLowerCase().contains("villager")) {
                    BlockPos standPos = mc.player.getBlockPos();
                    villagerPoints.add(new TradePoint(standPos, entity.getBlockPos()));
                    info("Added villager point #" + villagerPoints.size());
                }
            }
        }
        addVillagerWasPressed = addVillagerIsPressed;
        
        boolean depositIsPressed = addDepositChestKey.get().isPressed();
        if (depositIsPressed && !addDepositWasPressed) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos targetPos = ((net.minecraft.util.hit.BlockHitResult) mc.crosshairTarget).getBlockPos();
                depositChest = new TradePoint(mc.player.getBlockPos(), targetPos);
                info("Saved Deposit Chest!");
            }
        }
        addDepositWasPressed = depositIsPressed;
        
        boolean restockIsPressed = addRestockChestKey.get().isPressed();
        if (restockIsPressed && !addRestockWasPressed) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos targetPos = ((net.minecraft.util.hit.BlockHitResult) mc.crosshairTarget).getBlockPos();
                chestPoints.add(new TradePoint(mc.player.getBlockPos(), targetPos));
                info("Saved Restock Chest #" + chestPoints.size());
            }
        }
        addRestockWasPressed = restockIsPressed;
        
        boolean clearAllIsPressed = clearAllKey.get().isPressed();
        if (clearAllIsPressed && !clearAllWasPressed) {
            villagerPoints.clear();
            chestPoints.clear();
            depositChest = null;
            info("Đã xóa sạch toàn bộ tọa độ Dân làng và Rương!");
        }
        clearAllWasPressed = clearAllIsPressed;
    }

    private void moveToPoint(TradePoint point) {
        info("Going to " + point.standPos.toShortString());
        ChatUtils.sendPlayerMsg("#goto " + point.standPos.getX() + " " + point.standPos.getY() + " " + point.standPos.getZ());
        waitTicks = 60; 
    }

    private void startSmoothAim(Vec3d target, Runnable callback) {
        if (rotationMode.get() == RotationMode.Packet) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), 50, true, callback);
        } else {
            isAiming = true;
            aimTarget = target;
            aimCallback = callback;
            aimTicks = 0;
        }
    }

    private void lookAndInteractVillager(TradePoint point) {
        Box searchBox = new Box(point.targetPos).expand(1.0);
        List<Entity> entities = mc.world.getOtherEntities(null, searchBox);
        Entity targetVillager = null;
        for (Entity e : entities) {
            if (e.getType().toString().toLowerCase().contains("villager") && e.isAlive()) {
                targetVillager = e;
                break;
            }
        }
        
        if (targetVillager == null) {
            error("Không tìm thấy dân làng!");
            nextVillager();
            return;
        }

        Vec3d center = new Vec3d(targetVillager.getX(), targetVillager.getY() + targetVillager.getHeight() / 2, targetVillager.getZ());
        
        Entity finalTarget = targetVillager;
        startSmoothAim(center, () -> {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                Entity hit = ((EntityHitResult) mc.crosshairTarget).getEntity();
                if (hit == finalTarget || hit.getType().toString().toLowerCase().contains("villager")) {
                    pressTicks = 2;
                    currentState = State.TRADING;
                    waitTicks = 30; 
                    return;
                }
            }
            
            error("Lỗi: Không thể click dân làng! Có thể đứng ngoài tầm với (hơn 3 block) hoặc bị che khuất.");
            nextVillager();
        });
    }

    private void lookAndInteractChest(TradePoint point) {
        Vec3d hitVec = new Vec3d(point.targetPos.getX() + 0.5, point.targetPos.getY() + 0.5, point.targetPos.getZ() + 0.5);
        startSmoothAim(hitVec, () -> {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockHitResult bhr = (BlockHitResult) mc.crosshairTarget;
                if (bhr.getBlockPos().equals(point.targetPos)) {
                    pressTicks = 2;
                    return;
                }
            }
            pressTicks = 2;
        });
    }

    private void nextVillager() {
        currentVillagerIndex++;
        if (currentVillagerIndex >= villagerPoints.size()) {
            currentVillagerIndex = 0;
            info("Hoàn thành vòng lặp. Chờ " + waitTime.get() + " giây...");
            waitTicks = waitTime.get() * 20;
            currentState = State.IDLE;
        } else {
            currentState = State.MOVING_TO_VILLAGER;
            moveToPoint(villagerPoints.get(currentVillagerIndex));
        }
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        for (int i = 0; i < villagerPoints.size(); i++) {
            TradePoint p = villagerPoints.get(i);
            
            Box searchBox = new Box(p.targetPos).expand(1.0);
            List<Entity> entities = mc.world.getOtherEntities(null, searchBox);
            Entity found = null;
            for (Entity e : entities) {
                if (e.getType().toString().toLowerCase().contains("villager")) {
                    found = e;
                    break;
                }
            }
            
            if (found != null) {
                event.renderer.box(found.getBoundingBox(), Color.CYAN, Color.CYAN, ShapeMode.Lines, 0);
            } else {
                event.renderer.box(new Box(p.targetPos), Color.CYAN, Color.CYAN, ShapeMode.Lines, 0);
            }
        }
        
        if (depositChest != null) {
            event.renderer.box(new Box(depositChest.targetPos), Color.RED, Color.RED, ShapeMode.Lines, 0);
        }
        
        for (TradePoint cp : chestPoints) {
            event.renderer.box(new Box(cp.targetPos), Color.ORANGE, Color.ORANGE, ShapeMode.Lines, 0);
        }
    }

    public enum RotationMode {
        Legit, Packet
    }

    private enum State {
        IDLE, MOVING_TO_VILLAGER, LOOK_AT_VILLAGER, TRADING, MOVING_TO_DEPOSIT, DEPOSITING, MOVING_TO_RESTOCK, RESTOCKING
    }

    private static class TradePoint {
        public final BlockPos standPos;
        public final BlockPos targetPos;

        public TradePoint(BlockPos standPos, BlockPos targetPos) {
            this.standPos = standPos;
            this.targetPos = targetPos;
        }
    }
}
