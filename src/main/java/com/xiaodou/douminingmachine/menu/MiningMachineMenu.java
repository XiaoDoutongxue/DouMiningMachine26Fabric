package com.xiaodou.douminingmachine.menu;

import com.xiaodou.douminingmachine.DouMiningMachine;
import com.xiaodou.douminingmachine.block.entity.MiningMachineBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MiningMachineMenu extends AbstractContainerMenu {
    public static final int FUEL_SLOT = 0;
    public static final int FILTER_FIRST_SLOT = 1;
    public static final int FILTER_SLOT_COUNT = 9;
    public static final int MACHINE_SLOT_COUNT = 10;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int PLAYER_HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int PLAYER_HOTBAR_END = PLAYER_HOTBAR_START + 9;

    private final Container container;
    private final ContainerData data;

    public MiningMachineMenu(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, new SimpleContainer(MACHINE_SLOT_COUNT), new SimpleContainerData(6));
    }

    public MiningMachineMenu(int syncId, Inventory playerInventory, Container container, ContainerData data) {
        super(DouMiningMachine.MINING_MACHINE_MENU, syncId);
        checkContainerSize(container, MACHINE_SLOT_COUNT);
        checkContainerDataCount(data, 6);
        this.container = container;
        this.data = data;

        // 燃料槽：只允许放煤炭。
        this.addSlot(new Slot(container, FUEL_SLOT, 152, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.COAL);
            }
        });

        // 左侧 3x3 过滤槽：挖矿机=矿石，挖石机=石头类，铲土机=泥土/草方块类。
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slot = FILTER_FIRST_SLOT + row * 3 + col;
                this.addSlot(new Slot(container, slot, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        if (container instanceof MiningMachineBlockEntity machine) {
                            return machine.canUseAsFilter(stack);
                        }
                        return MiningMachineBlockEntity.isAnyMachineFilterItem(stack);
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
            }
        }

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
        addDataSlots(data);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < 9; ++column) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int column = 0; column < 9; ++column) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.container instanceof MiningMachineBlockEntity machine) {
            if (id == 0) {
                machine.toggleMining(player);
                return true;
            }
            if (id == 1) {
                machine.adjustChunkCount(player, -1);
                return true;
            }
            if (id == 2) {
                machine.adjustChunkCount(player, 1);
                return true;
            }
            if (id == 3) {
                machine.adjustSpeedLevel(player, -1);
                return true;
            }
            if (id == 4) {
                machine.adjustSpeedLevel(player, 1);
                return true;
            }
            if (id == 5) {
                machine.toggleShovelMode(player);
                return true;
            }
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < MACHINE_SLOT_COUNT) {
                if (!this.moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (stack.is(Items.COAL)) {
                    if (!this.moveItemStackTo(stack, FUEL_SLOT, FUEL_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (isAllowedFilterStack(stack)) {
                    if (!this.moveItemStackTo(stack, FILTER_FIRST_SLOT, FILTER_FIRST_SLOT + FILTER_SLOT_COUNT, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index < PLAYER_INVENTORY_END) {
                    if (!this.moveItemStackTo(stack, PLAYER_HOTBAR_START, PLAYER_HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    private boolean isAllowedFilterStack(ItemStack stack) {
        if (this.container instanceof MiningMachineBlockEntity machine) {
            return machine.canUseAsFilter(stack);
        }
        return MiningMachineBlockEntity.isAnyMachineFilterItem(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    public boolean isActive() {
        return this.data.get(0) != 0;
    }

    public int getFuelTicks() {
        return this.data.get(1);
    }

    public int getMiningProgress() {
        return this.data.get(2);
    }

    public int getChunkCount() {
        return this.data.get(3);
    }

    public int getSpeedLevel() {
        return this.data.get(4);
    }

    public int getFuelMultiplier() {
        int speed = Math.max(1, getSpeedLevel());
        return Math.max(1, getChunkCount()) * speed * speed;
    }

    public boolean isShovelWholeChunkMode() {
        return this.data.get(5) != 0;
    }
}
