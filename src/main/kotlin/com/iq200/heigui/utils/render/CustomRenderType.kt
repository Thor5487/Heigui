package com.iq200.heigui.utils.render

import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier

object CustomRenderType {

    private val BEACON_BEAM_TEXTURE = Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png")

    // RenderTypes.LINES, RenderTypes.LINES_TRANSLUCENT || LINES_ESP, LINES_TRANSLUCENT_ESP

    val LINES_ESP: RenderType = RenderType.create(
        "lines-esp",
        RenderSetup.builder(CustomRenderPipelines.LINES_ESP)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup()
    )

    val LINES_TRANSLUCENT_ESP: RenderType = RenderType.create(
        "lines-translucent-esp",
        RenderSetup.builder(CustomRenderPipelines.LINES_TRANSLUCENT_ESP)
            .createRenderSetup()
    )

    // RenderTypes.DEBUG_FILLED_BOX / RenderTypes.debugFilledBox() || QUADS_OPAQUE / QUADS_TRANSLUCENT / QUADS_ESP / QUADS_TRANSLUCENT_ESP

    val QUADS_OPAQUE: RenderType = RenderType.create(
        "quads-opaque",
        RenderSetup.builder(CustomRenderPipelines.QUADS_OPAQUE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup()
    )

    val QUADS_TRANSLUCENT: RenderType = RenderType.create(
        "quads-translucent",
        RenderSetup.builder(CustomRenderPipelines.QUADS_TRANSLUCENT)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .sortOnUpload()
            .createRenderSetup()
    )

    val QUADS_ESP: RenderType = RenderType.create(
        "quads-esp",
        RenderSetup.builder(CustomRenderPipelines.QUADS_ESP)
            .createRenderSetup()
    )

    val QUADS_TRANSLUCENT_ESP: RenderType = RenderType.create(
        "quads-translucent-esp",
        RenderSetup.builder(CustomRenderPipelines.QUADS_TRANSLUCENT_ESP)
            .sortOnUpload()
            .createRenderSetup()
    )

    val BEACON_BEAM_OPAQUE_ESP: RenderType = RenderType.create(
        "beacon-beam-opaque-esp",
        RenderSetup.builder(CustomRenderPipelines.BEACON_BEAM_OPAQUE_ESP)
            .withTexture("Sampler0", BEACON_BEAM_TEXTURE)
            .sortOnUpload()
            .createRenderSetup()
    )

    val BEACON_BEAM_TRANSLUCENT_ESP: RenderType = RenderType.create(
        "beacon-beam-translucent-esp",
        RenderSetup.builder(CustomRenderPipelines.BEACON_BEAM_TRANSLUCENT_ESP)
            .withTexture("Sampler0", BEACON_BEAM_TEXTURE)
            .sortOnUpload()
            .createRenderSetup()
    )
}
