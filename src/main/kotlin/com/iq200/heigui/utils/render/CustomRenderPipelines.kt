package com.iq200.heigui.utils.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.pipeline.*
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

object CustomRenderPipelines {
    private val NO_DEPTH = DepthStencilState(CompareOp.ALWAYS_PASS, false)
    private val TRANSLUCENT = ColorTargetState(BlendFunction.TRANSLUCENT)

    val LINES_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withDepthStencilState(NO_DEPTH)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withLocation("heigui/lines_esp")
            .build()
    )

    val LINES_TRANSLUCENT_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withDepthStencilState(NO_DEPTH)
            .withColorTargetState(TRANSLUCENT)
            .withLocation("heigui/lines_translucent_esp")
            .build()
    )

    val QUADS_OPAQUE: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withCull(false)
            .withLocation("heigui/quads_opaque")
            .build()
    )

    val QUADS_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withDepthStencilState(NO_DEPTH)
            .withCull(false)
            .withLocation("heigui/quads_esp")
            .build()
    )

    val QUADS_TRANSLUCENT: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withColorTargetState(TRANSLUCENT)
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withLocation("heigui/quads_translucent")
            .build()
    )

    val QUADS_TRANSLUCENT_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withDepthStencilState(NO_DEPTH)
            .withColorTargetState(TRANSLUCENT)
            .withCull(false)
            .withLocation("heigui/quads_translucent_esp")
            .build()
    )

    val BEACON_BEAM_OPAQUE_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
            .withDepthStencilState(NO_DEPTH)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withLocation("heigui/beacon_beam_opaque_esp")
            .build()
    )

    val BEACON_BEAM_TRANSLUCENT_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
            .withDepthStencilState(NO_DEPTH)
            .withColorTargetState(TRANSLUCENT)
            .withLocation("heigui/beacon_beam_translucent_esp")
            .build()
    )

    val PIPELINE_ROUND_RECT: RenderPipeline = roundRect("round_rect", RenderPipelines.GUI_SNIPPET)
    val PIPELINE_ROUND_RECT_TEXTURED: RenderPipeline = roundRect("round_rect_textured", RenderPipelines.GUI_TEXTURED_SNIPPET)
    val PIPELINE_ROUND_RECT_SHADOW: RenderPipeline = roundRect("round_rect_shadow", RenderPipelines.GUI_SNIPPET)

    private fun roundRect(name: String, snippet: RenderPipeline.Snippet): RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(snippet)
            .withLocation(Identifier.fromNamespaceAndPath("heigui", "pipeline/$name"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("heigui", "core/$name"))
            .withVertexShader(Identifier.fromNamespaceAndPath("heigui", "core/round_rect"))
            .withVertexBinding(0, RoundedRectRenderer.FORMAT)
            .build()
    )
}
