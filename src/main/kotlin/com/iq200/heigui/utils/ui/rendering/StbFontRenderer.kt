package com.iq200.heigui.utils.ui.rendering

import com.iq200.heigui.Heigui
import com.iq200.heigui.Heigui.mc
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTTPackContext
import org.lwjgl.stb.STBTTPackedchar
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * STB TrueType renderer modeled after NanoVG's font-atlas approach.
 *
 * Glyphs are rasterized at the actual ClickGUI font sizes and submitted through
 * RenderPearl, so the implementation remains independent of the active GPU backend.
 */
object StbFontRenderer {

    private const val FIRST_CODEPOINT = 32
    // Include the Latin-1 supplement so units such as "°/t" stay on the
    // ClickGUI font instead of falling back to Minecraft's bitmap font.
    private const val LAST_CODEPOINT = 255
    private const val GLYPH_COUNT = LAST_CODEPOINT - FIRST_CODEPOINT + 1
    private const val ATLAS_SIZE = 2048
    private const val OVERSAMPLE = 4
    private val FONT_SIZES = intArrayOf(16, 18, 20, 22)
    private val TEXTURE_ID = Identifier.fromNamespaceAndPath("heigui", "stb_font_atlas")

    private val atlas: Atlas? by lazy {
        runCatching(::createAtlas).onFailure {
            Heigui.logger.error("Failed to initialize the STB ClickGUI font renderer", it)
        }.getOrNull()
    }

    fun isAvailable(): Boolean = atlas != null

    fun supports(text: String): Boolean = text.all { it == '\n' || it.code in FIRST_CODEPOINT..LAST_CODEPOINT }

    fun width(text: String, size: Float): Float {
        val atlas = atlas ?: return 0f
        val face = atlas.face(size)
        var current = 0f
        var widest = 0f
        var previous = -1

        for (character in text) {
            if (character == '\n') {
                widest = max(widest, current)
                current = 0f
                previous = -1
                continue
            }

            val codepoint = character.code
            if (previous >= 0) current += atlas.kerning(previous, codepoint, face)
            current += face.glyph(codepoint).advance
            previous = codepoint
        }
        return max(widest, current)
    }

    fun verticalBounds(text: String, size: Float): Pair<Float, Float> {
        val atlas = atlas ?: return 0f to size
        val face = atlas.face(size)
        var top = Float.POSITIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY

        for (character in text) {
            if (character == '\n') continue
            val glyph = face.glyph(character.code)
            if (glyph.height <= 0f) continue
            top = minOf(top, face.ascent + glyph.yOffset)
            bottom = maxOf(bottom, face.ascent + glyph.yOffset + glyph.height)
        }

        return if (top.isFinite() && bottom.isFinite()) top to bottom else 0f to size
    }

    fun draw(
        context: GuiGraphicsExtractor,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        shadow: Boolean
    ): Boolean {
        val atlas = atlas ?: return false
        if (!supports(text)) return false

        if (shadow) {
            val shadowColor = (color and 0xFF000000.toInt()) or ((color and 0x00FCFCFC) shr 2)
            submit(context, atlas, text, x + 2f, y + 2f, size, shadowColor)
        }
        submit(context, atlas, text, x, y, size, color)
        return true
    }

    fun wrap(text: String, width: Float, size: Float): List<String> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<String>()

        for (paragraph in text.split('\n')) {
            if (paragraph.isEmpty()) {
                result.add("")
                continue
            }

            var start = 0
            while (start < paragraph.length) {
                var end = start
                var lastSpace = -1
                while (end < paragraph.length) {
                    if (paragraph[end].isWhitespace()) lastSpace = end
                    if (width(paragraph.substring(start, end + 1), size) > width) break
                    end++
                }

                if (end == paragraph.length) {
                    result.add(paragraph.substring(start))
                    break
                }

                val breakAt = if (lastSpace >= start) lastSpace else max(start + 1, end)
                result.add(paragraph.substring(start, breakAt).trimEnd())
                start = breakAt
                while (start < paragraph.length && paragraph[start].isWhitespace()) start++
            }
        }
        return result
    }

    private fun submit(
        context: GuiGraphicsExtractor,
        atlas: Atlas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        val face = atlas.face(size)
        val quads = ArrayList<GlyphQuad>(text.length)
        var cursorX = x
        var cursorY = y + face.ascent
        var previous = -1

        for (character in text) {
            if (character == '\n') {
                cursorX = x
                cursorY += size
                previous = -1
                continue
            }

            val codepoint = character.code
            if (previous >= 0) cursorX += atlas.kerning(previous, codepoint, face)
            val glyph = face.glyph(codepoint)
            if (glyph.width > 0f && glyph.height > 0f) {
                quads.add(
                    GlyphQuad(
                        cursorX + glyph.xOffset,
                        cursorY + glyph.yOffset,
                        cursorX + glyph.xOffset + glyph.width,
                        cursorY + glyph.yOffset + glyph.height,
                        glyph.u0,
                        glyph.v0,
                        glyph.u1,
                        glyph.v1
                    )
                )
            }
            cursorX += glyph.advance
            previous = codepoint
        }

        if (quads.isEmpty()) return
        val pose = Matrix3x2f(context.pose())
        val scissor = context.scissorStack.peek()
        val localBounds = ScreenRectangle(
            floor(quads.minOf { it.x0 }).toInt(),
            floor(quads.minOf { it.y0 }).toInt(),
            ceil(quads.maxOf { it.x1 }).toInt() - floor(quads.minOf { it.x0 }).toInt(),
            ceil(quads.maxOf { it.y1 }).toInt() - floor(quads.minOf { it.y0 }).toInt()
        ).transformMaxBounds(pose)
        val bounds = if (scissor != null) scissor.intersection(localBounds) else localBounds

        context.guiRenderState.addGuiElement(
            TextElement(pose, quads, color, atlas.textureSetup, scissor, bounds)
        )
    }

    private fun createAtlas(): Atlas {
        val fontBytes = loadFontBytes()
        val fontInfo = STBTTFontinfo.malloc()
        check(STBTruetype.stbtt_InitFont(fontInfo, fontBytes)) { "Invalid TrueType font" }

        val bitmap = MemoryUtil.memCalloc(ATLAS_SIZE * ATLAS_SIZE)
        val packContext = STBTTPackContext.malloc()
        val packedFaces = mutableMapOf<Int, PackedFace>()

        try {
            check(STBTruetype.stbtt_PackBegin(packContext, bitmap, ATLAS_SIZE, ATLAS_SIZE, 0, 2, MemoryUtil.NULL)) {
                "Unable to begin STB font packing"
            }

            for (size in FONT_SIZES) {
                STBTruetype.stbtt_PackSetOversampling(packContext, OVERSAMPLE, OVERSAMPLE)
                val characters = STBTTPackedchar.malloc(GLYPH_COUNT)
                try {
                    check(
                        STBTruetype.stbtt_PackFontRange(
                            packContext,
                            fontBytes,
                            0,
                            -size.toFloat(),
                            FIRST_CODEPOINT,
                            characters
                        )
                    ) { "STB font atlas is too small for ${size}px glyphs" }
                    packedFaces[size] = copyFace(fontInfo, size, characters)
                } finally {
                    characters.free()
                }
            }
            STBTruetype.stbtt_PackEnd(packContext)

            val image = NativeImage(ATLAS_SIZE, ATLAS_SIZE, false)
            for (index in 0 until ATLAS_SIZE * ATLAS_SIZE) {
                val alpha = bitmap.get(index).toInt() and 0xFF
                image.setPixelABGR(index % ATLAS_SIZE, index / ATLAS_SIZE, alpha shl 24 or 0x00FFFFFF)
            }

            val texture = LinearDynamicTexture({ "Heigui STB font atlas" }, image)
            mc.textureManager.register(TEXTURE_ID, texture)
            return Atlas(fontBytes, fontInfo, packedFaces, texture)
        } catch (throwable: Throwable) {
            MemoryUtil.memFree(fontBytes)
            fontInfo.free()
            throw throwable
        } finally {
            packContext.free()
            MemoryUtil.memFree(bitmap)
        }
    }

    private fun copyFace(
        fontInfo: STBTTFontinfo,
        size: Int,
        characters: STBTTPackedchar.Buffer
    ): PackedFace {
        val glyphs = Array(GLYPH_COUNT) { index ->
            val packed = characters[index]
            Glyph(
                packed.x0().toInt() / ATLAS_SIZE.toFloat(),
                packed.y0().toInt() / ATLAS_SIZE.toFloat(),
                packed.x1().toInt() / ATLAS_SIZE.toFloat(),
                packed.y1().toInt() / ATLAS_SIZE.toFloat(),
                packed.xoff(),
                packed.yoff(),
                packed.xoff2() - packed.xoff(),
                packed.yoff2() - packed.yoff(),
                packed.xadvance()
            )
        }

        MemoryStack.stackPush().use { stack ->
            val ascent = stack.mallocInt(1)
            val descent = stack.mallocInt(1)
            val lineGap = stack.mallocInt(1)
            STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascent, descent, lineGap)
            val scale = STBTruetype.stbtt_ScaleForMappingEmToPixels(fontInfo, size.toFloat())
            return PackedFace(size, scale, ascent[0] * scale, glyphs)
        }
    }

    private fun loadFontBytes(): ByteBuffer {
        val bytes = mc.resourceManager
            .open(Identifier.fromNamespaceAndPath("heigui", "font/gui.ttf"))
            .use { it.readBytes() }
        return MemoryUtil.memAlloc(bytes.size).also {
            it.put(bytes)
            it.flip()
        }
    }

    private class LinearDynamicTexture(label: () -> String, image: NativeImage) : DynamicTexture(label, image) {
        init {
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        }
    }

    private class Atlas(
        val fontBytes: ByteBuffer,
        val fontInfo: STBTTFontinfo,
        private val faces: Map<Int, PackedFace>,
        texture: DynamicTexture
    ) {
        val textureSetup: TextureSetup = TextureSetup.singleTexture(
            texture.textureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        )

        fun face(requestedSize: Float): PackedFace =
            faces.values.minBy { kotlin.math.abs(it.size - requestedSize) }

        fun kerning(left: Int, right: Int, face: PackedFace): Float =
            STBTruetype.stbtt_GetCodepointKernAdvance(fontInfo, left, right) * face.scale
    }

    private data class PackedFace(
        val size: Int,
        val scale: Float,
        val ascent: Float,
        val glyphs: Array<Glyph>
    ) {
        fun glyph(codepoint: Int): Glyph = glyphs[codepoint - FIRST_CODEPOINT]
    }

    private data class Glyph(
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
        val xOffset: Float,
        val yOffset: Float,
        val width: Float,
        val height: Float,
        val advance: Float
    )

    private data class GlyphQuad(
        val x0: Float,
        val y0: Float,
        val x1: Float,
        val y1: Float,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float
    )

    private class TextElement(
        private val pose: Matrix3x2fc,
        private val quads: List<GlyphQuad>,
        private val color: Int,
        private val textureSetup: TextureSetup,
        private val scissor: ScreenRectangle?,
        private val bounds: ScreenRectangle?
    ) : GuiElementRenderState {

        override fun pipeline() = RenderPipelines.GUI_TEXTURED
        override fun textureSetup() = textureSetup
        override fun scissorArea() = scissor
        override fun bounds() = bounds

        override fun buildVertices(consumer: VertexConsumer) {
            for (quad in quads) {
                vertex(consumer, quad.x0, quad.y0, quad.u0, quad.v0)
                vertex(consumer, quad.x0, quad.y1, quad.u0, quad.v1)
                vertex(consumer, quad.x1, quad.y1, quad.u1, quad.v1)
                vertex(consumer, quad.x1, quad.y0, quad.u1, quad.v0)
            }
        }

        private fun vertex(consumer: VertexConsumer, x: Float, y: Float, u: Float, v: Float) {
            consumer.addVertexWith2DPose(pose, x, y).setUv(u, v).setColor(color)
        }
    }
}
