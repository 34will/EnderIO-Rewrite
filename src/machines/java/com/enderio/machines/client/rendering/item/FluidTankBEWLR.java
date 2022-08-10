package com.enderio.machines.client.rendering.item;

import com.enderio.base.common.capability.FluidHandlerBlockItemStack;
import com.enderio.machines.client.rendering.blockentity.FluidTankBER;
import com.enderio.machines.common.blockentity.FluidTankBlockEntity;
import com.enderio.machines.common.init.MachineBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

// TODO: No longer lights in the inventory/hand like other machines...
public class FluidTankBEWLR extends BlockEntityWithoutLevelRenderer {
    public static final FluidTankBEWLR INSTANCE = new FluidTankBEWLR(
            Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());

    public FluidTankBEWLR(BlockEntityRenderDispatcher blockEntityRenderDispatcher, EntityModelSet entityModelSet) {
        super(blockEntityRenderDispatcher, entityModelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemTransforms.TransformType transformType, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // Get the model for the fluid tank block
        BakedModel model = Minecraft.getInstance().getModelManager()
                .getModel(new ModelResourceLocation(ForgeRegistries.ITEMS.getKey(stack.getItem()), "facing=north"));
        poseStack.pushPose();

        // Render the main model
        Minecraft.getInstance().getItemRenderer().renderModelLists(model, stack, packedLight, packedOverlay, poseStack,
                buffer.getBuffer(RenderType.cutout()));

        // Read the fluid from the NBT, if it has fluid, then we render it.
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains(FluidHandlerBlockItemStack.BLOCK_ENTITY_NBT_KEY)) {
            CompoundTag blockEntityTag = nbt.getCompound(FluidHandlerBlockItemStack.BLOCK_ENTITY_NBT_KEY);
            if (blockEntityTag != null && blockEntityTag.contains(FluidHandlerBlockItemStack.FLUID_NBT_KEY)) {
                FluidStack fluidStack = FluidStack
                        .loadFluidStackFromNBT(blockEntityTag.getCompound(FluidHandlerBlockItemStack.FLUID_NBT_KEY));
                if (fluidStack != null) {
                    Fluid fluid = fluidStack.getFluid();
                    int amount = fluidStack.getAmount();

                    if (fluid != null && fluid != Fluids.EMPTY && amount > 0) {
                        // Get the preferred render buffer
                        VertexConsumer fluidBuffer = buffer
                                .getBuffer(ItemBlockRenderTypes.getRenderLayer(fluid.defaultFluidState()));

                        // Determine capacity.
                        int capacity = FluidTankBlockEntity.Standard.CAPACITY;
                        if (stack.is(MachineBlocks.PRESSURIZED_FLUID_TANK.get().asItem())) {
                            capacity = FluidTankBlockEntity.Enhanced.CAPACITY;
                        }

                        PoseStack.Pose pose = poseStack.last();
                        IClientFluidTypeExtensions props = IClientFluidTypeExtensions.of(fluid);
                        FluidTankBER.renderFluid(pose.pose(), pose.normal(), fluidBuffer, fluid,
                                amount / (float) capacity, props.getTintColor());
                    }
                }
            }
        }
        poseStack.popPose();
    }
}
