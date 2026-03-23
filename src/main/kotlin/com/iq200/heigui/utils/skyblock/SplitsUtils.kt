package com.iq200.heigui.utils.skyblock

object SplitsUtils {
    /**
     * 提供給其他 Feature 引用的數據接口
     * Triple(各階段差值時間, 各階段差值計時, 目前進行到第幾個階段)
     */
    val currentData: Triple<List<Long>, List<Long>, Int>
        get() = SplitsManager.getAndUpdateSplitsTimes(SplitsManager.currentSplits)

    // 如果其他功能只需要時間列表，可以用這個
    val currentTimes: List<Long>
        get() = currentData.first
}