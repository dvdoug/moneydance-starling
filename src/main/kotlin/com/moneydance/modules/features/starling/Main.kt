package com.moneydance.modules.features.starling

import com.moneydance.apps.md.controller.FeatureModule
import com.moneydance.apps.md.controller.FeatureModuleContext
import com.moneydance.apps.md.controller.Main as MdMain
import com.moneydance.apps.md.view.gui.MoneydanceGUI
import com.moneydance.modules.features.starling.settings.SettingsStore
import com.moneydance.modules.features.starling.sync.SourceLoader
import com.moneydance.modules.features.starling.sync.SyncService
import com.moneydance.modules.features.starling.ui.MdNotify
import com.moneydance.modules.features.starling.ui.StarlingWindow
import java.awt.Image
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import javax.swing.Timer

class Main : FeatureModule() {

    private var window: StarlingWindow? = null
    private var openTimer: Timer? = null

    override fun init() {
        val ctx = getContext() ?: return
        try {
            ctx.registerFeature(this, SHOW, iconImage, name)
        } catch (e: Exception) {
            MdNotify.log("failed to register feature: ${e.message}", e)
        }
        MdNotify.log("initialized build $build")
    }

    override fun getName(): String = DISPLAY_NAME

    override fun getIconImage(): Image? {
        val stream = javaClass.getResourceAsStream("icon.png") ?: return super.getIconImage()
        return stream.use { ImageIO.read(it) }
    }

    override fun invoke(uri: String) {
        val command = uri.substringBefore(':').substringBefore('?')
        if (command == SHOW || command == ID || command.isEmpty()) {
            SwingUtilities.invokeLater { showWindow() }
        }
    }

    override fun handleEvent(appEvent: String) {
        when {
            appEvent.equals("md:file:opened", ignoreCase = true) ->
                SwingUtilities.invokeLater { scheduleAutoImport() }
            appEvent.equals("md:file:closing", ignoreCase = true) ||
                appEvent.equals("md:file:closed", ignoreCase = true) ->
                SwingUtilities.invokeLater {
                    cancelAutoImport()
                    SyncService.discardInFlight()
                    closeWindow()
                }
        }
    }

    override fun cleanup() {
        unload()
    }

    override fun unload() {
        cancelAutoImport()
        SyncService.discardInFlight()
        closeWindow()
    }

    private fun showWindow() {
        val existing = window
        if (existing != null && existing.isDisplayable) {
            existing.bringToFront()
            return
        }
        window = null
        val gui = moneydanceGUI()
        if (gui == null) {
            MdNotify.log("UI not ready")
            return
        }
        val book = getContext()?.currentAccountBook
        val created = StarlingWindow(gui, book, SettingsStore.fromBook(book))
        created.onGoneAway = {
            if (window === created) window = null
        }
        created.onAutomaticImportChanged = { enabled ->
            if (enabled) scheduleAutoImport() else cancelAutoImport()
        }
        window = created
        created.bringToFront()
    }

    private fun scheduleAutoImport() {
        cancelAutoImport()
        val store = SettingsStore.fromBook(getContext()?.currentAccountBook) ?: return
        if (!store.importOnOpen()) return
        val timer = Timer(AUTO_IMPORT_REPEAT_MS) { runAutoImport() }
        timer.initialDelay = AUTO_IMPORT_FIRST_DELAY_MS
        timer.isRepeats = true
        openTimer = timer
        timer.start()
    }

    private fun cancelAutoImport() {
        openTimer?.stop()
        openTimer = null
    }

    private fun runAutoImport() {
        val gui = moneydanceGUI() ?: return
        val book = getContext()?.currentAccountBook ?: return
        val store = SettingsStore.fromBook(book) ?: return
        if (!store.importOnOpen()) {
            cancelAutoImport()
            return
        }
        if (store.tokens().isEmpty()) return
        val maps = store.mappings()
        if (maps.none { it.moneydanceAccountUuid.isNotBlank() }) return
        val sources = try {
            SourceLoader.loadActive(store.tokens(), store.catalogue()).sources
        } catch (e: Exception) {
            MdNotify.log("auto-import list failed: ${e.message}", e)
            return
        }
        SyncService.start(
            book = book,
            settings = store,
            gui = gui,
            mappings = maps,
            sources = sources,
            onStatus = { text -> liveWindow()?.showStatus(text) },
            onBusy = { running -> liveWindow()?.setImportBusy(running) },
            onSources = { list -> liveWindow()?.showSources(list) },
            onMappings = { updated -> liveWindow()?.showSavedMappings(updated) }
        )
    }

    private fun liveWindow(): StarlingWindow? = window?.takeIf { it.isDisplayable }

    private fun closeWindow() {
        val current = window
        window = null
        current?.onGoneAway = null
        current?.goAway()
    }

    private fun moneydanceGUI(): MoneydanceGUI? {
        val ctx: FeatureModuleContext = getContext() ?: return null
        val mdMain = ctx as? MdMain ?: return null
        return mdMain.ui as? MoneydanceGUI
    }

    companion object {
        const val ID: String = "starling"
        const val DISPLAY_NAME: String = "Starling Bank"
        const val AUTHOR: String = "Doug Wright"
        const val SHOW: String = "show"

        const val THIRD_PARTY_DISCLAIMER: String =
            "Unofficial extension by Doug Wright. Not affiliated with Starling Bank or The Infinite Kind."

        private const val AUTO_IMPORT_FIRST_DELAY_MS: Int = 1_800
        private const val AUTO_IMPORT_REPEAT_MS: Int = 30 * 60 * 1_000
    }
}
