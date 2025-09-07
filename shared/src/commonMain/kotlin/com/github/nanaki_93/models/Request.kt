package com.github.nanaki_93.models

import kotlinx.serialization.Serializable


@Serializable
data class SelectRequest(val gameMode: GameMode, val level: Level, val userId: String)

@Serializable
data class LevelListRequest(val gameMode: GameMode, val userId: String)





