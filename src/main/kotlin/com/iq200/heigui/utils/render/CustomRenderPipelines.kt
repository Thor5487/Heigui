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
        // 🌟 關鍵修正：直接繼承原版的烽火台 Snippet！
        // 這樣它就會自動帶有正確的 VertexFormat、Shader 以及貼圖插槽 (Sampler0)
        // (註：請用 IDE 自動補全確認名稱，通常是 BEACON_BEAM_SNIPPET 或 RENDERTYPE_BEACON_BEAM_SNIPPET)
        RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
            .withDepthStencilState(Optional.empty()) // 🌟 唯一修改：關閉深度測試，達成穿牆 ESP
            .withLocation("heigui/beacon_esp")
            .build()
    )
}