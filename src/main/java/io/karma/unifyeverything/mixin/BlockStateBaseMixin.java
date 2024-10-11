package io.karma.unifyeverything.mixin;

import io.karma.unifyeverything.AlmostUnifyEverything;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * @author Alexander Hinze
 * @since 11/10/2024
 */
@Mixin(BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Shadow
    public abstract Block getBlock();

    @Shadow
    protected abstract BlockState asState();

    @SuppressWarnings("deprecation")
    @Inject(method = "getDrops", at = @At("HEAD"), cancellable = true)
    private void onGetDrops(LootParams.Builder builder, final CallbackInfoReturnable<List<ItemStack>> cbi) {
        // @formatter:off
        cbi.setReturnValue(getBlock().getDrops(asState(), builder)
            .stream()
            .map(AlmostUnifyEverything::unify)
            .toList());
        // @formatter:on
        cbi.cancel();
    }
}
