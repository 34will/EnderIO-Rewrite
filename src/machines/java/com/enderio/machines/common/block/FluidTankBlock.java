package com.enderio.machines.common.block;

import java.util.Optional;

import com.enderio.machines.common.blockentity.FluidTankBlockEntity;
import com.enderio.machines.common.blockentity.base.MachineBlockEntity;
import com.enderio.machines.common.util.FluidUtil;
import com.mojang.datafixers.util.Pair;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import org.apache.commons.lang3.tuple.Triple;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

public class FluidTankBlock extends MachineBlock {
    private enum InteractionType {
        PICKUP,
        PLACE
    }

    public FluidTankBlock(Properties properties, BlockEntityEntry<? extends MachineBlockEntity> blockEntityType) {
        super(properties, blockEntityType);
    }

    private void playPickupSound(Player player, Level level, Fluid fluid, BlockPos blockPos) {
        // Code adapted from BucketItem class.
        SoundEvent sound = fluid.getFluidType().getSound(player, level, blockPos, SoundActions.BUCKET_FILL);
        if (sound == null) {
            sound = fluid.getPickupSound().orElse(SoundEvents.BUCKET_FILL);
        }

        level.playSound(player, blockPos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(player, GameEvent.FLUID_PICKUP, blockPos);
    }

    private void playPlaceSound(Player player, Level level, Fluid fluid, BlockPos blockPos) {
        // Code adapted from BucketItem class.
        SoundEvent sound = fluid.getFluidType().getSound(player, level, blockPos, SoundActions.BUCKET_EMPTY);
        if (sound == null) {
            if (fluid == Fluids.LAVA) {
                sound = SoundEvents.BUCKET_EMPTY_LAVA;
            } else {
                sound = SoundEvents.BUCKET_EMPTY;
            }
        }

        level.playSound(player, blockPos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(player, GameEvent.FLUID_PLACE, blockPos);
    }

    private Triple<InteractionResult, Fluid, InteractionType> useInternal(BlockState pState, Level pLevel,
            BlockPos pPos, Player pPlayer,
            InteractionHand pHand) {
        BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
        if (!(blockEntity instanceof FluidTankBlockEntity fluidTankBlockEntity)) {
            return null;
        }

        ItemStack itemStack = pPlayer.getItemInHand(pHand);
        if (itemStack.isEmpty()) {
            return null;
        }

        if (itemStack.getItem() instanceof BucketItem bucket) {
            // If bucket is empty.
            Fluid bucketFluid = bucket.getFluid();
            if (bucketFluid == Fluids.EMPTY) {
                Pair<Boolean, FluidStack> result = fluidTankBlockEntity.drainTankWithBucket();
                // If the block entity rejects the bucket, most likely because it's not the same
                // fluid type.
                if (!result.getFirst()) {
                    return Triple.of(InteractionResult.FAIL, null, null);
                }

                // Give the player back a full bucket.
                Fluid blockFluid = result.getSecond().getRawFluid();
                ItemStack fullBucket = blockFluid.getBucket().getDefaultInstance();
                if (itemStack.getCount() == 1) {
                    pPlayer.setItemInHand(pHand, fullBucket);
                } else {
                    itemStack.shrink(1);
                    pPlayer.getInventory().add(fullBucket);
                }
                return Triple.of(InteractionResult.SUCCESS, blockFluid, InteractionType.PICKUP);
            }

            // If bucket has fluid.
            int result = fluidTankBlockEntity.fillTankFromBucket(bucket);
            if (result <= FluidUtil.INVALID_OPERATION) {
                return null;
            }
            if (result == 0) {
                return Triple.of(InteractionResult.FAIL, null, null);
            }
            // Give the player back an empty bucket.
            pPlayer.setItemInHand(pHand, Items.BUCKET.getDefaultInstance());
            return Triple.of(InteractionResult.SUCCESS, bucketFluid, InteractionType.PLACE);
        }

        Optional<IFluidHandlerItem> fluidHandlerCap = itemStack
                .getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY)
                .resolve();
        if (!fluidHandlerCap.isPresent()) {
            return null;
        }

        // Read the information for the Fluid in the block.
        IFluidHandlerItem fluidHandler = fluidHandlerCap.get();
        FluidStack blockFluidStack = fluidTankBlockEntity.getFluidTank().getFluid();
        boolean blockIsEmpty = blockFluidStack == null ? true : blockFluidStack.isEmpty();

        // Read the information for the Fluid in the item.
        boolean itemIsEmpty = false;
        for (int i = 0; i < fluidHandler.getTanks(); i++) {
            if (fluidHandler.getFluidInTank(i).isEmpty()) {
                itemIsEmpty = true;
                break;
            }
        }

        // If the block and the item are both empty: pass.
        if (blockIsEmpty && itemIsEmpty) {
            return null;
        }

        InteractionType interactionType;
        int result;
        Fluid fluid;
        // If the block is empty, and the item is not empty: fill the block from the
        // item.
        if (blockIsEmpty) {
            interactionType = InteractionType.PLACE;
            result = fluidTankBlockEntity.fillTankFromItem(fluidHandler);
            fluid = fluidTankBlockEntity.getFluidTank().getFluid().getFluid();
            // If the block is not empty, and the item is empty: fill the item from the
            // block.
        } else if (itemIsEmpty) {
            interactionType = InteractionType.PICKUP;
            fluid = fluidTankBlockEntity.getFluidTank().getFluid().getFluid();
            result = fluidTankBlockEntity.drainTankWithItem(fluidHandler);
            // If the block is not empty, and the item is not empty:
        } else {
            interactionType = InteractionType.PLACE;
            // If the fluid is the same: fill the block from the item.
            fluid = fluidTankBlockEntity.getFluidTank().getFluid().getFluid();
            result = fluidTankBlockEntity.fillTankFromItem(fluidHandler);
        }

        if (result <= FluidUtil.INVALID_OPERATION) {
            return null;
        }
        if (result == 0) {
            return Triple.of(InteractionResult.FAIL, null, null);
        }
        return Triple.of(InteractionResult.SUCCESS, fluid, interactionType);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand,
            BlockHitResult pHit) {
        Triple<InteractionResult, Fluid, InteractionType> result = useInternal(pState, pLevel, pPos, pPlayer, pHand);
        if (result != null) {
            switch (result.getLeft()) {
                case SUCCESS:
                    if (result.getRight() == InteractionType.PICKUP) {
                        playPickupSound(pPlayer, pLevel, result.getMiddle(), pPos);
                    } else {
                        playPlaceSound(pPlayer, pLevel, result.getMiddle(), pPos);
                    }
                    return result.getLeft();
                case FAIL:
                    return result.getLeft();
                default:
                    break;
            }
        }

        return super.use(pState, pLevel, pPos, pPlayer, pHand, pHit);
    }
}