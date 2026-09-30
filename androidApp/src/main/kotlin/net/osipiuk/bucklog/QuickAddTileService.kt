package net.osipiuk.bucklog

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** "＋ Bucklog" in Quick Settings: opens the quick-add panel from anywhere (unlocking first if needed). */
class QuickAddTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = getString(R.string.tile_subtitle)
            updateTile()
        }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(::openQuickAdd) else openQuickAdd()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openQuickAdd() {
        val intent = Intent(this, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Android 13+ can offer to add the tile with one tap. */
        val canRequestAdd: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        fun requestAdd(context: Context) {
            if (!canRequestAdd) return
            context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(context, QuickAddTileService::class.java),
                context.getString(R.string.tile_label),
                Icon.createWithResource(context, R.drawable.ic_tile_add),
                context.mainExecutor,
            ) { }
        }
    }
}
