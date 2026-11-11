package ru.hwhelper.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hwhelper.client.Cosmetics;

/** Only the reserved IDs of our unregistered clone models receive this translucent material. */
@Mixin(LivingEntityRenderer.class)
abstract class CloneMaterialMixin {
    @Shadow public abstract Identifier getTextureLocation(LivingEntityRenderState state);
    @Inject(method="setupRotations",at=@At("TAIL"))
    private void hwhelper$narutoLean(LivingEntityRenderState state,com.mojang.blaze3d.vertex.PoseStack matrices,float bodyRot,float scale,CallbackInfo ci){
        if(state instanceof AvatarRenderState avatar&&Cosmetics.running(avatar.id)&&Cosmetics.signs(avatar.id)<.001f){
            matrices.translate(0,.75,0);matrices.mulPose(com.mojang.math.Axis.XP.rotation(.45f));matrices.translate(0,-.75,0);
        }
    }
    @Inject(method="getModelTint",at=@At("HEAD"),cancellable=true)
    private void hwhelper$cloneTint(LivingEntityRenderState state,CallbackInfoReturnable<Integer> ci){
        if(state instanceof AvatarRenderState avatar){int color=Cosmetics.cloneColor(avatar.id);if(color!=0)ci.setReturnValue(color);}
    }
    @Inject(method="getRenderType",at=@At("HEAD"),cancellable=true)
    private void hwhelper$cloneLayer(LivingEntityRenderState state,boolean visible,boolean translucent,boolean outline,CallbackInfoReturnable<RenderType> ci){
        if(state instanceof AvatarRenderState avatar&&Cosmetics.cloneColor(avatar.id)!=0)ci.setReturnValue(RenderTypes.entityTranslucent(getTextureLocation(state)));
    }
}
