package com.asmr.player.ui.nav

import androidx.navigation.NavHostController
import java.net.URLEncoder

object Routes {
    const val Library = "library"
    const val Search = "search"
    const val NowPlaying = "now_playing"
    const val Lyrics = "lyrics"

    const val AlbumDetailByIdPattern = "album_detail/{albumId}?rjCode={rjCode}"
    const val AlbumDetailOnlineByRjPattern = "album_detail_online/{rj}"

    const val AlbumDetailByRjPattern = "album_detail_rj/{rj}"
    fun albumDetailByRj(rj: String): String {
        val encoded = URLEncoder.encode(rj, "UTF-8")
        return "album_detail_rj/$encoded"
    }
}

class AppNavigator(
    private val navController: NavHostController
) {
    fun openAlbumDetail(albumId: Long?, rj: String?) {
        val normalizedRj = rj?.trim().orEmpty()
        if (normalizedRj.isNotBlank()) {
            openAlbumDetailByRj(normalizedRj)
            return
        }
        val id = albumId ?: 0L
        if (id <= 0L) return
        val route = "album_detail/$id"
        val popped = navController.popBackStack(route, inclusive = false)
        if (!popped) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
        navController.currentBackStackEntry?.savedStateHandle?.set("refreshToken", System.currentTimeMillis())
    }

    fun openAlbumDetailByRj(rj: String) {
        val normalized = rj.trim().uppercase()
        if (normalized.isBlank()) return
        val route = Routes.albumDetailByRj(normalized)
        val popped = navController.popBackStack(route, inclusive = false)
        if (!popped) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
        navController.currentBackStackEntry?.savedStateHandle?.set("refreshToken", System.currentTimeMillis())
    }

    fun openAlbumDetailByRjStacked(rj: String) {
        val normalized = rj.trim().uppercase()
        if (normalized.isBlank()) return
        val route = Routes.albumDetailByRj(normalized)
        navController.navigate(route) {
            launchSingleTop = false
            restoreState = false
        }
    }
}
