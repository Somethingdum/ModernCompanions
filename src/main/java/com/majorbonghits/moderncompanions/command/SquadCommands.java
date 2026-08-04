package com.majorbonghits.moderncompanions.command;

import com.majorbonghits.moderncompanions.ModernCompanions;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import com.majorbonghits.moderncompanions.squad.CompanionSquadData;
import com.majorbonghits.moderncompanions.squad.Squad;
import com.majorbonghits.moderncompanions.squad.SquadOrder;
import com.majorbonghits.moderncompanions.squad.SquadService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * Keyboard control for squads, complementing the Command Baton.
 *
 * <p>The baton is faster in the field, but commands make bulk work practical:
 * moving every nearby companion into a squad at once, or ordering several squads
 * without hunting each one down. All of it is self-service, so no permission
 * level is required beyond being a player with companions of your own.
 */
@EventBusSubscriber(modid = ModernCompanions.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class SquadCommands {
    private SquadCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(root("squad"));
        // Short alias; typing this mid-fight matters more than it looks.
        event.getDispatcher().register(root("sq"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("add")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, Squad.MAX_SQUADS))
                                .executes(ctx -> addNearby(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, Squad.MAX_SQUADS))
                                .executes(ctx -> clear(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                .then(Commands.literal("name")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, Squad.MAX_SQUADS))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> rename(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "slot"),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("follow")
                        .then(slotArg((source, slot) -> order(source, slot, SquadOrder.follow()))))
                .then(Commands.literal("hold")
                        .then(slotArg((source, slot) -> order(source, slot, SquadOrder.hold()))))
                .then(Commands.literal("work")
                        .then(slotArg((source, slot) -> order(source, slot, SquadOrder.work()))))
                .then(Commands.literal("moveto")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, Squad.MAX_SQUADS))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> moveTo(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "slot"),
                                                BlockPosArgument.getSpawnablePos(ctx, "pos"))))))
                .then(Commands.literal("ward")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, Squad.MAX_SQUADS))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                                .executes(ctx -> ward(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "slot"),
                                                        BlockPosArgument.getSpawnablePos(ctx, "pos"),
                                                        IntegerArgumentType.getInteger(ctx, "radius")))))))
                // Bulk verbs: every squad at once, for when things go wrong.
                .then(Commands.literal("allfollow").executes(ctx -> orderAll(ctx.getSource(), SquadOrder.follow())))
                .then(Commands.literal("allhold").executes(ctx -> orderAll(ctx.getSource(), SquadOrder.hold())));
    }

    private interface SlotAction {
        int run(CommandSourceStack source, int slot);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> slotArg(SlotAction action) {
        return Commands.argument("slot", IntegerArgumentType.integer(1, Squad.MAX_SQUADS))
                .executes(ctx -> action.run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")));
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = player(source);
        List<Squad> squads = CompanionSquadData.get(player.server).squadsOf(player.getUUID());
        if (squads.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.modern_companions.squad.none"), false);
            return 0;
        }
        for (Squad squad : squads) {
            source.sendSuccess(() -> Component.translatable("message.modern_companions.squad.list_entry",
                    SquadService.describe(squad), squad.size(),
                    Component.translatable(squad.order().type().translationKey())), false);
        }
        return squads.size();
    }

    /** Sweeps every owned companion loaded near the player into one squad. */
    private static int addNearby(CommandSourceStack source, int slot) {
        ServerPlayer player = player(source);
        List<AbstractHumanCompanionEntity> nearby = SquadService.nearbyOwned(player);
        for (AbstractHumanCompanionEntity companion : nearby) {
            SquadService.assign(player, companion, slot);
        }
        Squad squad = CompanionSquadData.get(player.server).getOrCreate(player.getUUID(), slot);
        source.sendSuccess(() -> Component.translatable("message.modern_companions.squad.bulk_assigned",
                nearby.size(), SquadService.describe(squad)), false);
        return nearby.size();
    }

    private static int clear(CommandSourceStack source, int slot) {
        ServerPlayer player = player(source);
        CompanionSquadData data = CompanionSquadData.get(player.server);
        Squad squad = data.getOrCreate(player.getUUID(), slot);
        int removed = squad.size();
        for (java.util.UUID member : squad.members()) {
            squad.remove(member);
        }
        data.markChanged();
        source.sendSuccess(() -> Component.translatable("message.modern_companions.squad.cleared",
                SquadService.describe(squad), removed), false);
        return removed;
    }

    private static int rename(CommandSourceStack source, int slot, String name) {
        ServerPlayer player = player(source);
        CompanionSquadData data = CompanionSquadData.get(player.server);
        Squad squad = data.getOrCreate(player.getUUID(), slot);
        squad.setName(name);
        data.markChanged();
        source.sendSuccess(() -> Component.translatable("message.modern_companions.squad.renamed",
                SquadService.describe(squad)), false);
        return 1;
    }

    private static int order(CommandSourceStack source, int slot, SquadOrder order) {
        ServerPlayer player = player(source);
        Squad squad = CompanionSquadData.get(player.server).getOrCreate(player.getUUID(), slot);
        int applied = SquadService.issue(player, squad, order);
        source.sendSuccess(() -> Component.translatable("message.modern_companions.squad.order",
                SquadService.describe(squad), Component.translatable(order.type().translationKey()), applied), false);
        return applied;
    }

    private static int orderAll(CommandSourceStack source, SquadOrder order) {
        ServerPlayer player = player(source);
        int total = 0;
        for (Squad squad : CompanionSquadData.get(player.server).squadsOf(player.getUUID())) {
            total += SquadService.issue(player, squad, order);
        }
        int applied = total;
        source.sendSuccess(() -> Component.translatable("message.modern_companions.squad.order_all",
                Component.translatable(order.type().translationKey()), applied), false);
        return applied;
    }

    private static int moveTo(CommandSourceStack source, int slot, BlockPos pos) {
        ServerPlayer player = player(source);
        return order(source, slot, SquadOrder.moveTo(player.level().dimension(), pos));
    }

    private static int ward(CommandSourceStack source, int slot, BlockPos pos, int radius) {
        ServerPlayer player = player(source);
        return order(source, slot, SquadOrder.ward(player.level().dimension(), pos, radius));
    }

    private static ServerPlayer player(CommandSourceStack source) {
        return (ServerPlayer) source.getEntity();
    }
}
