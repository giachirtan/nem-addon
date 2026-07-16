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
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
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

public class VillagerRoller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgSetup = settings.createGroup("Setup");

    public final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("rotation-mode")
        .description("How to rotate when looking at villagers/blocks.")
        .defaultValue(RotationMode.Legit)
        .build()
    );

    public final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Delay in ticks for inventory clicks (higher = safer).")
        .defaultValue(6)
        .min(1)
        .sliderMax(20)
        .build()
    );

    public final Setting<String> targetEnchant = sgGeneral.add(new StringSetting.Builder()
        .name("target-enchant")
        .description("Nhập tên bùa (ví dụ: mending, sharpness). Có thể nhập tùy ý.")
        .defaultValue("mending")
        .build()
    );

    public final Setting<Integer> targetEnchantLevel = sgGeneral.add(new IntSetting.Builder()
        .name("target-enchant-level")
        .description("Cấp độ bùa tối thiểu (vd: Mending = 1, Sharpness = 5).")
        .defaultValue(1)
        .min(1)
        .sliderMax(5)
        .build()
    );

    public final Setting<Item> tradeInput1 = sgInventory.add(new ItemSetting.Builder()
        .name("trade-input-1")
        .description("Primary item to trade (Emerald).")
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
        .description("Secondary item to trade (Book).")
        .defaultValue(Items.BOOK)
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

    public final Setting<Boolean> setupMode = sgSetup.add(new BoolSetting.Builder()
        .name("setup-mode")
        .description("Enable to add setup points.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Keybind> addVillagerKey = sgSetup.add(new KeybindSetting.Builder()
        .name("add-villager")
        .description("Key to add the villager you are looking at (I).")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> addLecternKey = sgSetup.add(new KeybindSetting.Builder()
        .name("add-lectern")
        .description("Key to add the lectern pos for the LAST added villager (N).")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> addRestockChestKey = sgSetup.add(new KeybindSetting.Builder()
        .name("add-restock-chest")
        .description("Key to add a restock chest (M).")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> addDumpChestKey = sgSetup.add(new KeybindSetting.Builder()
        .name("add-dump-chest")
        .description("Key to add a dump chest to deposit enchanted books (K).")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> clearAllKey = sgSetup.add(new KeybindSetting.Builder()
        .name("clear-all")
        .description("Xóa toàn bộ dữ liệu cài đặt tọa độ.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final List<RollPoint> rollPoints = new ArrayList<>();
    private final List<ChestPoint> chestPoints = new ArrayList<>();
    private final List<ChestPoint> dumpPoints = new ArrayList<>();
    
    private State currentState = State.IDLE;
    private int currentPointIndex = 0;
    private int currentRestockChestIndex = 0;
    private int currentDumpChestIndex = 0;
    private int lecternCount = 0;
    private int waitTicks = 0;
    private int lastRecipeIndex = -1;
    private int jobTimeout = 0;
    private int guiTimeout = 0;
    
    private boolean addVillagerWasPressed = false;
    private boolean addLecternWasPressed = false;
    private boolean clearAllWasPressed = false;
    private boolean addRestockWasPressed = false;
    private boolean addDumpChestWasPressed = false;

    private boolean isAiming = false;
    private Vec3d aimTarget = null;
    private Runnable aimCallback = null;
    private Entity targetVillagerEntity = null;
    private boolean isBreaking = false;

    public VillagerRoller() {
        super(AddonTemplate.CATEGORY, "villager-roller", "Automatically rolls for specific enchanted books.");
    }

    private int getDelay() {
        return actionDelay.get() + (int)(Math.random() * 3);
    }

    @Override
    public void onActivate() {
        currentState = State.IDLE;
        currentPointIndex = 0;
        currentRestockChestIndex = 0;
        currentDumpChestIndex = 0;
        lecternCount = 0;
        waitTicks = 0;
        lastRecipeIndex = -1;
        jobTimeout = 0;
        guiTimeout = 0;
        isAiming = false;
        isBreaking = false;
        targetVillagerEntity = null;
        if (mc.options != null && mc.options.attackKey != null) {
            mc.options.attackKey.setPressed(false);
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null && mc.options.attackKey != null) {
            mc.options.attackKey.setPressed(false);
        }
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        
        NbtList rolls = new NbtList();
        for (RollPoint rp : rollPoints) {
            NbtCompound rpTag = new NbtCompound();
            rpTag.putLong("standPos", rp.standPos.asLong());
            rpTag.putLong("villagerPos", rp.villagerPos.asLong());
            if (rp.lecternPos != null) rpTag.putLong("lecternPos", rp.lecternPos.asLong());
            rpTag.putBoolean("completed", rp.completed);
            rolls.add(rpTag);
        }
        tag.put("rollPoints", rolls);

        NbtList chests = new NbtList();
        for (ChestPoint cp : chestPoints) {
            NbtCompound bpTag = new NbtCompound();
            bpTag.putLong("standPos", cp.standPos.asLong());
            bpTag.putLong("targetPos", cp.targetPos.asLong());
            chests.add(bpTag);
        }
        tag.put("chestPoints", chests);

        NbtList dumps = new NbtList();
        for (ChestPoint cp : dumpPoints) {
            NbtCompound dpTag = new NbtCompound();
            dpTag.putLong("standPos", cp.standPos.asLong());
            dpTag.putLong("targetPos", cp.targetPos.asLong());
            dumps.add(dpTag);
        }
        tag.put("dumpPoints", dumps);

        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        super.fromTag(tag);
        
        rollPoints.clear();
        if (tag.contains("rollPoints")) {
            NbtList rolls = (NbtList) tag.get("rollPoints");
            for (int i = 0; i < rolls.size(); i++) {
                NbtCompound rpTag = (NbtCompound) rolls.get(i);
                BlockPos stand = BlockPos.fromLong(rpTag.getLong("standPos").orElse(0L));
                BlockPos villager = BlockPos.fromLong(rpTag.getLong("villagerPos").orElse(0L));
                RollPoint rp = new RollPoint(stand, villager);
                if (rpTag.contains("lecternPos")) {
                    rp.lecternPos = BlockPos.fromLong(rpTag.getLong("lecternPos").orElse(0L));
                }
                rp.completed = rpTag.getBoolean("completed").orElse(false);
                rollPoints.add(rp);
            }
        }

        chestPoints.clear();
        if (tag.contains("chestPoints")) {
            NbtList chests = (NbtList) tag.get("chestPoints");
            for (int i = 0; i < chests.size(); i++) {
                NbtCompound cpTag = (NbtCompound) chests.get(i);
                BlockPos standPos = BlockPos.fromLong(cpTag.getLong("standPos").orElse(0L));
                BlockPos targetPos = BlockPos.fromLong(cpTag.getLong("targetPos").orElse(0L));
                chestPoints.add(new ChestPoint(standPos, targetPos));
            }
        }

        dumpPoints.clear();
        if (tag.contains("dumpPoints")) {
            NbtList dumps = (NbtList) tag.get("dumpPoints");
            for (int i = 0; i < dumps.size(); i++) {
                NbtCompound dpTag = (NbtCompound) dumps.get(i);
                BlockPos standPos = BlockPos.fromLong(dpTag.getLong("standPos").orElse(0L));
                BlockPos targetPos = BlockPos.fromLong(dpTag.getLong("targetPos").orElse(0L));
                dumpPoints.add(new ChestPoint(standPos, targetPos));
            }
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
        
        handleSetup(); // Vẫn cho phép set up lúc đang chạy nếu cần

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (isAiming && aimTarget != null) {
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
            
            if (Math.abs(diffYaw) > 2.0f) mc.player.setYaw(mc.player.getYaw() + stepYaw);
            if (Math.abs(diffPitch) > 2.0f) mc.player.setPitch(mc.player.getPitch() + stepPitch);
            
            if (Math.abs(diffYaw) <= 2.0f && Math.abs(diffPitch) <= 2.0f) {
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
                if (!rollPoints.isEmpty()) {
                    while (currentPointIndex < rollPoints.size() && rollPoints.get(currentPointIndex).completed) {
                        currentPointIndex++;
                    }
                    if (currentPointIndex >= rollPoints.size()) {
                        info("Đã hoàn thành Roll tất cả dân làng trong danh sách! Tắt module.");
                        toggle();
                        return;
                    }

                    if (isInventoryFull()) {
                        info("Túi đã đầy! Đi cất sách phù phép.");
                        currentState = State.MOVING_TO_DUMP;
                        currentDumpChestIndex = 0;
                        if (!dumpPoints.isEmpty()) {
                            moveToPoint(dumpPoints.get(currentDumpChestIndex).standPos);
                        } else {
                            error("Chưa cài đặt rương cất đồ (Dump Chest)! Xin hãy dọn túi bằng tay.");
                            toggle();
                        }
                        return;
                    }
                    
                    if (needsRestock()) {
                        info("Hết nguyên liệu khóa nghề! Đi lấy đồ.");
                        currentState = State.MOVING_TO_RESTOCK;
                        currentRestockChestIndex = 0;
                        if (!chestPoints.isEmpty()) {
                            moveToPoint(chestPoints.get(currentRestockChestIndex).standPos);
                        } else {
                            error("Chưa cài đặt rương lấy đồ (Restock Chest)!");
                            toggle();
                        }
                        return;
                    }
                    
                    currentState = State.MOVING_TO_STATION;
                    moveToPoint(rollPoints.get(currentPointIndex).standPos);
                }
                break;
                
            case MOVING_TO_STATION:
                if (isAtPoint(rollPoints.get(currentPointIndex).standPos)) {
                    currentState = State.CHECK_VILLAGER;
                    waitTicks = 10;
                }
                break;
                
            case CHECK_VILLAGER:
                doCheckVillager();
                break;

            case OPEN_GUI:
                if (targetVillagerEntity != null) {
                    Vec3d center = new Vec3d(targetVillagerEntity.getX(), targetVillagerEntity.getY() + targetVillagerEntity.getHeight() / 2, targetVillagerEntity.getZ());
                    startSmoothAim(center, () -> {
                        mc.interactionManager.interactEntity(mc.player, targetVillagerEntity, Hand.MAIN_HAND);
                        currentState = State.CHECK_TRADES;
                        waitTicks = 15; 
                        guiTimeout++;
                        if (guiTimeout > 10) {
                            error("Không thể mở GUI (Có thể do đứng quá xa)! Bỏ qua trạm này.");
                            rollPoints.get(currentPointIndex).completed = true;
                            currentState = State.IDLE;
                        }
                    });
                } else {
                    currentState = State.CHECK_VILLAGER;
                }
                break;

            case CHECK_TRADES:
                doCheckTrades();
                break;
                
            case LOCK_TRADE:
                doLockTrade();
                break;
                
            case BREAK_LECTERN:
                doBreakLectern();
                break;

            case PLACE_LECTERN:
                doPlaceLectern();
                break;

            case MOVING_TO_RESTOCK:
                if (!chestPoints.isEmpty() && isAtPoint(chestPoints.get(currentRestockChestIndex).standPos)) {
                    lookAndInteractChest(chestPoints.get(currentRestockChestIndex).targetPos);
                    currentState = State.RESTOCKING;
                    waitTicks = 10;
                }
                break;
                
            case RESTOCKING:
                doRestockLogic();
                break;
                
            case MOVING_TO_DUMP:
                if (!dumpPoints.isEmpty() && isAtPoint(dumpPoints.get(currentDumpChestIndex).standPos)) {
                    lookAndInteractChest(dumpPoints.get(currentDumpChestIndex).targetPos);
                    currentState = State.DUMPING;
                    waitTicks = 10;
                }
                break;
                
            case DUMPING:
                doDumpLogic();
                break;
                
            case WAIT_FOR_LECTERN_DROP:
                if (countItemsInInventory(Items.LECTERN) > lecternCount) {
                    currentState = State.CHECK_VILLAGER;
                } else {
                    info("Bục văng quá xa hoặc vướng chân dân làng! Đang chạy đi nhặt...");
                    RollPoint rp = rollPoints.get(currentPointIndex);
                    ChatUtils.sendPlayerMsg("#goto " + rp.villagerPos.getX() + " " + rp.villagerPos.getY() + " " + rp.villagerPos.getZ());
                    waitTicks = 40;
                    currentState = State.MOVING_TO_RETRIEVE;
                }
                break;

            case MOVING_TO_RETRIEVE:
                RollPoint rpRetrieve = rollPoints.get(currentPointIndex);
                if (isAtPoint(rpRetrieve.villagerPos)) {
                    info("Đã tiếp cận bục, quay lại vị trí đứng...");
                    ChatUtils.sendPlayerMsg("#goto " + rpRetrieve.standPos.getX() + " " + rpRetrieve.standPos.getY() + " " + rpRetrieve.standPos.getZ());
                    waitTicks = 40;
                    currentState = State.MOVING_BACK_TO_STAND;
                }
                break;
                
            case MOVING_BACK_TO_STAND:
                RollPoint rpBack = rollPoints.get(currentPointIndex);
                if (isAtPoint(rpBack.standPos)) {
                    currentState = State.CHECK_VILLAGER;
                }
                break;
        }
    }

    private void doCheckVillager() {
        RollPoint rp = rollPoints.get(currentPointIndex);
        
        if (rp.lecternPos == null) {
            error("Lỗi: Dân làng này chưa được lưu vị trí Bục (Phím N)! Bỏ qua.");
            rp.completed = true;
            currentState = State.IDLE;
            return;
        }

        Box searchBox = new Box(rp.villagerPos).expand(3.0);
        List<Entity> entities = mc.world.getOtherEntities(null, searchBox);
        
        VillagerEntity closest = null;
        double minDist = Double.MAX_VALUE;
        for (Entity e : entities) {
            if (e instanceof VillagerEntity) {
                double dist = e.squaredDistanceTo(rp.lecternPos.getX() + 0.5, rp.lecternPos.getY(), rp.lecternPos.getZ() + 0.5);
                if (dist < minDist) {
                    minDist = dist;
                    closest = (VillagerEntity) e;
                }
            }
        }
        targetVillagerEntity = closest;
        
        if (targetVillagerEntity == null) {
            error("Không tìm thấy dân làng tại trạm này! Bỏ qua.");
            rp.completed = true;
            currentState = State.IDLE;
            return;
        }

        VillagerEntity villager = (VillagerEntity) targetVillagerEntity;
        String profession = villager.getVillagerData().profession().toString().toLowerCase();

        if (profession.contains("none") || profession.contains("nitwit")) {
            if (mc.world.getBlockState(rp.lecternPos).isAir()) {
                currentState = State.PLACE_LECTERN;
            } else {
                if (jobTimeout > 0) {
                    jobTimeout--;
                    waitTicks = 1; 
                } else {
                    info("Bị dân làng khác cướp bục! Đang đập đi đặt lại...");
                    currentState = State.BREAK_LECTERN;
                }
            }
        } else if (profession.contains("librarian")) {
            guiTimeout = 0;
            currentState = State.OPEN_GUI;
        } else {
            currentState = State.BREAK_LECTERN;
        }
    }

    private void doCheckTrades() {
        if (!(mc.currentScreen instanceof MerchantScreen)) {
            if (waitTicks <= 0) {
                currentState = State.OPEN_GUI;
            }
            return;
        }

        MerchantScreen screen = (MerchantScreen) mc.currentScreen;
        MerchantScreenHandler handler = screen.getScreenHandler();
        
        if (handler.getRecipes().isEmpty()) {
            waitTicks = 5;
            return;
        }

        boolean foundTarget = false;
        int targetRecipeIndex = -1;

        for (int i = 0; i < handler.getRecipes().size(); i++) {
            TradeOffer offer = handler.getRecipes().get(i);
            Item sellItem = offer.getSellItem().getItem();
            
            if (sellItem == Items.ENCHANTED_BOOK) {
                String filter = targetEnchant.get().trim().toLowerCase();
                int minLevel = targetEnchantLevel.get();
                ComponentMap components = offer.getSellItem().getComponents();
                for (ComponentType<?> type : components.getTypes()) {
                    if (type.toString().toLowerCase().contains("enchantment")) {
                        Object comp = components.get(type);
                        if (comp != null) {
                            String compStr = comp.toString().toLowerCase();
                            int idx = compStr.indexOf(filter + "=");
                            if (idx != -1) {
                                int levelIdx = idx + filter.length() + 1;
                                int endIdx = levelIdx;
                                while (endIdx < compStr.length() && Character.isDigit(compStr.charAt(endIdx))) {
                                    endIdx++;
                                }
                                if (endIdx > levelIdx) {
                                    int level = Integer.parseInt(compStr.substring(levelIdx, endIdx));
                                    if (level >= minLevel) {
                                        foundTarget = true;
                                        targetRecipeIndex = i;
                                        break;
                                    }
                                }
                            } else if (compStr.contains(filter) && minLevel <= 1) {
                                foundTarget = true;
                                targetRecipeIndex = i;
                                break;
                            }
                        }
                    }
                }
            }
            if (foundTarget) break;
        }

        if (foundTarget) {
            info("🎉 TÌM THẤY SÁCH MỤC TIÊU! Đang tiến hành giao dịch để khóa nghề...");
            lastRecipeIndex = targetRecipeIndex; 
            mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(targetRecipeIndex));
            waitTicks = getDelay();
            currentState = State.LOCK_TRADE;
        } else {
            mc.player.closeHandledScreen();
            currentState = State.BREAK_LECTERN;
        }
    }

    private void doLockTrade() {
        if (!(mc.currentScreen instanceof MerchantScreen)) return;
        MerchantScreen screen = (MerchantScreen) mc.currentScreen;
        MerchantScreenHandler handler = screen.getScreenHandler();

        if (handler.getSlot(2).hasStack()) {
            mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.PICKUP, mc.player);
            
            int emptySlot = -1;
            for (int i = 3; i < handler.slots.size(); i++) {
                if (!handler.getSlot(i).hasStack()) {
                    emptySlot = i;
                    break;
                }
            }
            
            if (emptySlot != -1) {
                mc.interactionManager.clickSlot(handler.syncId, emptySlot, 0, SlotActionType.PICKUP, mc.player);
            } else {
                mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.THROW, mc.player);
            }
            
            waitTicks = getDelay(); 
            
            info("Đã khóa nghề thành công! Chuyển sang trạm tiếp theo.");
            mc.player.closeHandledScreen();
            rollPoints.get(currentPointIndex).completed = true;
            lastRecipeIndex = -1;
            currentState = State.IDLE;
            return; 
        }

        TradeOffer offer = handler.getRecipes().get(lastRecipeIndex);
        Item buyItem1 = offer.getOriginalFirstBuyItem().getItem();

        boolean moved = moveItemToSlot(handler, buyItem1, 0);
        if (!moved && offer.getSecondBuyItem().isPresent()) {
            Item buyItem2 = (buyItem1 == tradeInput1.get()) ? tradeInput2.get() : tradeInput1.get();
            moved = moveItemToSlot(handler, buyItem2, 1);
        }
        
        if (moved) {
            waitTicks = getDelay();
        } else {
            if (needsRestock()) {
                mc.player.closeHandledScreen();
                lastRecipeIndex = -1;
                currentState = State.MOVING_TO_RESTOCK;
            }
        }
    }

    private void doBreakLectern() {
        RollPoint rp = rollPoints.get(currentPointIndex);
        if (rp.lecternPos == null) {
            error("Chưa lưu tọa độ Lectern (Phím N) cho trạm này!");
            toggle();
            return;
        }

        if (mc.world.getBlockState(rp.lecternPos).isAir()) {
            if (isBreaking) {
                mc.options.attackKey.setPressed(false);
                isBreaking = false;
            }
            waitTicks = 20;
            currentState = State.WAIT_FOR_LECTERN_DROP;
            return;
        }

        if (!isBreaking) {
            lecternCount = countItemsInInventory(Items.LECTERN);
            Vec3d center = new Vec3d(rp.lecternPos.getX() + 0.5, rp.lecternPos.getY() + 0.5, rp.lecternPos.getZ() + 0.5);
            startSmoothAim(center, () -> {
                FindItemResult axe = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof net.minecraft.item.AxeItem);
                if (axe.found()) {
                    InvUtils.swap(axe.slot(), false);
                }
                
                // Sử dụng vanilla attack key để đập khối legit 100%
                mc.options.attackKey.setPressed(true);
                isBreaking = true;
            });
        }
    }

    private void doPlaceLectern() {
        RollPoint rp = rollPoints.get(currentPointIndex);
        
        if (!mc.world.getBlockState(rp.lecternPos).isAir()) {
            currentState = State.CHECK_VILLAGER;
            return;
        }

        FindItemResult lectern = InvUtils.findInHotbar(Items.LECTERN);
        if (!lectern.found()) {
            error("Không có Bục đọc sách (Lectern) trong Hotbar!");
            toggle();
            return;
        }

        Vec3d center = new Vec3d(rp.lecternPos.getX() + 0.5, rp.lecternPos.getY() + 0.5, rp.lecternPos.getZ() + 0.5);
        startSmoothAim(center, () -> {
            InvUtils.swap(lectern.slot(), true);
            
            Vec3d hitVec = new Vec3d(rp.lecternPos.getX() + 0.5, rp.lecternPos.getY() - 0.5, rp.lecternPos.getZ() + 0.5);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, Direction.UP, rp.lecternPos.down(), false));
            mc.player.swingHand(Hand.MAIN_HAND);
            
            // Note: InvUtils.swap(..., true) automatically schedules a swap back.
            
            waitTicks = 10;
            jobTimeout = 40; 
            currentState = State.CHECK_VILLAGER;
        });
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
                    moveToPoint(chestPoints.get(currentRestockChestIndex).standPos);
                } else {
                    error("Đã lục soát toàn bộ rương nhưng vẫn thiếu nguyên liệu! Tắt module.");
                    toggle();
                }
                return;
            }
            info("Đã lấy đủ đồ! Quay lại làm việc.");
            mc.player.closeHandledScreen();
            lastRecipeIndex = -1;
            currentState = State.IDLE;
        }
    }

    private void doDumpLogic() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) return;
        GenericContainerScreen screen = (GenericContainerScreen) mc.currentScreen;
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        
        int containerSize = handler.getInventory().size();
        boolean dumpedAnything = false;
        
        for (int i = containerSize; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).hasStack() && handler.getSlot(i).getStack().getItem() == Items.ENCHANTED_BOOK) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                dumpedAnything = true;
                waitTicks = getDelay();
                return; 
            }
        }
        
        if (!dumpedAnything) {
            if (countItemsInInventory(Items.ENCHANTED_BOOK) > 0 && isInventoryFull()) {
                mc.player.closeHandledScreen();
                currentDumpChestIndex++;
                if (currentDumpChestIndex < dumpPoints.size()) {
                    info("Rương cất đồ đã đầy! Chuyển sang rương tiếp theo...");
                    currentState = State.MOVING_TO_DUMP;
                    moveToPoint(dumpPoints.get(currentDumpChestIndex).standPos);
                } else {
                    error("Tất cả rương cất đồ đều đã ĐẦY! Hãy dọn bớt rương hoặc xả bằng tay.");
                    toggle();
                }
                return;
            }
            info("Đã cất hết sách! Quay lại làm việc.");
            mc.player.closeHandledScreen();
            currentState = State.IDLE;
        }
    }

    private boolean isInventoryFull() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isAtPoint(BlockPos pos) {
        return mc.player.getBlockPos().isWithinDistance(pos, 2.5);
    }

    private boolean moveItemToSlot(MerchantScreenHandler handler, Item item, int targetSlot) {
        if (handler.getSlot(targetSlot).hasStack() && handler.getSlot(targetSlot).getStack().getItem() == item) return false;
        
        for (int i = 3; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).hasStack() && handler.getSlot(i).getStack().getItem() == item) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true; 
            }
        }
        return false;
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
                if (entity instanceof VillagerEntity) {
                    BlockPos standPos = mc.player.getBlockPos();
                    rollPoints.add(new RollPoint(standPos, entity.getBlockPos()));
                    info("Đã lưu Dân làng số #" + rollPoints.size() + ". Bây giờ hãy nhìn vào chỗ sẽ đặt bục và bấm phím cài đặt Bục (N).");
                }
            }
        }
        addVillagerWasPressed = addVillagerIsPressed;
        
        boolean lecternIsPressed = addLecternKey.get().isPressed();
        if (lecternIsPressed && !addLecternWasPressed) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos targetPos = ((net.minecraft.util.hit.BlockHitResult) mc.crosshairTarget).getBlockPos().up();
                if (!rollPoints.isEmpty()) {
                    RollPoint closestRp = null;
                    double minDist = Double.MAX_VALUE;
                    for (RollPoint rp : rollPoints) {
                        double dist = targetPos.getSquaredDistance(rp.villagerPos);
                        if (dist < minDist) {
                            minDist = dist;
                            closestRp = rp;
                        }
                    }
                    if (closestRp != null) {
                        closestRp.lecternPos = targetPos;
                        info("Đã ghép cặp Bục này cho Dân làng gần nhất!");
                    }
                } else {
                    error("Chưa lưu dân làng nào! Hãy bấm phím thêm dân làng trước (I).");
                }
            }
        }
        addLecternWasPressed = lecternIsPressed;
        
        boolean restockIsPressed = addRestockChestKey.get().isPressed();
        if (restockIsPressed && !addRestockWasPressed) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos targetPos = ((net.minecraft.util.hit.BlockHitResult) mc.crosshairTarget).getBlockPos();
                BlockPos standPos = mc.player.getBlockPos();
                chestPoints.add(new ChestPoint(standPos, targetPos));
                info("Đã lưu Rương tiếp tế số #" + chestPoints.size());
            }
        }
        addRestockWasPressed = restockIsPressed;
        
        boolean dumpIsPressed = addDumpChestKey.get().isPressed();
        if (dumpIsPressed && !addDumpChestWasPressed) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos targetPos = ((net.minecraft.util.hit.BlockHitResult) mc.crosshairTarget).getBlockPos();
                BlockPos standPos = mc.player.getBlockPos();
                dumpPoints.add(new ChestPoint(standPos, targetPos));
                info("Đã lưu Rương cất sách (Dump Chest) số #" + dumpPoints.size());
            }
        }
        addDumpChestWasPressed = dumpIsPressed;
        
        boolean clearAllIsPressed = clearAllKey.get().isPressed();
        if (clearAllIsPressed && !clearAllWasPressed) {
            rollPoints.clear();
            chestPoints.clear();
            dumpPoints.clear();
            info("Đã xóa sạch toàn bộ tọa độ Dân làng, Bục và Rương!");
        }
        clearAllWasPressed = clearAllIsPressed;
    }

    private void moveToPoint(BlockPos pos) {
        info("Going to " + pos.toShortString());
        ChatUtils.sendPlayerMsg("#goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        waitTicks = 60; 
    }

    private void startSmoothAim(Vec3d target, Runnable callback) {
        if (rotationMode.get() == RotationMode.Packet) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), 50, true, callback);
        } else {
            isAiming = true;
            aimTarget = target;
            aimCallback = callback;
        }
    }

    private void lookAndInteractChest(BlockPos targetPos) {
        Vec3d hitVec = new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        startSmoothAim(hitVec, () -> {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, Direction.UP, targetPos, false));
        });
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        for (int i = 0; i < rollPoints.size(); i++) {
            RollPoint p = rollPoints.get(i);
            event.renderer.box(new Box(p.villagerPos), p.completed ? Color.GREEN : Color.CYAN, p.completed ? Color.GREEN : Color.CYAN, ShapeMode.Lines, 0);
            if (p.lecternPos != null) {
                event.renderer.box(new Box(p.lecternPos), Color.MAGENTA, Color.MAGENTA, ShapeMode.Lines, 0);
            }
        }
        for (ChestPoint cp : chestPoints) {
            event.renderer.box(new Box(cp.targetPos), Color.ORANGE, Color.ORANGE, ShapeMode.Lines, 0);
        }
        for (ChestPoint cp : dumpPoints) {
            event.renderer.box(new Box(cp.targetPos), Color.RED, Color.RED, ShapeMode.Lines, 0);
        }
    }

    public enum RotationMode {
        Legit, Packet
    }

    private enum State {
        IDLE, MOVING_TO_STATION, CHECK_VILLAGER, OPEN_GUI, CHECK_TRADES, LOCK_TRADE, 
        BREAK_LECTERN, PLACE_LECTERN, MOVING_TO_RESTOCK, RESTOCKING, MOVING_TO_DUMP, DUMPING, WAIT_FOR_LECTERN_DROP, MOVING_TO_RETRIEVE, MOVING_BACK_TO_STAND
    }

    private static class RollPoint {
        public final BlockPos standPos;
        public final BlockPos villagerPos;
        public BlockPos lecternPos;
        public boolean completed = false;

        public RollPoint(BlockPos standPos, BlockPos villagerPos) {
            this.standPos = standPos;
            this.villagerPos = villagerPos;
        }
    }

    private static class ChestPoint {
        public BlockPos standPos;
        public BlockPos targetPos;

        public ChestPoint(BlockPos standPos, BlockPos targetPos) {
            this.standPos = standPos;
            this.targetPos = targetPos;
        }
    }
}
