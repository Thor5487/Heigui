package com.iq200.heigui.utils.render

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.util.Optional

object CustomRenderPipelines {
    val LINES_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withDepthStencilState(Optional.empty())
            .withLocation("heigui/lines_esp")
            .build()
    )

    val LINES_TRANSLUCENT_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withDepthStencilState(Optional.empty())
            .withLocation("heigui/lines_translucent_esp")
            .build()
    )

    val QUADS_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withDepthStencilState(Optional.empty())
            .withLocation("heigui/quads_esp")
            .build()
    )

    val PIPELINE_ROUND_RECT: RenderPipeline = roundRect("round_rect", RenderPipelines.GUI_SNIPPET, "round_rect")
    val PIPELINE_ROUND_RECT_TEXTURED: RenderPipeline = roundRect("round_rect_textured", RenderPipelines.GUI_TEXTURED_SNIPPET, "round_rect")
    val PIPELINE_ROUND_RECT_SHADOW: RenderPipeline = roundRect("round_rect_shadow", RenderPipelines.GUI_SNIPPET, "round_rect_shadow")

    private fun roundRect(name: String, snippet: RenderPipeline.Snippet, vertex: String): RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(snippet)
            .withLocation(Identifier.fromNamespaceAndPath("heigui", "pipeline/$name"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("heigui", "core/$name"))
            .withVertexShader(Identifier.fromNamespaceAndPath("heigui", "core/$vertex"))
            .withVertexBinding(0, RoundedRectRenderer.FORMAT)
            .build()
    )

    val BEACON_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader("core/rendertype_beacon_beam")
            .withFragmentShader("core/rendertype_beacon_beam")

            // 🌟 關鍵 1：必須在管線宣告這個插槽，Shader 才能吃到圖！
            .withSampler("Sampler0")

            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty()) // 穿牆透視
            .withLocation("heigui/beacon_esp")
            .build()
    )
}