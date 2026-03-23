package com.iq200.heigui.utils

import com.iq200.heigui.utils.render.drawLine
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector4f
import kotlin.math.cos
import kotlin.math.sin

object Render2DUtils {
    /**
     * @param guiGraphics 遊戲傳遞過來的畫筆 (必須傳入！)
     * @param centerX 圓心 X 座標
     * @param centerY 圓心 Y 座標
     * @param radius 圓的半徑
     * @param color 圓的顏色
     * @param thickness 線條粗細
     * @param segments 圓的精細度
     */

    fun drawHollowCircle(
        guiGraphics: GuiGraphics,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Color,
        thickness: Float = 1f,
        segments: Int = 36
    ) {
        val angleStep = (Math.PI * 2) / segments

        for (i in 0 until segments) {
            val angle1 = i * angleStep
            val angle2 = (i + 1) * angleStep

            val x1 = centerX + (radius * cos(angle1)).toFloat()
            val y1 = centerY + (radius * sin(angle1)).toFloat()
            val x2 = centerX + (radius * cos(angle2)).toFloat()
            val y2 = centerY + (radius * sin(angle2)).toFloat()

            guiGraphics.drawLine(x1, y1, x2, y2, color, thickness)
        }
    }

    fun worldToScreen(worldPos: Vec3): Vector2f? {
        val mc = Minecraft.getInstance()
        val gameRenderer = mc.gameRenderer
        val camera = gameRenderer.mainCamera

        val camPos = camera.position()

        val relX = (worldPos.x - camPos.x).toFloat()
        val relY = (worldPos.y - camPos.y).toFloat()
        val relZ = (worldPos.z - camPos.z).toFloat()

        val viewMatrix = Matrix4f().rotation(camera.rotation()).invert()
        val projMatrix = gameRenderer.getProjectionMatrix(mc.options.fov().get().toFloat())

        val clipPos = Vector4f(relX, relY, relZ, 1.0f)

        clipPos.mul(viewMatrix)
        clipPos.mul(projMatrix)

        if (clipPos.w <= 0f) return null

        val ndcX = clipPos.x / clipPos.w
        val ndcY = clipPos.y / clipPos.w

        val window = mc.window
        val screenX = (ndcX + 1f) / 2f * window.guiScaledWidth
        val screenY = (1f - ndcY) / 2f * window.guiScaledHeight

        return Vector2f(screenX, screenY)
    }
}