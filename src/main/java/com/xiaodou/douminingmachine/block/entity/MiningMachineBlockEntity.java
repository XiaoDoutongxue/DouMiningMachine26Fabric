package com.xiaodou.douminingmachine.block.entity;

import com.xiaodou.douminingmachine.DouMiningMachine;
import com.xiaodou.douminingmachine.menu.MiningMachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MiningMachineBlockEntity extends BlockEntity implements Container, MenuProvider {
    private static final int COAL_BURN_TICKS = 1600;
    private static final int TICKS_PER_BLOCK = 80;
    private static final int CHUNK_SIZE = 16;
    private static final int MESSAGE_COOLDOWN_TICKS = 100;
    private static final int MIN_CHUNK_COUNT = 1;
    private static final int MAX_CHUNK_COUNT = 100;
    private static final int MIN_SPEED_LEVEL = 1;
    private static final int MAX_SPEED_LEVEL = 10;

    // 机器槽位：0 = 煤炭燃料，1-9 = 过滤槽。
    private static final int FUEL_SLOT = 0;
    private static final int FILTER_FIRST_SLOT = 1;
    private static final int FILTER_LAST_SLOT = 9;
    private static final int INVENTORY_SIZE = 10;

    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private static final TagKey<Block> ORES_TAG = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath("c", "ores")
    );

    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);

    private boolean active;
    private int fuelTicks;
    private int miningProgress;
    private int chunkCount = MIN_CHUNK_COUNT;
    private int speedLevel = MIN_SPEED_LEVEL;
    /**
     * 铲土机模式：false = 只铲与机器相同 Y 层；true = 扫描设定区块内所有 Y 层的泥土/草方块。
     */
    private boolean shovelWholeChunkMode;
    private @Nullable UUID lastOperator;
    private long lastNoChestMessageTick;
    private long lastChestFullMessageTick;
    private long lastNoTargetMessageTick;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> active ? 1 : 0;
                case 1 -> fuelTicks;
                case 2 -> miningProgress;
                case 3 -> chunkCount;
                case 4 -> speedLevel;
                case 5 -> shovelWholeChunkMode ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> active = value != 0;
                case 1 -> fuelTicks = value;
                case 2 -> miningProgress = value;
                case 3 -> chunkCount = clampChunkCount(value);
                case 4 -> speedLevel = clampSpeedLevel(value);
                case 5 -> shovelWholeChunkMode = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public MiningMachineBlockEntity(BlockPos pos, BlockState state) {
        super(DouMiningMachine.MINING_MACHINE_BLOCK_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MiningMachineBlockEntity machine) {
        if (!(level instanceof ServerLevel serverLevel) || !machine.active) {
            return;
        }

        MachineKind kind = machine.getKind();
        Container output = machine.findOutputContainer(serverLevel, pos);
        if (output == null) {
            machine.messageWithCooldown(serverLevel, MessageType.NO_CHEST, Component.literal("§c" + kind.displayName + "旁边没有箱子，已暂停工作！"));
            return;
        }

        if (machine.fuelTicks <= 0 && !machine.consumeCoal()) {
            machine.stop(serverLevel, Component.literal("§c燃料耗尽，" + kind.displayName + "已停止！"));
            return;
        }

        BlockPos targetPos = machine.findNextTarget(serverLevel, pos, kind);
        if (targetPos == null) {
            machine.messageWithCooldown(serverLevel, MessageType.NO_TARGET, Component.literal("§e" + kind.displayName + "扫描范围内暂时没有符合过滤条件的方块。"));
            return;
        }

        int fuelCostPerTick = machine.getFuelMultiplier();
        machine.fuelTicks = Math.max(0, machine.fuelTicks - fuelCostPerTick);
        machine.miningProgress += Math.max(MIN_SPEED_LEVEL, machine.speedLevel);

        if (machine.miningProgress >= TICKS_PER_BLOCK) {
            machine.miningProgress = 0;
            if (!machine.mineTargetToContainer(serverLevel, targetPos, output, kind)) {
                machine.messageWithCooldown(serverLevel, MessageType.CHEST_FULL, Component.literal("§c" + kind.displayName + "旁边的箱子满了，已暂停工作！"));
                return;
            }
        }

        machine.setChanged();
    }

    public void toggleMining(Player player) {
        MachineKind kind = getKind();
        this.lastOperator = player.getUUID();
        if (this.active) {
            this.active = false;
            this.miningProgress = 0;
            player.sendSystemMessage(Component.literal("§e" + kind.displayName + "已停止。"));
        } else {
            if (this.fuelTicks <= 0 && (this.items.get(FUEL_SLOT).isEmpty() || !this.items.get(FUEL_SLOT).is(Items.COAL))) {
                player.sendSystemMessage(Component.literal("§c请先放入煤炭燃料！"));
                return;
            }
            this.active = true;
            String modeText = kind == MachineKind.SHOVEL ? "§a，模式：§e" + getShovelModeName() : "";
            player.sendSystemMessage(Component.literal("§a" + kind.displayName + "已启动！当前范围：§e" + this.chunkCount + " §a个区块，速度：§e" + this.speedLevel + "x" + modeText + " §a，燃料消耗倍率：§e" + getFuelMultiplier() + "x"));
        }
        setChanged();
    }

    public void adjustChunkCount(Player player, int delta) {
        int old = this.chunkCount;
        this.chunkCount = clampChunkCount(this.chunkCount + delta);
        if (old == this.chunkCount) {
            if (delta > 0) {
                player.sendSystemMessage(Component.literal("§c范围最多只能设置为 100 个区块！"));
            } else {
                player.sendSystemMessage(Component.literal("§c范围最少为 1 个区块！"));
            }
            return;
        }
        player.sendSystemMessage(Component.literal("§a范围已调整为：§e" + this.chunkCount + " §a个区块，当前速度：§e" + this.speedLevel + "x §a，燃料消耗倍率：§e" + getFuelMultiplier() + "x"));
        setChanged();
    }

    public void adjustSpeedLevel(Player player, int delta) {
        int old = this.speedLevel;
        this.speedLevel = clampSpeedLevel(this.speedLevel + delta);
        if (old == this.speedLevel) {
            if (delta > 0) {
                player.sendSystemMessage(Component.literal("§c速度最多只能设置为 10 倍！"));
            } else {
                player.sendSystemMessage(Component.literal("§c速度最少为 1 倍！"));
            }
            return;
        }
        player.sendSystemMessage(Component.literal("§a运行速度已调整为：§e" + this.speedLevel + "x §a，当前燃料消耗倍率：§e" + getFuelMultiplier() + "x"));
        setChanged();
    }


    public void toggleShovelMode(Player player) {
        MachineKind kind = getKind();
        if (kind != MachineKind.SHOVEL) {
            player.sendSystemMessage(Component.literal("§c只有铲土机可以切换铲土模式！"));
            return;
        }
        this.shovelWholeChunkMode = !this.shovelWholeChunkMode;
        this.miningProgress = 0;
        player.sendSystemMessage(Component.literal("§a铲土机模式已切换为：§e" + getShovelModeName()));
        setChanged();
    }

    public boolean isShovelWholeChunkMode() {
        return this.shovelWholeChunkMode;
    }

    private String getShovelModeName() {
        return this.shovelWholeChunkMode ? "整区块" : "同层";
    }

    private int getFuelMultiplier() {
        int chunkMultiplier = Math.max(MIN_CHUNK_COUNT, this.chunkCount);
        int speed = Math.max(MIN_SPEED_LEVEL, this.speedLevel);
        // 速度越高，燃料消耗按平方级增加：10x 速度 = 100 倍速度燃料消耗。
        return chunkMultiplier * speed * speed;
    }

    private static int clampChunkCount(int value) {
        return Math.max(MIN_CHUNK_COUNT, Math.min(MAX_CHUNK_COUNT, value));
    }

    private static int clampSpeedLevel(int value) {
        return Math.max(MIN_SPEED_LEVEL, Math.min(MAX_SPEED_LEVEL, value));
    }

    private boolean consumeCoal() {
        ItemStack stack = this.items.get(FUEL_SLOT);
        if (!stack.is(Items.COAL)) {
            return false;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            this.items.set(FUEL_SLOT, ItemStack.EMPTY);
        }
        this.fuelTicks += COAL_BURN_TICKS;
        setChanged();
        return true;
    }

    private void stop(ServerLevel level, Component message) {
        this.active = false;
        this.miningProgress = 0;
        notifyLastOperator(level, message);
        setChanged();
    }

    private Container findOutputContainer(ServerLevel level, BlockPos pos) {
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(direction));
            if (blockEntity instanceof Container container && container != this) {
                return container;
            }
        }
        return null;
    }

    private @Nullable BlockPos findNextTarget(ServerLevel level, BlockPos machinePos, MachineKind kind) {
        int machineChunkX = Math.floorDiv(machinePos.getX(), CHUNK_SIZE);
        int machineChunkZ = Math.floorDiv(machinePos.getZ(), CHUNK_SIZE);
        int checked = 0;

        for (int radius = 0; checked < this.chunkCount; radius++) {
            for (int dx = -radius; dx <= radius && checked < this.chunkCount; dx++) {
                for (int dz = -radius; dz <= radius && checked < this.chunkCount; dz++) {
                    if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    checked++;
                    BlockPos found = findNextTargetInChunk(level, machinePos, kind, machineChunkX + dx, machineChunkZ + dz);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private @Nullable BlockPos findNextTargetInChunk(ServerLevel level, BlockPos machinePos, MachineKind kind, int chunkX, int chunkZ) {
        int chunkMinX = chunkX * CHUNK_SIZE;
        int chunkMinZ = chunkZ * CHUNK_SIZE;
        int chunkMaxX = chunkMinX + CHUNK_SIZE - 1;
        int chunkMaxZ = chunkMinZ + CHUNK_SIZE - 1;

        if (kind == MachineKind.SHOVEL) {
            if (!this.shovelWholeChunkMode) {
                int y = machinePos.getY();
                for (int x = chunkMinX; x <= chunkMaxX; x++) {
                    for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                        BlockPos checkPos = new BlockPos(x, y, z);
                        if (checkPos.equals(machinePos)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(checkPos);
                        if (matchesTarget(state, kind)) {
                            return checkPos;
                        }
                    }
                }
                return null;
            }

            // 整区块模式：扫描设定区块里的所有 Y 层，只处理泥土/草方块类目标。
            int minY = level.getMinY();
            int maxY = level.getMaxY() - 1;
            for (int y = maxY; y >= minY; y--) {
                for (int x = chunkMinX; x <= chunkMaxX; x++) {
                    for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                        BlockPos checkPos = new BlockPos(x, y, z);
                        if (checkPos.equals(machinePos)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(checkPos);
                        if (matchesTarget(state, kind)) {
                            return checkPos;
                        }
                    }
                }
            }
            return null;
        }

        // 挖矿机/挖石机：从机器脚下高度往世界底部扫描，不跨出设定区块数量。
        int minY = level.getMinY();
        for (int y = machinePos.getY() - 1; y >= minY; y--) {
            for (int x = chunkMinX; x <= chunkMaxX; x++) {
                for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(checkPos);
                    if (matchesTarget(state, kind)) {
                        return checkPos;
                    }
                }
            }
        }
        return null;
    }

    private boolean matchesTarget(BlockState state, MachineKind kind) {
        if (state.isAir()) {
            return false;
        }

        boolean hasFilter = false;
        for (int slot = FILTER_FIRST_SLOT; slot <= FILTER_LAST_SLOT; slot++) {
            ItemStack filter = this.items.get(slot);
            if (filter.isEmpty()) {
                continue;
            }

            // 方块过滤：放煤矿石/钻石矿石/石头/泥土这类方块时，只挖对应方块。
            if (filter.getItem() instanceof BlockItem blockItem) {
                BlockState filterState = blockItem.getBlock().defaultBlockState();
                if (!kind.isValidFilterBlock(filterState)) {
                    continue;
                }
                hasFilter = true;
                if (state.is(blockItem.getBlock()) && kind.isValidFilterBlock(state)) {
                    return true;
                }
                continue;
            }

            // 挖矿机额外支持掉落物过滤：粗铁/粗金/粗铜/煤炭/钻石/绿宝石/红石粉/青金石。
            if (kind == MachineKind.ORE && MachineKind.isOreDropFilterItem(filter)) {
                hasFilter = true;
                if (MachineKind.matchesOreDropFilter(filter, state)) {
                    return true;
                }
            }
        }

        // 过滤槽全部为空时，根据机器类型使用默认目标。
        return !hasFilter && kind.isDefaultTarget(state);
    }

    public boolean canUseAsFilter(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        MachineKind kind = getKind();
        if (stack.getItem() instanceof BlockItem blockItem) {
            return kind.isValidFilterBlock(blockItem.getBlock().defaultBlockState());
        }

        // 只有挖矿机允许使用矿物掉落物当过滤物品。
        return kind == MachineKind.ORE && MachineKind.isOreDropFilterItem(stack);
    }

    public static boolean isAnyMachineFilterItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (MachineKind.isOreDropFilterItem(stack)) {
            return true;
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        return MachineKind.ORE.isValidFilterBlock(state)
                || MachineKind.STONE.isValidFilterBlock(state)
                || MachineKind.SHOVEL.isValidFilterBlock(state);
    }

    private boolean mineTargetToContainer(ServerLevel level, BlockPos targetPos, Container output, MachineKind kind) {
        BlockState targetState = level.getBlockState(targetPos);
        BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
        ItemStack tool = kind == MachineKind.SHOVEL ? new ItemStack(Items.DIAMOND_SHOVEL) : new ItemStack(Items.DIAMOND_PICKAXE);
        List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                targetState,
                level,
                targetPos,
                targetBlockEntity,
                null,
                tool
        );

        if (drops.isEmpty()) {
            level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
            return true;
        }

        if (!canInsertAll(output, drops)) {
            return false;
        }

        for (ItemStack drop : drops) {
            insertItem(output, drop.copy());
        }

        level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
        output.setChanged();
        return true;
    }

    private boolean canInsertAll(Container container, List<ItemStack> stacks) {
        List<ItemStack> simulated = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            simulated.add(container.getItem(i).copy());
        }

        for (ItemStack original : stacks) {
            ItemStack stack = original.copy();
            for (int i = 0; i < simulated.size() && !stack.isEmpty(); i++) {
                ItemStack target = simulated.get(i);
                if (!target.isEmpty() && ItemStack.isSameItemSameComponents(target, stack)) {
                    int max = Math.min(container.getMaxStackSize(), target.getMaxStackSize());
                    int move = Math.min(stack.getCount(), max - target.getCount());
                    if (move > 0) {
                        target.grow(move);
                        stack.shrink(move);
                    }
                }
            }
            for (int i = 0; i < simulated.size() && !stack.isEmpty(); i++) {
                ItemStack target = simulated.get(i);
                if (target.isEmpty() && container.canPlaceItem(i, stack)) {
                    int max = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
                    ItemStack placed = stack.copyWithCount(Math.min(max, stack.getCount()));
                    simulated.set(i, placed);
                    stack.shrink(placed.getCount());
                }
            }
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ItemStack insertItem(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack target = container.getItem(i);
            if (!target.isEmpty() && ItemStack.isSameItemSameComponents(target, stack)) {
                int max = Math.min(container.getMaxStackSize(), target.getMaxStackSize());
                int move = Math.min(stack.getCount(), max - target.getCount());
                if (move > 0) {
                    target.grow(move);
                    stack.shrink(move);
                    container.setChanged();
                }
            }
        }

        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack target = container.getItem(i);
            if (target.isEmpty() && container.canPlaceItem(i, stack)) {
                int count = Math.min(container.getMaxStackSize(), stack.getCount());
                container.setItem(i, stack.copyWithCount(count));
                stack.shrink(count);
                container.setChanged();
            }
        }
        return stack;
    }

    private void notifyLastOperator(ServerLevel level, Component message) {
        if (this.lastOperator == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.lastOperator);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    private void messageWithCooldown(ServerLevel level, MessageType type, Component message) {
        long now = level.getGameTime();
        switch (type) {
            case NO_CHEST -> {
                if (now - lastNoChestMessageTick >= MESSAGE_COOLDOWN_TICKS) {
                    lastNoChestMessageTick = now;
                    notifyLastOperator(level, message);
                }
            }
            case CHEST_FULL -> {
                if (now - lastChestFullMessageTick >= MESSAGE_COOLDOWN_TICKS) {
                    lastChestFullMessageTick = now;
                    notifyLastOperator(level, message);
                }
            }
            case NO_TARGET -> {
                if (now - lastNoTargetMessageTick >= MESSAGE_COOLDOWN_TICKS) {
                    lastNoTargetMessageTick = now;
                    notifyLastOperator(level, message);
                }
            }
        }
    }

    private MachineKind getKind() {
        Block block = getBlockState().getBlock();
        if (block == DouMiningMachine.STONE_MINING_MACHINE_BLOCK) {
            return MachineKind.STONE;
        }
        if (block == DouMiningMachine.SHOVEL_MACHINE_BLOCK) {
            return MachineKind.SHOVEL;
        }
        return MachineKind.ORE;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        ContainerHelper.saveAllItems(output, this.items);
        output.putBoolean("Active", this.active);
        output.putInt("FuelTicks", this.fuelTicks);
        output.putInt("MiningProgress", this.miningProgress);
        output.putInt("ChunkCount", this.chunkCount);
        output.putInt("SpeedLevel", this.speedLevel);
        output.putBoolean("ShovelWholeChunkMode", this.shovelWholeChunkMode);
        super.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        this.active = input.getBooleanOr("Active", false);
        this.fuelTicks = input.getIntOr("FuelTicks", 0);
        this.miningProgress = input.getIntOr("MiningProgress", 0);
        this.chunkCount = clampChunkCount(input.getIntOr("ChunkCount", MIN_CHUNK_COUNT));
        this.speedLevel = clampSpeedLevel(input.getIntOr("SpeedLevel", MIN_SPEED_LEVEL));
        this.shovelWholeChunkMode = input.getBooleanOr("ShovelWholeChunkMode", false);
        this.lastOperator = null;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(getKind().displayName);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new MiningMachineMenu(syncId, playerInventory, this, this.data);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= FILTER_FIRST_SLOT && slot <= FILTER_LAST_SLOT && !stack.isEmpty()) {
            stack = stack.copyWithCount(1);
        }
        this.items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D,
                this.worldPosition.getZ() + 0.5D
        ) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == FUEL_SLOT) {
            return stack.is(Items.COAL);
        }
        if (slot >= FILTER_FIRST_SLOT && slot <= FILTER_LAST_SLOT) {
            return canUseAsFilter(stack);
        }
        return false;
    }

    @Override
    public void clearContent() {
        this.items.clear();
        setChanged();
    }

    private enum MachineKind {
        ORE("挖矿机"),
        STONE("挖石机"),
        SHOVEL("铲土机");

        private final String displayName;

        MachineKind(String displayName) {
            this.displayName = displayName;
        }

        private boolean isDefaultTarget(BlockState state) {
            return switch (this) {
                case ORE -> isOreBlock(state);
                case STONE -> isStoneLikeBlock(state);
                case SHOVEL -> isDirtLikeBlock(state);
            };
        }

        private boolean isValidFilterBlock(BlockState state) {
            return switch (this) {
                case ORE -> isOreBlock(state);
                case STONE -> isStoneLikeBlock(state);
                case SHOVEL -> isDirtLikeBlock(state);
            };
        }

        private static boolean isOreBlock(BlockState state) {
            if (state.is(ORES_TAG)) {
                return true;
            }
            String path = pathOf(state);
            return path.endsWith("_ore") || path.endsWith("_ores") || path.equals("ancient_debris") || path.endsWith("ancient_debris");
        }

        private static boolean isOreDropFilterItem(ItemStack stack) {
            return stack.is(Items.RAW_IRON)
                    || stack.is(Items.RAW_GOLD)
                    || stack.is(Items.RAW_COPPER)
                    || stack.is(Items.COAL)
                    || stack.is(Items.DIAMOND)
                    || stack.is(Items.EMERALD)
                    || stack.is(Items.REDSTONE)
                    || stack.is(Items.LAPIS_LAZULI);
        }

        private static boolean matchesOreDropFilter(ItemStack filter, BlockState state) {
            if (!isOreBlock(state)) {
                return false;
            }

            String path = pathOf(state);
            if (filter.is(Items.RAW_IRON)) {
                return path.equals("iron_ore") || path.equals("deepslate_iron_ore");
            }
            if (filter.is(Items.RAW_GOLD)) {
                return path.equals("gold_ore") || path.equals("deepslate_gold_ore");
            }
            if (filter.is(Items.RAW_COPPER)) {
                return path.equals("copper_ore") || path.equals("deepslate_copper_ore");
            }
            if (filter.is(Items.COAL)) {
                return path.equals("coal_ore") || path.equals("deepslate_coal_ore");
            }
            if (filter.is(Items.DIAMOND)) {
                return path.equals("diamond_ore") || path.equals("deepslate_diamond_ore");
            }
            if (filter.is(Items.EMERALD)) {
                return path.equals("emerald_ore") || path.equals("deepslate_emerald_ore");
            }
            if (filter.is(Items.REDSTONE)) {
                return path.equals("redstone_ore") || path.equals("deepslate_redstone_ore");
            }
            if (filter.is(Items.LAPIS_LAZULI)) {
                return path.equals("lapis_ore") || path.equals("deepslate_lapis_ore");
            }
            return false;
        }

        private static boolean isStoneLikeBlock(BlockState state) {
            String path = pathOf(state);
            return path.equals("stone")
                    || path.equals("cobblestone")
                    || path.equals("deepslate")
                    || path.equals("cobbled_deepslate")
                    || path.equals("granite")
                    || path.equals("diorite")
                    || path.equals("andesite")
                    || path.equals("tuff")
                    || path.equals("calcite")
                    || path.equals("dripstone_block")
                    || path.equals("smooth_basalt")
                    || path.equals("basalt")
                    || path.equals("blackstone")
                    || path.equals("netherrack")
                    || path.equals("end_stone");
        }

        private static boolean isDirtLikeBlock(BlockState state) {
            String path = pathOf(state);
            return path.equals("dirt")
                    || path.equals("grass_block")
                    || path.equals("coarse_dirt")
                    || path.equals("rooted_dirt")
                    || path.equals("podzol")
                    || path.equals("mycelium")
                    || path.equals("farmland")
                    || path.equals("dirt_path");
        }

        private static String pathOf(BlockState state) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return id.getPath();
        }
    }

    private enum MessageType {
        NO_CHEST,
        CHEST_FULL,
        NO_TARGET
    }
}
