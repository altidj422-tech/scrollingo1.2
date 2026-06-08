package com.scrollingo.app

/**
 * The social apps Scrollingo can watch. Each "app" maps to one or more real
 * package names (TikTok ships under different package names per region).
 */
object TargetApps {

    data class App(val id: String, val label: String, val packages: Set<String>)

    val TIKTOK = App(
        id = "tiktok",
        label = "TikTok",
        packages = setOf(
            "com.zhiliaoapp.musically", // global
            "com.ss.android.ugc.trill",  // some regions
            "com.ss.android.ugc.aweme"   // Douyin
        )
    )

    val INSTAGRAM = App(
        id = "instagram",
        label = "Instagram Reels",
        packages = setOf("com.instagram.android")
    )

    val ALL: List<App> = listOf(TIKTOK, INSTAGRAM)

    val ALL_IDS: Set<String> = ALL.map { it.id }.toSet()

    /** Resolve a set of app ids (e.g. "tiktok") into concrete package names. */
    fun packagesFor(ids: Set<String>): Set<String> =
        ALL.filter { it.id in ids }.flatMap { it.packages }.toSet()
}
