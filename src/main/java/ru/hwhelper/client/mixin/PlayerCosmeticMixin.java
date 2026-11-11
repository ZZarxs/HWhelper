package ru.hwhelper.client.mixin;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hwhelper.client.Cosmetics;

/** Changes only the local avatar's render pose; movement and packets are untouched. */
@Mixin(PlayerModel.class)
abstract class PlayerCosmeticMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("TAIL"))
    private void hwhelper$pose(AvatarRenderState state,CallbackInfo ci){
        PlayerModel model=(PlayerModel)(Object)this;
        float signs=Cosmetics.signs(state.id);
        if(signs>.001f){
            float pulse=(float)Math.sin(state.ageInTicks*.7f)*.08f;
            model.rightArm.xRot+=( -1.3f+pulse-model.rightArm.xRot)*signs;
            model.leftArm.xRot+=( -1.3f-pulse-model.leftArm.xRot)*signs;
            model.rightArm.yRot+=(-.45f-model.rightArm.yRot)*signs;model.leftArm.yRot+=(.45f-model.leftArm.yRot)*signs;
        }else if(Cosmetics.running(state.id)){
            model.rightArm.xRot=model.leftArm.xRot=1.7f+(float)Math.cos(state.ageInTicks*.6f)*.2f;
            model.rightArm.zRot=.55f;model.leftArm.zRot=-.55f;
            model.rightArm.yRot+=.10f;model.leftArm.yRot-=.10f;model.head.xRot-=.15f;
        }else return;
        model.rightSleeve.xRot=model.rightArm.xRot;model.rightSleeve.yRot=model.rightArm.yRot;model.rightSleeve.zRot=model.rightArm.zRot;
        model.leftSleeve.xRot=model.leftArm.xRot;model.leftSleeve.yRot=model.leftArm.yRot;model.leftSleeve.zRot=model.leftArm.zRot;
        model.jacket.xRot=model.body.xRot;
    }
}
