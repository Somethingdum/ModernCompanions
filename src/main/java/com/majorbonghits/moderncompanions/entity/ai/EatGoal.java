package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Lets companions consume edible items from their inventory when injured.
 * Deliberately registers no goal flags so eating can happen while walking or
 * withdrawing; the goal system's canContinueToUse drives cleanup instead of
 * the old self-called stop() pattern, which desynced the eating state.
 */
public class EatGoal extends Goal {
    protected final AbstractHumanCompanionEntity companion;
    private ItemStack food = ItemStack.EMPTY;
    private int useTicks;

    public EatGoal(AbstractHumanCompanionEntity entity) {
        this.companion = entity;
    }

    @Override
    public boolean canUse() {
        // The former LowHealthGoal owned this master toggle; it now gates the one
        // remaining eating goal so disabling self-feeding still works.
        if (!ModConfig.safeGet(ModConfig.LOW_HEALTH_FOOD)) return false;
        if (companion.getHealth() >= companion.getMaxHealth()) return false;
        food = companion.checkFood();
        return !food.isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        if (!ModConfig.safeGet(ModConfig.LOW_HEALTH_FOOD)) return false;
        if (companion.getHealth() >= companion.getMaxHealth()) return false;
        food = companion.checkFood();
        return !food.isEmpty();
    }

    @Override
    public void start() {
        companion.setTemporaryOffhandItem(food);
        companion.startUsingItem(InteractionHand.OFF_HAND);
        companion.setEating(true);
        useTicks = safeUseDuration(food);
        companion.swing(InteractionHand.OFF_HAND, true);
    }

    @Override
    public void stop() {
        companion.setTemporaryOffhandItem(ItemStack.EMPTY);
        companion.setEating(false);
        companion.stopUsingItem();
        useTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (useTicks > 0) {
            useTicks--;
            if (useTicks % 4 == 0) {
                companion.swing(InteractionHand.OFF_HAND, true);
            }
            return;
        }
        // One bite finished: heal, then either start the next bite or let
        // canContinueToUse end the goal on the next evaluation.
        if (companion.healFromFoodStack(food)) {
            food = companion.checkFood();
            if (!food.isEmpty() && companion.getHealth() < companion.getMaxHealth()) {
                companion.setTemporaryOffhandItem(food);
                companion.startUsingItem(InteractionHand.OFF_HAND);
                useTicks = safeUseDuration(food);
            }
        }
    }

    private int safeUseDuration(ItemStack stack) {
        int duration = stack.getUseDuration(companion);
        return duration <= 0 ? 32 : duration;
    }
}
