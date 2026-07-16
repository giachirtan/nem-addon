package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class AutoEp extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgKeys = settings.createGroup("Keybinds & ID");
    private final SettingGroup sgPos = settings.createGroup("Quản lý Vị trí");
    private final SettingGroup sgDelays = settings.createGroup("Delays");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // === BIẾN ===
    private BlockPos pos1, pos2, pos3, pos4, pos5, posArmor, posOutput;
    private final List<BlockPos> anvilList = new ArrayList<>();
    private int timer = 0, guiTimer = 0, keyTimer = 0;

    // Webhook vars
    private int anvilFailCount = 0;
    private long lastAlertTime = 0;

    private enum Goal { GET_1, GET_2, GET_3, GET_4, GET_5, GET_ARMOR, DO_ANVIL, STORE, IDLE }
    private Goal currentGoal = Goal.IDLE;

    // === SETTINGS ===
    private final Setting<Boolean> setupMode = sgGeneral.add(new BoolSetting.Builder().name("setup-mode").defaultValue(false).build());
    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder().name("debug-mode").defaultValue(true).build());
    private final Setting<Boolean> autoStore = sgGeneral.add(new BoolSetting.Builder().name("auto-store").description("Tự động cất đồ xong vào rương.").defaultValue(true).build());
    private final Setting<Boolean> antiDismount = sgGeneral.add(new BoolSetting.Builder().name("anti-dismount").defaultValue(true).build());
    private final Setting<Integer> lootAmount = sgGeneral.add(new IntSetting.Builder().name("loot-amount").defaultValue(5).min(1).build());
    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder().name("webhook-url").defaultValue("").build());

    // CHỌN DANH SÁCH ITEM ĐỂ ÉP
    private final Setting<List<Item>> targetItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("target-items")
        .description("Chọn các món đồ (giáp, kiếm, cúp...) bạn muốn ép.")
        .defaultValue(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS)
        .build());

    // ID Sách & Phím
    private final Setting<String> id1 = sgKeys.add(new StringSetting.Builder().name("enchant-id-1").defaultValue("mending").build());
    private final Setting<Keybind> key1 = sgKeys.add(new KeybindSetting.Builder().name("key-save-1").defaultValue(Keybind.none()).build());
    
    private final Setting<String> id2 = sgKeys.add(new StringSetting.Builder().name("enchant-id-2").defaultValue("").build());
    private final Setting<Keybind> key2 = sgKeys.add(new KeybindSetting.Builder().name("key-save-2").defaultValue(Keybind.none()).build());
    
    private final Setting<String> id3 = sgKeys.add(new StringSetting.Builder().name("enchant-id-3").defaultValue("").build());
    private final Setting<Keybind> key3 = sgKeys.add(new KeybindSetting.Builder().name("key-save-3").defaultValue(Keybind.none()).build());
    
    private final Setting<String> id4 = sgKeys.add(new StringSetting.Builder().name("enchant-id-4").defaultValue("").build());
    private final Setting<Keybind> key4 = sgKeys.add(new KeybindSetting.Builder().name("key-save-4").defaultValue(Keybind.none()).build());
    
    private final Setting<String> id5 = sgKeys.add(new StringSetting.Builder().name("enchant-id-5").defaultValue("").build());
    private final Setting<Keybind> key5 = sgKeys.add(new KeybindSetting.Builder().name("key-save-5").defaultValue(Keybind.none()).build());

    private final Setting<Keybind> keyArmor = sgKeys.add(new KeybindSetting.Builder().name("key-save-item").description("Phím lưu rương chứa đồ cần ép").defaultValue(Keybind.none()).build());
    private final Setting<Keybind> keyAnvil = sgKeys.add(new KeybindSetting.Builder().name("key-add-anvil").defaultValue(Keybind.none()).build());
    private final Setting<Keybind> keyOutput = sgKeys.add(new KeybindSetting.Builder().name("key-save-output").description("Phím lưu Rương Cất Thành Phẩm.").defaultValue(Keybind.none()).build());

    private final Setting<Boolean> clearAll = sgPos.add(new BoolSetting.Builder().name("XÓA TẤT CẢ").defaultValue(false).onChanged(v -> {
        if(v) { pos1=null; pos2=null; pos3=null; pos4=null; pos5=null; posArmor=null; posOutput=null; anvilList.clear(); info("Đã xóa hết."); }
    }).build());
    private final Setting<Boolean> showPos = sgPos.add(new BoolSetting.Builder().name("Hiện Tọa Độ").defaultValue(false).onChanged(v -> { if(v) printPositions(); }).build());

    private final Setting<Integer> guiWaitTime = sgDelays.add(new IntSetting.Builder().name("gui-wait-base").defaultValue(15).min(5).build());
    private final Setting<Integer> actionDelay = sgDelays.add(new IntSetting.Builder().name("action-base").defaultValue(4).min(2).build());
    private final Setting<Integer> randomRange = sgDelays.add(new IntSetting.Builder().name("randomness").defaultValue(5).min(0).build());

    private final Setting<Boolean> renderEsp = sgRender.add(new BoolSetting.Builder().name("render-esp").defaultValue(true).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Lines).build());
    private final Setting<SettingColor> espColor = sgRender.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(0, 255, 200, 255)).build());

    public AutoEp() {
        super(AddonTemplate.CATEGORY, "AutoAnvil V1", "v48.0: Storage Mode.");
    }

    @Override
    public void onActivate() {
        timer = 0; guiTimer = 0; keyTimer = 0; currentGoal = Goal.IDLE;
        anvilFailCount = 0; lastAlertTime = 0;
        clearAll.set(false); showPos.set(false);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderEsp.get()) return;
        renderBox(event, pos1); renderBox(event, pos2); renderBox(event, pos3); 
        renderBox(event, pos4); renderBox(event, pos5); renderBox(event, posArmor); renderBox(event, posOutput);
        for (BlockPos pos : anvilList) renderBox(event, pos);
    }
    private void renderBox(Render3DEvent event, BlockPos pos) { if (pos != null) event.renderer.box(pos, espColor.get(), espColor.get(), shapeMode.get(), 0); }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (antiDismount.get() && mc.player.hasVehicle()) mc.options.sneakKey.setPressed(false);
        if (clearAll.get()) clearAll.set(false);
        if (showPos.get()) showPos.set(false);

        if (setupMode.get()) {
            if (mc.currentScreen == null) handleSetupKeys();
            return;
        }

        if (timer > 0) { timer--; return; }

        if (mc.currentScreen != null) {
            handleInGui();
        } else {
            guiTimer = guiWaitTime.get() + getRandom();
            handleMovement();
        }
    }

    private int getRandom() { return randomRange.get() > 0 ? ThreadLocalRandom.current().nextInt(0, randomRange.get() + 1) : 0; }
    private void setDelay(int base) { timer = base + getRandom(); }

    private void sendWebhook(String message) {
        if (webhookUrl.get().isEmpty()) return;
        if (System.currentTimeMillis() - lastAlertTime < 60000) return;
        lastAlertTime = System.currentTimeMillis();
        String finalMsg = message + " - **Thằng Huy bị lỏ chim**";
        new Thread(() -> {
            try {
                URL url = new URL(webhookUrl.get());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                String jsonInputString = "{\"content\": \"" + finalMsg + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                conn.getResponseCode();
            } catch (Exception ignored) {}
        }).start();
    }

    private BlockPos getValidAnvilPos() {
        if (anvilList.isEmpty()) return null;
        for (BlockPos pos : anvilList) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.getBlock() instanceof AnvilBlock) {
                anvilFailCount = 0;
                return pos;
            }
        }
        anvilFailCount++;
        if (anvilFailCount >= 3) {
            sendWebhook("Hết Đe (Anvil) rồi!");
            anvilFailCount = 0;
        }
        return null;
    }

    private void determineNextGoal() {
        if (posOutput != null && hasPerfectItem() && autoStore.get()) { currentGoal = Goal.STORE; return; }

        if (pos1 != null && !id1.get().isEmpty() && countBooksInInv(id1.get()) < lootAmount.get()) { currentGoal = Goal.GET_1; return; }
        if (pos2 != null && !id2.get().isEmpty() && countBooksInInv(id2.get()) < lootAmount.get()) { currentGoal = Goal.GET_2; return; }
        if (pos3 != null && !id3.get().isEmpty() && countBooksInInv(id3.get()) < lootAmount.get()) { currentGoal = Goal.GET_3; return; }
        if (pos4 != null && !id4.get().isEmpty() && countBooksInInv(id4.get()) < lootAmount.get()) { currentGoal = Goal.GET_4; return; }
        if (pos5 != null && !id5.get().isEmpty() && countBooksInInv(id5.get()) < lootAmount.get()) { currentGoal = Goal.GET_5; return; }
        if (posArmor != null && countItemInInv() < lootAmount.get()) { currentGoal = Goal.GET_ARMOR; return; }

        if (getValidAnvilPos() != null && canAnvil()) { currentGoal = Goal.DO_ANVIL; return; }

        currentGoal = Goal.IDLE;
    }

    private void handleMovement() {
        determineNextGoal();
        BlockPos targetPos = null;
        switch (currentGoal) {
            case GET_1: targetPos = pos1; break;
            case GET_2: targetPos = pos2; break;
            case GET_3: targetPos = pos3; break;
            case GET_4: targetPos = pos4; break;
            case GET_5: targetPos = pos5; break;
            case GET_ARMOR: targetPos = posArmor; break;
            case DO_ANVIL: targetPos = getValidAnvilPos(); break;
            case STORE: targetPos = posOutput; break;
            case IDLE: return;
        }

        if (targetPos != null && isLookingAt(targetPos)) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(targetPos.toCenterPos(), net.minecraft.util.math.Direction.UP, targetPos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            setDelay(5);
        }
    }

    private void handleInGui() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            if (guiTimer > 0) { guiTimer--; return; }
            if (currentGoal == Goal.STORE) handleDepositing();
            else handleChestLoot();
        } else if (mc.currentScreen instanceof AnvilScreen) {
            handleAnvilLogic();
        } else if (mc.currentScreen instanceof InventoryScreen) {
            mc.player.closeHandledScreen();
        }
    }

    private void handleDepositing() {
        GenericContainerScreenHandler container = ((GenericContainerScreen) mc.currentScreen).getScreenHandler();
        int chestSize = container.slots.size() - 36;
        boolean hasItemToStore = false;

        for (int i = chestSize; i < container.slots.size(); i++) {
            ItemStack stack = container.getSlot(i).getStack();
            if (isPerfectItem(stack)) { 
                shiftClickSlot(container.syncId, i);
                setDelay(actionDelay.get());
                hasItemToStore = true;
                return;
            }
        }

        if (!hasItemToStore) {
            if (debugMode.get()) info("Đã cất hết thành phẩm.");
            mc.player.closeHandledScreen();
            setDelay(20);
        }
    }

    private void handleChestLoot() {
        GenericContainerScreenHandler container = ((GenericContainerScreen) mc.currentScreen).getScreenHandler();

        int currentCount = 0;
        switch (currentGoal) {
            case GET_1: currentCount = countBooksInInv(id1.get()); break;
            case GET_2: currentCount = countBooksInInv(id2.get()); break;
            case GET_3: currentCount = countBooksInInv(id3.get()); break;
            case GET_4: currentCount = countBooksInInv(id4.get()); break;
            case GET_5: currentCount = countBooksInInv(id5.get()); break;
            case GET_ARMOR: currentCount = countItemInInv(); break;
        }

        if (currentCount >= lootAmount.get()) { mc.player.closeHandledScreen(); setDelay(20); return; }

        int chestSize = container.slots.size() - 36;
        int chestStock = 0;

        for (int i = 0; i < chestSize; i++) {
            ItemStack stack = container.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            boolean match = false;
            if (currentGoal == Goal.GET_1 && checkSimpleContains(stack, id1.get())) match = true;
            else if (currentGoal == Goal.GET_2 && checkSimpleContains(stack, id2.get())) match = true;
            else if (currentGoal == Goal.GET_3 && checkSimpleContains(stack, id3.get())) match = true;
            else if (currentGoal == Goal.GET_4 && checkSimpleContains(stack, id4.get())) match = true;
            else if (currentGoal == Goal.GET_5 && checkSimpleContains(stack, id5.get())) match = true;
            else if (currentGoal == Goal.GET_ARMOR && isTargetItem(stack)) match = true;

            if (match) {
                chestStock += stack.getCount();
                shiftClickSlot(container.syncId, i);
                setDelay(actionDelay.get());
                return;
            }
        }

        if (chestStock == 0) {
            String missingItem = "Item";
            if (currentGoal == Goal.GET_1) missingItem = id1.get();
            else if (currentGoal == Goal.GET_2) missingItem = id2.get();
            else if (currentGoal == Goal.GET_3) missingItem = id3.get();
            else if (currentGoal == Goal.GET_4) missingItem = id4.get();
            else if (currentGoal == Goal.GET_5) missingItem = id5.get();
            
            sendWebhook("Hết " + missingItem + " rồi!");
            mc.player.closeHandledScreen();
            setDelay(50);
        }
    }

    private void handleAnvilLogic() {
        AnvilScreenHandler anvil = ((AnvilScreen) mc.currentScreen).getScreenHandler();

        if (!anvil.getSlot(2).getStack().isEmpty()) {
            shiftClickSlot(anvil.syncId, 2); setDelay(actionDelay.get()); return;
        }

        if (anvil.getSlot(0).getStack().isEmpty()) {
            for (int i = 3; i < anvil.slots.size(); i++) {
                ItemStack stack = anvil.getSlot(i).getStack();
                if (isTargetItem(stack)) {
                    if (autoStore.get() && isPerfectItem(stack)) continue;
                    if (findSmartBookForItem(stack) != -1) {
                        shiftClickSlot(anvil.syncId, i); setDelay(actionDelay.get()); return;
                    }
                }
            }
            mc.player.closeHandledScreen();
            setDelay(20);
            return;
        }

        if (!anvil.getSlot(0).getStack().isEmpty() && anvil.getSlot(1).getStack().isEmpty()) {
            ItemStack armor = anvil.getSlot(0).getStack();
            int bookSlot = findSmartBookForItem(armor);

            if (bookSlot != -1) {
                shiftClickSlot(anvil.syncId, bookSlot); setDelay(actionDelay.get()); return;
            } else {
                if (isPerfectItem(armor) && autoStore.get()) {
                    shiftClickSlot(anvil.syncId, 0);
                    setDelay(actionDelay.get()); return;
                } else {
                    shiftClickSlot(anvil.syncId, 0);
                    mc.player.closeHandledScreen();
                    setDelay(actionDelay.get()); return;
                }
            }
        }
    }

    private void shiftClickSlot(int syncId, int slotId) { mc.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player); }

    private boolean checkSimpleContains(ItemStack stack, String keyword) {
        if (stack.isEmpty() || keyword.isEmpty()) return false;
        Set<RegistryEntry<Enchantment>> enchants = EnchantmentHelper.getEnchantments(stack).getEnchantments();
        for (RegistryEntry<Enchantment> entry : enchants) {
            if (entry.getIdAsString().toLowerCase().contains(keyword.toLowerCase())) return true;
        }
        return false;
    }

    private boolean isPerfectItem(ItemStack stack) {
        if (pos1 != null && !id1.get().isEmpty() && !checkSimpleContains(stack, id1.get())) return false;
        if (pos2 != null && !id2.get().isEmpty() && !checkSimpleContains(stack, id2.get())) return false;
        if (pos3 != null && !id3.get().isEmpty() && !checkSimpleContains(stack, id3.get())) return false;
        if (pos4 != null && !id4.get().isEmpty() && !checkSimpleContains(stack, id4.get())) return false;
        if (pos5 != null && !id5.get().isEmpty() && !checkSimpleContains(stack, id5.get())) return false;
        return true;
    }

    private boolean hasPerfectItem() {
        for (int i = 0; i < 36; i++) {
            if (isTargetItem(mc.player.getInventory().getStack(i)) && isPerfectItem(mc.player.getInventory().getStack(i))) return true;
        }
        return false;
    }

    private int findSmartBookForItem(ItemStack armor) {
        AnvilScreenHandler anvil = ((AnvilScreen) mc.currentScreen).getScreenHandler();
        for (int i = 3; i < anvil.slots.size(); i++) {
            ItemStack s = anvil.getSlot(i).getStack();
            if (s.getItem() == Items.ENCHANTED_BOOK) {
                if (pos1 != null && !id1.get().isEmpty() && checkSimpleContains(s, id1.get()) && !checkSimpleContains(armor, id1.get())) return i;
                if (pos2 != null && !id2.get().isEmpty() && checkSimpleContains(s, id2.get()) && !checkSimpleContains(armor, id2.get())) return i;
                if (pos3 != null && !id3.get().isEmpty() && checkSimpleContains(s, id3.get()) && !checkSimpleContains(armor, id3.get())) return i;
                if (pos4 != null && !id4.get().isEmpty() && checkSimpleContains(s, id4.get()) && !checkSimpleContains(armor, id4.get())) return i;
                if (pos5 != null && !id5.get().isEmpty() && checkSimpleContains(s, id5.get()) && !checkSimpleContains(armor, id5.get())) return i;
            }
        }
        return -1;
    }

    private boolean isTargetItem(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == Items.ENCHANTED_BOOK) return false;
        return targetItems.get().contains(stack.getItem());
    }
    
    private int countBooksInInv(String keyword) { int count = 0; for (int i=0; i<36; i++) if (checkSimpleContains(mc.player.getInventory().getStack(i), keyword)) count++; return count; }
    private int countItemInInv() { int count = 0; for (int i=0; i<36; i++) if (isTargetItem(mc.player.getInventory().getStack(i))) count++; return count; }

    private boolean canAnvil() {
        boolean hasNeeds = false; boolean hasBooks = false;
        for (int i=0; i<36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.ENCHANTED_BOOK) hasBooks = true;
            if (isTargetItem(s) && !isPerfectItem(s)) hasNeeds = true;
        }
        return hasNeeds && hasBooks;
    }

    private boolean isLookingAt(BlockPos targetPos) { HitResult hit = mc.crosshairTarget; return hit != null && hit.getType() == HitResult.Type.BLOCK && ((BlockHitResult) hit).getBlockPos().equals(targetPos); }
    private BlockPos getLookingPos() { HitResult hit = mc.crosshairTarget; return (hit != null && hit.getType() == HitResult.Type.BLOCK) ? ((BlockHitResult) hit).getBlockPos() : null; }

    private void printPositions() {
        info("Sách 1: " + (pos1!=null)); info("Sách 2: " + (pos2!=null)); info("Sách 3: " + (pos3!=null)); 
        info("Sách 4: " + (pos4!=null)); info("Sách 5: " + (pos5!=null));
        info("Đồ ép: " + (posArmor!=null)); info("Rương Cất: " + (posOutput!=null));
        info("Số Đe: " + anvilList.size());
    }

    private void handleSetupKeys() {
        if (keyTimer > 0) { keyTimer--; return; }
        BlockPos targetPos = getLookingPos();
        if (targetPos == null) return;
        String name = ""; boolean pressed = false;

        if (Input.isKeyPressed(key1.get().getValue())) { pos1 = targetPos; name = "Rương 1"; pressed = true; }
        else if (Input.isKeyPressed(key2.get().getValue())) { pos2 = targetPos; name = "Rương 2"; pressed = true; }
        else if (Input.isKeyPressed(key3.get().getValue())) { pos3 = targetPos; name = "Rương 3"; pressed = true; }
        else if (Input.isKeyPressed(key4.get().getValue())) { pos4 = targetPos; name = "Rương 4"; pressed = true; }
        else if (Input.isKeyPressed(key5.get().getValue())) { pos5 = targetPos; name = "Rương 5"; pressed = true; }
        
        else if (Input.isKeyPressed(keyArmor.get().getValue())) { posArmor = targetPos; name = "Đồ Ép"; pressed = true; }
        else if (Input.isKeyPressed(keyOutput.get().getValue())) { posOutput = targetPos; name = "Rương Cất"; pressed = true; }

        else if (Input.isKeyPressed(keyAnvil.get().getValue())) {
            if (!anvilList.contains(targetPos)) { anvilList.add(targetPos); name = "Đã THÊM Đe (Tổng: " + anvilList.size() + ")"; pressed = true; }
        }

        else if (Input.isKeyPressed(GLFW.GLFW_KEY_DELETE)) {
            if (targetPos.equals(pos1)) pos1=null; else if (targetPos.equals(pos2)) pos2=null;
            else if (targetPos.equals(pos3)) pos3=null; else if (targetPos.equals(pos4)) pos4=null;
            else if (targetPos.equals(pos5)) pos5=null; else if (targetPos.equals(posArmor)) posArmor=null;
            else if (targetPos.equals(posOutput)) posOutput=null;
            else if (anvilList.contains(targetPos)) { anvilList.remove(targetPos); name="Đã Xóa Đe này"; pressed=true; }
            else { name="Đã Xóa"; pressed=true; }
        }

        if (pressed) { ChatUtils.info(name + ": " + targetPos.toShortString()); keyTimer = 10; }
    }
}
