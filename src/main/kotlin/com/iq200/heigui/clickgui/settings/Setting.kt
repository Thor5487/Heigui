package com.iq200.heigui.clickgui.settings

import com.iq200.heigui.features.Module
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Superclass of Settings.
 * @author Aton
 */
abstract class Setting<T>(
    val name: String,
    var description: String = "",
) : ReadWriteProperty<Module, T>, PropertyDelegateProvider<Module, ReadWriteProperty<Module, T>> {

    /**
     * Default value of the setting
     */
    abstract val default: T

    /**
     * Value of the setting
     */
    abstract var value: T

    protected var hidden = false

    fun hide(): Setting<T> {
        hidden = true
        return this
    }



    /**
     * Dependency for if it should be shown in the [click gui][Module].
     */
    protected var visibilityDependency: (() -> Boolean)? = null

    protected var lockDependency: (() -> Boolean)? = null
    /**
     * Resets the setting to the default value
     */
    open fun reset() {
        value = default
    }

    val isVisible: Boolean
        get() {
            return lockDependency?.invoke() != false && (visibilityDependency?.invoke() ?: true) && !hidden
            // 🌟 2. 如果沒被鎖死，再檢查原本的排版隱藏依賴 (例如 autoImpel 沒開時隱藏 rotationSpeed)
        }

    override operator fun provideDelegate(thisRef: Module, property: KProperty<*>): ReadWriteProperty<Module, T> =
        thisRef.registerSetting(this)

    override operator fun getValue(thisRef: Module, property: KProperty<*>): T {
        // 🌟 如果被邏輯鎖死了，強制回傳預設值 (等同於失效)
        if (lockDependency?.invoke() == false) {
            return default
        }
        return value
    }
    override operator fun setValue(thisRef: Module, property: KProperty<*>, value: T) {
        this.value = value
    }

    companion object {

        fun <K : Setting<T>, T> K.withDependency(dependency: () -> Boolean): K {
            visibilityDependency = dependency
            return this
        }

        fun <K : Setting<T>, T> K.withLock(dependency: () -> Boolean): K {
            lockDependency = dependency
            return this
        }
    }
}