package com.elecho.item;

import com.elecho.ModItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * El mechero. Se comporta como pedernal y acero (hogueras, velas, TNT...) y además:
 *  - clic derecho en una antorcha apagada (colocada) la enciende (lo hace el propio bloque),
 *  - clic derecho en el aire con una antorcha apagada en la otra mano la enciende.
 */
public class LighterItem extends FlintAndSteelItem {
    public LighterItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack self = player.getStackInHand(hand);
        Hand otherHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack other = player.getStackInHand(otherHand);
        if (other.isOf(ModItems.EXTINGUISHED_TORCH)) {
            if (!world.isClient) {
                ItemStack lit = new ItemStack(Items.TORCH, 1);
                if (other.getCount() == 1) player.setStackInHand(otherHand, lit);
                else {
                    other.decrement(1);
                    player.getInventory().offerOrDrop(lit);
                }
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.PLAYERS, 1.0f, 1.2f);
                self.damage(1, player, LivingEntity.getSlotForHand(hand));
            }
            return TypedActionResult.success(self, world.isClient);
        }
        return TypedActionResult.pass(self);
    }
}
