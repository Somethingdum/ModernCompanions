package com.majorbonghits.moderncompanions.entity;

import com.majorbonghits.moderncompanions.ModernCompanions;
import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.core.ModEntityTypes;
import com.majorbonghits.moderncompanions.core.ModItems;
import com.majorbonghits.moderncompanions.core.StarterCompanionGear;
import com.majorbonghits.moderncompanions.squad.CompanionSquadData;
import com.majorbonghits.moderncompanions.squad.CompanionStance;
import com.majorbonghits.moderncompanions.squad.SquadService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Grants one well-equipped companion the first time a player joins a world.
 *
 * <p>The point is to make hour zero playable rather than to hand out power: the
 * starter is a Vanguard because it is the only archetype that already implements
 * shield use, taunting, and projectile soak, so it is the best survivability
 * pick without adding a single point of damage. Its gear deliberately bypasses
 * the normal spawn-armour roll, which produces nothing at all forty percent of
 * the time.
 */
@EventBusSubscriber(modid = ModernCompanions.MOD_ID)
public final class StarterCompanionEvents {
    private static final String GRANTED_TAG = "modern_companions_starter_granted";
    private static final int SPAWN_SEARCH_ATTEMPTS = 16;
    private static final int SPAWN_SEARCH_RADIUS = 3;

    private StarterCompanionEvents() {}

    @SubscribeEvent
    public static void onFirstJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ModConfig.safeGet(ModConfig.STARTER_ENABLED)) return;
        if (hasBeenGranted(player)) return;

        markGranted(player);
        grant(player, false);
    }

    public static boolean hasBeenGranted(Player player) {
        return player.getPersistentData().getBoolean(GRANTED_TAG);
    }

    public static void markGranted(Player player) {
        player.getPersistentData().putBoolean(GRANTED_TAG, true);
    }

    /**
     * Spawns and fully outfits the starter companion.
     *
     * @param announce whether to explain the companion's tools in chat
     * @return the companion, or empty if no safe spawn spot was found
     */
    public static Optional<AbstractHumanCompanionEntity> grant(ServerPlayer player, boolean announce) {
        if (!(player.level() instanceof ServerLevel level)) return Optional.empty();

        AbstractHumanCompanionEntity companion = ModEntityTypes.VANGUARD.get().create(level);
        if (companion == null) return Optional.empty();

        BlockPos spawn = findSafeSpawn(level, player);
        companion.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, player.getYRot(), 0.0F);
        companion.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null);

        equip(companion, ModConfig.safeGet(ModConfig.STARTER_GEAR));

        companion.tame(player);
        companion.setFirstTamedGameTime(level.getGameTime());
        companion.setStance(CompanionStance.ESCORT);
        companion.setAlert(true);
        companion.setHealth(companion.getMaxHealth());
        level.addFreshEntity(companion);

        // Slot 1 exists from the first recruit so squad orders work immediately.
        CompanionSquadData.get(player.server).getOrCreate(player.getUUID(), CompanionSquadData.DEFAULT_SLOT);
        SquadService.assign(player, companion, CompanionSquadData.DEFAULT_SLOT);

        if (ModConfig.safeGet(ModConfig.STARTER_GRANT_TOOLS)) {
            giveTool(player, new ItemStack(ModItems.COMMAND_BATON.get()));
            giveTool(player, new ItemStack(ModItems.PERIMETER_ROD.get()));
        }

        player.sendSystemMessage(Component.translatable("message.modern_companions.starter.granted",
                companion.getDisplayName()));
        if (announce || ModConfig.safeGet(ModConfig.STARTER_GRANT_TOOLS)) {
            player.sendSystemMessage(Component.translatable("message.modern_companions.starter.tools"));
        }
        return Optional.of(companion);
    }

    /** Guaranteed kit; the normal spawn roll yields nothing about 40% of the time. */
    private static void equip(AbstractHumanCompanionEntity companion, StarterCompanionGear gear) {
        switch (gear) {
            case IRON -> {
                companion.setManualEquipment(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                companion.setManualEquipment(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                companion.setManualEquipment(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                companion.setManualEquipment(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                companion.setManualEquipment(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                companion.setManualEquipment(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
            case LEATHER -> {
                companion.setManualEquipment(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                companion.setManualEquipment(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                companion.setManualEquipment(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
                companion.setManualEquipment(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                companion.setManualEquipment(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
            }
            case NONE -> {
                // Deliberately empty; the companion arrives with whatever it spawned with.
            }
        }
    }

    private static void giveTool(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Finds standing room near the player. Shared shape with the Summoning Wand's
     * search, kept local so a failed search returns the player's own position
     * rather than nothing at all.
     */
    private static BlockPos findSafeSpawn(ServerLevel level, ServerPlayer player) {
        BlockPos base = player.blockPosition();
        for (int attempt = 0; attempt < SPAWN_SEARCH_ATTEMPTS; attempt++) {
            int dx = level.random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS;
            int dz = level.random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS;
            BlockPos candidate = base.offset(dx, 0, dz);
            if (isStandable(level, candidate)) return candidate;
        }
        return base;
    }

    private static boolean isStandable(ServerLevel level, @Nullable BlockPos pos) {
        return pos != null && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())
                && !level.isEmptyBlock(pos.below());
    }
}
