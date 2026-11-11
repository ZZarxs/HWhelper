package ru.hwhelper.client;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;

/** Scoped native pipeline states replace the original glBlendFunc/depthMask calls. */
final class CosmeticLayers {
    static void initialize(){}
    static final RenderType CORE=layer("cosmetic_core","white",false,true,false);
    static final RenderType SURFACE=layer("cosmetic_surface","white",false,false,false);
    static final RenderType LIGHT=layer("cosmetic_light","white",true,false,false);
    static final RenderType BLOOM=layer("cosmetic_bloom","bloom",true,false,false);
    static final RenderType SMOKE=layer("cosmetic_smoke","smoke",false,false,true);
    private static RenderType layer(String name,String texture,boolean additive,boolean depthWrite,boolean sort){
        var pipeline=RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("hwhelper","pipeline/"+name))
                .withSampler("Sampler1").withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withBlend(additive?new BlendFunction(SourceFactor.SRC_ALPHA,DestFactor.ONE):BlendFunction.TRANSLUCENT)
                .withDepthWrite(depthWrite).withColorWrite(true,!additive).withCull(false).build();
        RenderPipelines.register(pipeline);
        var setup=RenderSetup.builder(pipeline).withTexture("Sampler0",Identifier.fromNamespaceAndPath("hwhelper","textures/world/"+texture+".png")).useOverlay().bufferSize(786432);
        if(sort)setup.sortOnUpload();
        return RenderType.create("hwhelper_"+name,setup.createRenderSetup());
    }
}
