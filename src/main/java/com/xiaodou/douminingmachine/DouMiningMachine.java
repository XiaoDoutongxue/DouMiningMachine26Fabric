package com.xiaodou.douminingmachine;

import com.xiaodou.douminingmachine.block.MiningMachineBlock;
import com.xiaodou.douminingmachine.block.entity.MiningMachineBlockEntity;
import com.xiaodou.douminingmachine.menu.MiningMachineMenu;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class DouMiningMachine implements ModInitializer {
    public static final String MOD_ID = "douminingmachine";

    public static final Identifier MINING_MACHINE_ID = id("mining_machine");
    public static final Identifier STONE_MINING_MACHINE_ID = id("stone_mining_machine");
    public static final Identifier SHOVEL_MACHINE_ID = id("shovel_machine");

    public static final ResourceKey<Block> MINING_MACHINE_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, MINING_MACHINE_ID);
    public static final ResourceKey<Block> STONE_MINING_MACHINE_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, STONE_MINING_MACHINE_ID);
    public static final ResourceKey<Block> SHOVEL_MACHINE_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, SHOVEL_MACHINE_ID);

    public static final ResourceKey<Item> MINING_MACHINE_ITEM_KEY = ResourceKey.create(Registries.ITEM, MINING_MACHINE_ID);
    public static final ResourceKey<Item> STONE_MINING_MACHINE_ITEM_KEY = ResourceKey.create(Registries.ITEM, STONE_MINING_MACHINE_ID);
    public static final ResourceKey<Item> SHOVEL_MACHINE_ITEM_KEY = ResourceKey.create(Registries.ITEM, SHOVEL_MACHINE_ID);

    public static final Block MINING_MACHINE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            MINING_MACHINE_BLOCK_KEY,
            new MiningMachineBlock(BlockBehaviour.Properties.of()
                    .setId(MINING_MACHINE_BLOCK_KEY)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops())
    );

    public static final Block STONE_MINING_MACHINE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            STONE_MINING_MACHINE_BLOCK_KEY,
            new MiningMachineBlock(BlockBehaviour.Properties.of()
                    .setId(STONE_MINING_MACHINE_BLOCK_KEY)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops())
    );

    public static final Block SHOVEL_MACHINE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            SHOVEL_MACHINE_BLOCK_KEY,
            new MiningMachineBlock(BlockBehaviour.Properties.of()
                    .setId(SHOVEL_MACHINE_BLOCK_KEY)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops())
    );

    public static final Item MINING_MACHINE_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            MINING_MACHINE_ITEM_KEY,
            new BlockItem(MINING_MACHINE_BLOCK, new Item.Properties().setId(MINING_MACHINE_ITEM_KEY).useBlockDescriptionPrefix())
    );

    public static final Item STONE_MINING_MACHINE_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            STONE_MINING_MACHINE_ITEM_KEY,
            new BlockItem(STONE_MINING_MACHINE_BLOCK, new Item.Properties().setId(STONE_MINING_MACHINE_ITEM_KEY).useBlockDescriptionPrefix())
    );

    public static final Item SHOVEL_MACHINE_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            SHOVEL_MACHINE_ITEM_KEY,
            new BlockItem(SHOVEL_MACHINE_BLOCK, new Item.Properties().setId(SHOVEL_MACHINE_ITEM_KEY).useBlockDescriptionPrefix())
    );

    public static final BlockEntityType<MiningMachineBlockEntity> MINING_MACHINE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            id("mining_machine"),
            FabricBlockEntityTypeBuilder.create(
                    MiningMachineBlockEntity::new,
                    MINING_MACHINE_BLOCK,
                    STONE_MINING_MACHINE_BLOCK,
                    SHOVEL_MACHINE_BLOCK
            ).build()
    );

    public static final MenuType<MiningMachineMenu> MINING_MACHINE_MENU = Registry.register(
            BuiltInRegistries.MENU,
            id("mining_machine"),
            new MenuType<>(MiningMachineMenu::new, FeatureFlags.DEFAULT_FLAGS)
    );

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(output -> {
                    output.accept(MINING_MACHINE_ITEM);
                    output.accept(STONE_MINING_MACHINE_ITEM);
                    output.accept(SHOVEL_MACHINE_ITEM);
                });

        System.out.println("[DouMiningMachine] 26.2 Fabric 挖矿机/挖石机/铲土机已加载！");
    }
}
