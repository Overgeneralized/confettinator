package dev.overgeneralized.confettinator;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.*;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class ConfettinatorMod implements ModInitializer {

    public static final String MOD_ID = "confettinator";
    public static final Block CONFETTINATOR = register("confettinator", ConfettinatorBlock::new, AbstractBlock.Settings.create().requiresTool().strength(4.0f));
    public static final BlockEntityType<ConfettinatorEntity> CONFETTINATOR_ENTITY = register("confettinator", FabricBlockEntityTypeBuilder.create(ConfettinatorEntity::new, CONFETTINATOR).build());
    public static final ScreenHandlerType<ConfettinatorScreenHandler> CONFETTINATOR_SCREEN_HANDLER = Registry.register(Registries.SCREEN_HANDLER, Identifier.of(MOD_ID, "confettinator"), new ScreenHandlerType<>(ConfettinatorScreenHandler::new, FeatureSet.empty()));
    public static final SimpleParticleType[] COLOR_CONFETTI_PAR_LIST = new SimpleParticleType[16];

    private static Block register(String name, Function<AbstractBlock.Settings, Block> blockFactory, AbstractBlock.Settings settings) {
        RegistryKey<Block> blockKey = keyOfBlock(name);
        Block block = blockFactory.apply(settings.registryKey(blockKey));

        RegistryKey<Item> itemKey = keyOfItem(name);
        BlockItem blockItem = new BlockItem(block, new Item.Settings().registryKey(itemKey));
        Registry.register(Registries.ITEM, itemKey, blockItem);

        return Registry.register(Registries.BLOCK, blockKey, block);
    }

    public static <T extends BlockEntityType<?>> T register(String path, T blockEntityType) {
        return Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, path), blockEntityType);
    }

    private static RegistryKey<Block> keyOfBlock(String name) {
        return RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MOD_ID, name));
    }

    private static RegistryKey<Item> keyOfItem(String name) {
        return RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, name));
    }

    @Override
    public void onInitialize() {
        for (int i = 0; i < 16; i++) {
            COLOR_CONFETTI_PAR_LIST[i] = FabricParticleTypes.simple();
            Registry.register(Registries.PARTICLE_TYPE, Identifier.of(MOD_ID, DyeColor.byIndex(i).getId()+"_confetti_par"), COLOR_CONFETTI_PAR_LIST[i]);
        }
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register((itemGroup) -> {
            itemGroup.add(CONFETTINATOR.asItem());
        });
    }
}
