package com.majorbonghits.moderncompanions.item;

import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import com.majorbonghits.moderncompanions.squad.CompanionSquadData;
import com.majorbonghits.moderncompanions.squad.Squad;
import com.majorbonghits.moderncompanions.squad.SquadOrder;
import com.majorbonghits.moderncompanions.squad.SquadService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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

/**
 * In-world squad director. Ordering a squad somewhere should not require opening
 * a screen, so every command lives on this one item.
 *
 * <ul>
 *   <li>Right-click ground: send the active squad there and hold.</li>
 *   <li>Right-click a companion: move it into the active squad.</li>
 *   <li>Sneak + right-click a companion: remove it from its squad.</li>
 *   <li>Right-click air: cycle which squad is active.</li>
 *   <li>Sneak + right-click air: cycle the active squad's standing order.</li>
 *   <li>Sneak + right-click ground: post the active squad to guard that spot.</li>
 * </ul>
 */
public class CommandBatonItem extends Item {
    private static final String TAG_ACTIVE_SLOT = "ActiveSquadSlot";
    private static final int DEFAULT_WARD_RADIUS = 12;

    public CommandBatonItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    /* ---------- Companion assignment ---------- */

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof AbstractHumanCompanionEntity companion)) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        if (!companion.isOwnedBy(player)) {
            serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.squad.not_owner"));
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown()) {
            SquadService.unassign(serverPlayer, companion);
            serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.squad.removed",
                    companion.getDisplayName()));
            return InteractionResult.CONSUME;
        }

        int slot = activeSlot(stack);
        Squad squad = SquadService.assign(serverPlayer, companion, slot);
        serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.squad.assigned",
                companion.getDisplayName(), SquadService.describe(squad)));
        return InteractionResult.CONSUME;
    }

    /* ---------- Destination and post orders ---------- */

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        Squad squad = activeSquad(serverPlayer, stack);
        if (squad.size() == 0) {
            serverPlayer.sendSystemMessage(Component.translatable("message.modern_companions.squad.empty",
                    SquadService.describe(squad)));
            return InteractionResult.CONSUME;
        }

        BlockPos destination = context.getClickedPos().relative(context.getClickedFace());
        SquadOrder order = player.isShiftKeyDown()
                ? SquadOrder.ward(level.dimension(), destination, DEFAULT_WARD_RADIUS)
                : SquadOrder.moveTo(level.dimension(), destination);

        int applied = SquadService.issue(serverPlayer, squad, order);
        markDestination(level, destination);
        serverPlayer.sendSystemMessage(Component.translatable(
                player.isShiftKeyDown() ? "message.modern_companions.squad.warding" : "message.modern_companions.squad.moving",
                SquadService.describe(squad), applied,
                Component.literal(destination.getX() + ", " + destination.getY() + ", " + destination.getZ())));
        return InteractionResult.CONSUME;
    }

    /* ---------- Squad selection and standing orders ---------- */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        if (player.isShiftKeyDown()) {
            cycleStandingOrder(serverPlayer, stack);
        } else {
            cycleActiveSquad(serverPlayer, stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** Steps to the next squad slot, so switching who you are commanding is one click. */
    private void cycleActiveSquad(ServerPlayer player, ItemStack stack) {
        int next = activeSlot(stack) % Squad.MAX_SQUADS + 1;
        setActiveSlot(stack, next);
        Squad squad = CompanionSquadData.get(player.server).getOrCreate(player.getUUID(), next);
        player.sendSystemMessage(Component.translatable("message.modern_companions.squad.selected",
                SquadService.describe(squad), squad.size()));
    }

    /**
     * Rotates the active squad through the postures that need no destination, so
     * recalling or halting a squad mid-fight takes one click and no screen.
     */
    private void cycleStandingOrder(ServerPlayer player, ItemStack stack) {
        Squad squad = activeSquad(player, stack);
        SquadOrder next = switch (squad.order().type()) {
            case FOLLOW -> SquadOrder.hold();
            case HOLD -> SquadOrder.work();
            default -> SquadOrder.follow();
        };
        int applied = SquadService.issue(player, squad, next);
        player.sendSystemMessage(Component.translatable("message.modern_companions.squad.order",
                SquadService.describe(squad), Component.translatable(next.type().translationKey()), applied));
    }

    /* ---------- Helpers ---------- */

    private Squad activeSquad(ServerPlayer player, ItemStack stack) {
        return CompanionSquadData.get(player.server).getOrCreate(player.getUUID(), activeSlot(stack));
    }

    private static int activeSlot(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        int slot = data.copyTag().getInt(TAG_ACTIVE_SLOT);
        return slot < 1 || slot > Squad.MAX_SQUADS ? CompanionSquadData.DEFAULT_SLOT : slot;
    }

    private static void setActiveSlot(ItemStack stack, int slot) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_ACTIVE_SLOT, slot));
    }

    /** Visible marker so the player can see where the squad was actually sent. */
    private static void markDestination(ServerLevel level, BlockPos pos) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 12, 0.4D, 0.6D, 0.4D, 0.0D);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.modern_companions.command_baton.slot", activeSlot(stack))
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.modern_companions.command_baton.move")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.modern_companions.command_baton.ward")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.modern_companions.command_baton.assign")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.modern_companions.command_baton.cycle")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
