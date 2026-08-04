package com.majorbonghits.moderncompanions.item;

import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import com.majorbonghits.moderncompanions.squad.Squad;
import com.majorbonghits.moderncompanions.squad.SquadOrder;
import com.majorbonghits.moderncompanions.squad.SquadService;
import com.majorbonghits.moderncompanions.world.zone.PerimeterZone;
import com.majorbonghits.moderncompanions.world.zone.PerimeterZoneData;
import com.majorbonghits.moderncompanions.world.zone.ZoneMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * Stick-like tool that defines a base perimeter from two clicked corners.
 *
 * <p>Click one corner, then another, and the enclosed region becomes a zone.
 * The zone's vertical extent is expanded past the clicked blocks so it covers
 * tunnelling from below and flying in from above, which a surface-only box
 * would not. Right-clicking a companion posts its whole squad to the selected
 * zone.
 */
public class PerimeterRodItem extends Item {
    private static final String TAG_CORNER = "PendingCorner";
    private static final String TAG_SELECTED_ZONE = "SelectedZone";

    public PerimeterRodItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    /* ---------- Corner selection ---------- */

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();

        // Sneaking always restarts, so a mis-placed first corner is one click to fix.
        if (player.isShiftKeyDown()) {
            setPendingCorner(stack, clicked);
            markCorner(level, clicked);
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.modern_companions.zone.corner_first", describe(clicked)));
            return InteractionResult.CONSUME;
        }

        BlockPos pending = pendingCorner(stack);
        if (pending == null) {
            setPendingCorner(stack, clicked);
            markCorner(level, clicked);
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.modern_companions.zone.corner_first", describe(clicked)));
            return InteractionResult.CONSUME;
        }

        return completeZone(serverPlayer, level, stack, pending, clicked);
    }

    private InteractionResult completeZone(ServerPlayer player, ServerLevel level, ItemStack stack,
                                           BlockPos cornerA, BlockPos cornerB) {
        PerimeterZoneData data = PerimeterZoneData.get(level);
        List<PerimeterZone> owned = data.zonesOf(player.getUUID());
        if (owned.size() >= PerimeterZoneData.MAX_ZONES_PER_PLAYER) {
            player.sendSystemMessage(Component.translatable("message.modern_companions.zone.too_many",
                    PerimeterZoneData.MAX_ZONES_PER_PLAYER));
            clearPendingCorner(stack);
            return InteractionResult.CONSUME;
        }

        int minY = ZoneMath.lowerBound(cornerA.getY(), cornerB.getY(),
                ModConfig.safeGet(ModConfig.ZONE_DEPTH_BELOW), level.getMinBuildHeight());
        int maxY = ZoneMath.upperBound(cornerA.getY(), cornerB.getY(),
                ModConfig.safeGet(ModConfig.ZONE_HEIGHT_ABOVE), level.getMaxBuildHeight());

        PerimeterZone zone = new PerimeterZone(UUID.randomUUID(), player.getUUID(),
                defaultName(owned.size() + 1), cornerA, cornerB, minY, maxY);

        if (zone.volume() > PerimeterZoneData.MAX_ZONE_VOLUME) {
            player.sendSystemMessage(Component.translatable("message.modern_companions.zone.too_large"));
            clearPendingCorner(stack);
            return InteractionResult.CONSUME;
        }

        data.add(zone);
        clearPendingCorner(stack);
        setSelectedZone(stack, zone.id());
        outlineZone(level, zone);
        player.sendSystemMessage(Component.translatable("message.modern_companions.zone.created",
                Component.literal(zone.name()).withStyle(ChatFormatting.AQUA),
                (zone.maxX() - zone.minX() + 1) + "x" + (zone.maxZ() - zone.minZ() + 1),
                zone.minY(), zone.maxY()));
        return InteractionResult.CONSUME;
    }

    /* ---------- Posting a squad to the selected zone ---------- */

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof AbstractHumanCompanionEntity companion)) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        if (!companion.isOwnedBy(player)) {
            serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.squad.not_owner"));
            return InteractionResult.CONSUME;
        }

        PerimeterZone zone = selectedZone(level, stack, serverPlayer);
        if (zone == null) {
            serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.zone.none_selected"));
            return InteractionResult.CONSUME;
        }

        Squad squad = SquadService.squadOf(serverPlayer, companion);
        if (squad == null) {
            // A companion with no squad still gets posted, just on its own.
            companion.setStance(com.majorbonghits.moderncompanions.squad.CompanionStance.WARD);
            companion.setPatrolPos(zone.center());
            companion.setPatrolRadius(wardRadius(zone));
            serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.zone.posted_one",
                    companion.getDisplayName(), Component.literal(zone.name())));
            return InteractionResult.CONSUME;
        }

        int applied = SquadService.issue(serverPlayer, squad,
                SquadOrder.ward(level.dimension(), zone.center(), wardRadius(zone)));
        distributeSentryPosts(serverPlayer, squad, zone);
        outlineZone(level, zone);
        serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.zone.posted_squad",
                SquadService.describe(squad), applied, Component.literal(zone.name())));
        return InteractionResult.CONSUME;
    }

    /**
     * Spreads squad members around the boundary instead of stacking them on the
     * centre. Coverage of a perimeter is what actually stops something walking in,
     * and several guards on one corner cover no more ground than one.
     */
    private void distributeSentryPosts(ServerPlayer owner, Squad squad, PerimeterZone zone) {
        List<BlockPos> posts = zone.boundarySamples();
        if (posts.isEmpty()) return;
        List<AbstractHumanCompanionEntity> members = SquadService.loadedMembers(owner, squad);
        for (int i = 0; i < members.size(); i++) {
            // Evenly spaced indices keep posts as far apart as the roster allows.
            BlockPos post = posts.get((int) Math.round(ZoneMath.perimeterFraction(i, Math.max(1, members.size()))
                    * (posts.size() - 1)));
            members.get(i).setPatrolPos(post);
        }
    }

    private static int wardRadius(PerimeterZone zone) {
        int halfWidth = (zone.maxX() - zone.minX()) / 2;
        int halfDepth = (zone.maxZ() - zone.minZ()) / 2;
        return Math.max(4, Math.max(halfWidth, halfDepth) + 4);
    }

    /* ---------- Zone selection ---------- */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (player.isShiftKeyDown()) {
            clearPendingCorner(stack);
            serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.zone.cleared_pending"));
            return InteractionResultHolder.consume(stack);
        }

        List<PerimeterZone> zones = PerimeterZoneData.get(serverLevel).zonesOf(serverPlayer.getUUID());
        if (zones.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.zone.none"));
            return InteractionResultHolder.consume(stack);
        }

        UUID current = selectedZoneId(stack);
        int index = 0;
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).id().equals(current)) {
                index = (i + 1) % zones.size();
                break;
            }
        }
        PerimeterZone zone = zones.get(index);
        setSelectedZone(stack, zone.id());
        outlineZone(serverLevel, zone);
        serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.zone.selected",
                Component.literal(zone.name()).withStyle(ChatFormatting.AQUA)));
        return InteractionResultHolder.consume(stack);
    }

    /* ---------- Feedback ---------- */

    /** Traces the zone edge so the player can see the boundary they just drew. */
    private static void outlineZone(ServerLevel level, PerimeterZone zone) {
        int y = (zone.minY() + zone.maxY()) / 2;
        for (BlockPos sample : zone.boundarySamples()) {
            level.sendParticles(ParticleTypes.END_ROD,
                    sample.getX() + 0.5D, y + 1.0D, sample.getZ() + 0.5D, 3, 0.1D, 0.6D, 0.1D, 0.0D);
        }
        level.playSound(null, zone.center(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4F, 1.6F);
    }

    private static void markCorner(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.WAX_ON,
                pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 10, 0.3D, 0.4D, 0.3D, 0.0D);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    private static String describe(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String defaultName(int index) {
        return "Base " + index;
    }

    /* ---------- Stack state ---------- */

    private static BlockPos pendingCorner(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        var tag = data.copyTag();
        return tag.contains(TAG_CORNER) ? BlockPos.of(tag.getLong(TAG_CORNER)) : null;
    }

    private static void setPendingCorner(ItemStack stack, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(TAG_CORNER, pos.asLong()));
    }

    private static void clearPendingCorner(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(TAG_CORNER));
    }

    private static UUID selectedZoneId(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        var tag = data.copyTag();
        return tag.hasUUID(TAG_SELECTED_ZONE) ? tag.getUUID(TAG_SELECTED_ZONE) : null;
    }

    private static void setSelectedZone(ItemStack stack, UUID zoneId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(TAG_SELECTED_ZONE, zoneId));
    }

    /** Selected zone, falling back to the player's only zone when just one exists. */
    private static PerimeterZone selectedZone(ServerLevel level, ItemStack stack, ServerPlayer player) {
        PerimeterZoneData data = PerimeterZoneData.get(level);
        PerimeterZone zone = data.byId(selectedZoneId(stack));
        if (zone != null && zone.owner().equals(player.getUUID())) return zone;

        List<PerimeterZone> owned = data.zonesOf(player.getUUID());
        return owned.size() == 1 ? owned.get(0) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlockPos pending = pendingCorner(stack);
        if (pending != null) {
            tooltip.add(Component.translatable("tooltip.modern_companions.perimeter_rod.pending", describe(pending))
                    .withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable("tooltip.modern_companions.perimeter_rod.corners")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.modern_companions.perimeter_rod.post")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.modern_companions.perimeter_rod.cycle")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
