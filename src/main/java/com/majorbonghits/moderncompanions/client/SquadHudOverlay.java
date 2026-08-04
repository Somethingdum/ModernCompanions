package com.majorbonghits.moderncompanions.client;

import com.majorbonghits.moderncompanions.ModernCompanions;
import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small corner readout of your squads: name, strength, and average health.
 *
 * <p>Deliberately built from entity data the client already has rather than a
 * dedicated roster packet. Companions sync their squad label, health, and stance
 * flags anyway, so grouping the loaded ones by label produces a live HUD with no
 * networking at all. The trade-off is that it only counts companions currently
 * loaded near you, which is also the only set whose health is meaningful.
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modid = ModernCompanions.MOD_ID, value = Dist.CLIENT)
public final class SquadHudOverlay {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ModernCompanions.MOD_ID, "squad_hud");
    private static final double SCAN_RADIUS = 128.0D;
    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;

    private SquadHudOverlay() {}

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ID, (LayeredDraw.Layer) SquadHudOverlay::render);
    }

    private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!ModConfig.safeGet(ModConfig.SQUAD_HUD_ENABLED)) return;
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui || mc.screen != null) return;
        if (mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen()) return;

        Map<String, SquadLine> squads = collect(mc);
        if (squads.isEmpty()) return;

        int y = MARGIN;
        for (SquadLine line : squads.values()) {
            graphics.drawString(mc.font, line.toComponent(), MARGIN, y, 0xFFFFFF, true);
            y += LINE_HEIGHT;
        }
    }

    /** Groups loaded, owned companions by their synced squad label. */
    private static Map<String, SquadLine> collect(Minecraft mc) {
        Map<String, SquadLine> squads = new LinkedHashMap<>();
        var box = mc.player.getBoundingBox().inflate(SCAN_RADIUS);
        for (AbstractHumanCompanionEntity companion : mc.level.getEntitiesOfClass(
                AbstractHumanCompanionEntity.class, box,
                c -> c.isAlive() && c.isTame() && mc.player.getUUID().equals(c.getOwnerUUID()))) {
            String label = companion.getSquadLabel();
            if (label == null || label.isBlank()) continue;
            squads.computeIfAbsent(label, SquadLine::new).add(companion);
        }
        return squads;
    }

    /** One squad's aggregated state. */
    private static final class SquadLine {
        private final String label;
        private int members;
        private float health;
        private float maxHealth;
        private int wounded;

        SquadLine(String label) {
            this.label = label;
        }

        void add(AbstractHumanCompanionEntity companion) {
            members++;
            health += companion.getHealth();
            maxHealth += companion.getMaxHealth();
            if (companion.getHealth() < companion.getMaxHealth() * 0.35F) wounded++;
        }

        Component toComponent() {
            int percent = maxHealth <= 0.0F ? 100 : Math.round(health / maxHealth * 100.0F);
            // Colour tracks the worst news: anyone badly hurt turns the line red.
            ChatFormatting colour = wounded > 0 ? ChatFormatting.RED
                    : percent < 70 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            return Component.literal(label + "  " + members + "  " + percent + "%").withStyle(colour);
        }
    }
}
