package com.iq200.heigui.utils.skyblock

object SplitsUtils {




    val currentData: Triple<List<Long>, List<Long>, Int>
        get() = SplitsManager.getAndUpdateSplitsTimes(SplitsManager.currentSplits)


    val currentTimes: List<Long>
        get() = currentData.first
}