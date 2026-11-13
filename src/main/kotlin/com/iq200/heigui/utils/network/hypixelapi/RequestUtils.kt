package com.iq200.heigui.utils.network.hypixelapi

import com.iq200.heigui.Heigui.logger
import com.iq200.heigui.features.impl.render.ClickGUIModule.hypixelApiUrl
import com.iq200.heigui.utils.network.WebUtils.fetchJson
import java.util.concurrent.ConcurrentHashMap

object RequestUtils {

    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

    private data class CacheEntry<T>(
        val value: T,
        val timestamp: Long
    )

    private val uuidCache = ConcurrentHashMap<String, CacheEntry<UuidData>>()
    private val playerCache = ConcurrentHashMap<String, CacheEntry<HypixelData.PlayerInfo>>()

    private fun getServer(endpoint: EndPoint, uuid: String): String =
        hypixelApiUrl + endpoint.name.lowercase() + "/" + uuid

    private fun <T> MutableMap<String, CacheEntry<T>>.evictExpired() {
        val now = System.currentTimeMillis()
        entries.removeIf { now - it.value.timestamp >= CACHE_TTL_MS }
    }

    private fun getPlayerFromCache(name: String): HypixelData.PlayerInfo? {
        val key = name.lowercase()
        playerCache.evictExpired()
        return playerCache[key]?.value
    }

    private fun putPlayerInCache(info: HypixelData.PlayerInfo) {
        if (info.profileData.profiles.isEmpty()) {
            logger.info("Refusing to cache empty profile!")
            return
        }

        playerCache[info.name.lowercase()] = CacheEntry(info, System.currentTimeMillis())

        logger.info("Cached ${info.name}. Cache size: ${playerCache.size}")
    }

    suspend fun getProfile(name: String): Result<HypixelData.PlayerInfo> {
        getPlayerFromCache(name)?.let { return Result.success(it) }
        val uuidData = getUuid(name).getOrElse { return Result.failure(Exception(it.cause)) }

        return fetchJson<HypixelData.ProfilesData>(getServer(EndPoint.GET, uuidData.id)).map { data ->
            data.failed?.let { return Result.failure(Exception("Failed to get hypixel data: $it")) }

            HypixelData.PlayerInfo(data, uuidData.id, uuidData.name).also(::putPlayerInCache)
        }
    }

    suspend fun getUuid(name: String): Result<UuidData> {
        val key = name.lowercase()

        uuidCache.evictExpired()

        uuidCache[key]?.let { return Result.success(it.value) }

        return fetchJson<UuidData>(
            "https://api.minecraftservices.com/minecraft/profile/lookup/name/$name"
        ).onSuccess(::cacheUuidData)
    }

    suspend fun getName(uuid: String): Result<UuidData> {
        val key = uuid.replace("-", "").lowercase()

        uuidCache.evictExpired()

        uuidCache[key]?.let { return Result.success(it.value) }

        return fetchJson<UuidData>(
            "https://sessionserver.mojang.com/session/minecraft/profile/$key"
        ).onSuccess(::cacheUuidData)
    }

    private fun cacheUuidData(data: UuidData) {
        val entry = CacheEntry(data, System.currentTimeMillis())
        uuidCache[data.name.lowercase()] = entry
        uuidCache[data.id.replace("-", "").lowercase()] = entry
    }

    enum class EndPoint { SECRETS, GET }
    data class UuidData(val name: String, val id: String)
}
