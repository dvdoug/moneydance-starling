package com.moneydance.modules.features.starling.ui

import com.infinitekind.moneydance.model.AccountBook
import com.moneydance.apps.md.view.gui.MoneydanceGUI
import com.moneydance.apps.md.view.gui.SecondaryDialog
import com.moneydance.modules.features.starling.Main
import com.moneydance.modules.features.starling.api.MappableSource
import com.moneydance.modules.features.starling.api.SourceKind
import com.moneydance.modules.features.starling.settings.AccountMapping
import com.moneydance.modules.features.starling.settings.ApiKeyMask
import com.moneydance.modules.features.starling.settings.PatIndexCodec
import com.moneydance.modules.features.starling.settings.SettingsStore
import com.moneydance.modules.features.starling.sync.SourceLoader
import com.moneydance.modules.features.starling.sync.SyncService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingWorker

class StarlingWindow(
    private val mdGUI: MoneydanceGUI,
    private val book: AccountBook?,
    private val settings: SettingsStore?,
    var onGoneAway: (() -> Unit)? = null,
    var onAutomaticImportChanged: ((Boolean) -> Unit)? = null
) : SecondaryDialog(mdGUI, mdGUI.getTopLevelFrame(), "Starling Bank", false) {

    private val tokenField = JPasswordField()
    private val patModel = DefaultListModel<PatRow>()
    private val patList = JList(patModel)
    private val statusArea = JTextArea(5, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = null
        isOpaque = false
        border = BorderFactory.createEmptyBorder()
    }
    private val progress = JProgressBar(0, 100).apply {
        isStringPainted = true
        string = " "
        isVisible = false
    }
    private val mappingPanel = AccountMappingPanel(book, mdGUI)
    private val refreshButton = JButton("Refresh accounts")
    private val addButton = JButton("Add token")
    private val removeButton = JButton("Remove token")
    private val syncButton = JButton("Import")
    private val importOnOpenBox = JCheckBox("Automatically import")
    private var busy = false
    private var sources: List<MappableSource> = emptyList()

    init {
        setUsesDataFile(true)
        setEscapeKeyCancels(true)
        setRememberSizeLocationKeys("starling.sz", "starling.loc", Dimension(820, 640))
        layout = BorderLayout(0, 0)

        mappingPanel.setSavedMappings(settings?.mappings().orEmpty())
        mappingPanel.border = BorderFactory.createEmptyBorder(0, 20, 8, 20)
        showSavedAccountRows()
        reloadPatList()

        add(buildHeader(), BorderLayout.NORTH)
        add(mappingPanel, BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)

        addButton.addActionListener { addToken() }
        removeButton.addActionListener { removeToken() }
        refreshButton.addActionListener { refreshAccounts() }
        syncButton.addActionListener { syncNow() }
        importOnOpenBox.isSelected = settings?.importOnOpen() ?: false
        importOnOpenBox.addActionListener {
            val on = importOnOpenBox.isSelected
            settings?.setImportOnOpen(on)
            onAutomaticImportChanged?.invoke(on)
        }

        setBusyButtons(true)
        if (settings == null || book == null) {
            setStatus("Open a data file to continue.")
        }
        minimumSize = Dimension(680, 520)
    }

    private fun buildHeader(): JPanel {
        val header = JPanel(GridBagLayout())
        header.border = BorderFactory.createEmptyBorder(16, 20, 8, 20)
        val gbc = GridBagConstraints().apply {
            gridx = 0
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 6, 0)
        }

        val title = JLabel("Starling Bank")
        title.font = title.font.deriveFont(Font.BOLD, title.font.size2D + 3f)
        header.add(title, gbc)

        gbc.insets = Insets(0, 0, 4, 0)
        header.add(JLabel("Import your Starling accounts and Spaces. Your computer talks only to Starling."), gbc)

        val help = JButton("Setup guide")
        help.isBorderPainted = false
        help.isContentAreaFilled = false
        help.isOpaque = false
        help.horizontalAlignment = SwingConstants.LEFT
        help.margin = Insets(0, 0, 0, 0)
        help.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        help.foreground = Color(0x0B, 0x57, 0xD0)
        help.addActionListener { mdGUI.showInternetURL(DOCS_URL) }
        gbc.insets = Insets(0, 0, 12, 0)
        header.add(help, gbc)

        gbc.insets = Insets(0, 0, 4, 0)
        header.add(JLabel("Personal access token (PAT)"), gbc)
        gbc.insets = Insets(0, 0, 2, 0)
        header.add(tokenField, gbc)

        val keyButtons = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        keyButtons.add(addButton)
        keyButtons.add(removeButton)
        keyButtons.add(refreshButton)
        gbc.insets = Insets(0, 0, 8, 0)
        header.add(keyButtons, gbc)

        patList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        patList.visibleRowCount = 3
        gbc.insets = Insets(0, 0, 8, 0)
        header.add(JScrollPane(patList).apply { preferredSize = Dimension(40, 72) }, gbc)

        gbc.insets = Insets(0, 0, 0, 0)
        header.add(progress, gbc)
        return header
    }

    private fun buildFooter(): JPanel {
        val footer = JPanel(BorderLayout())
        footer.border = BorderFactory.createEmptyBorder(4, 20, 12, 20)
        footer.add(
            JScrollPane(statusArea).apply {
                border = BorderFactory.createEmptyBorder()
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                preferredSize = Dimension(40, 88)
            },
            BorderLayout.NORTH
        )
        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        actions.add(syncButton)
        actions.add(importOnOpenBox)
        val close = JButton("Close")
        close.addActionListener { goAway() }
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        right.add(close)
        val buttons = JPanel(BorderLayout())
        buttons.add(actions, BorderLayout.WEST)
        buttons.add(right, BorderLayout.EAST)
        buttons.border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        footer.add(buttons, BorderLayout.CENTER)
        val note = JLabel(Main.THIRD_PARTY_DISCLAIMER)
        note.font = note.font.deriveFont(note.font.size2D - 1f)
        note.foreground = Color.GRAY
        note.border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        footer.add(note, BorderLayout.SOUTH)
        return footer
    }

    override fun getWindowName(): String = "Starling Bank"

    fun bringToFront() {
        isVisible = true
        toFront()
        requestFocus()
        if (settings?.tokens()?.isNotEmpty() == true) {
            refreshAccounts()
        }
    }

    override fun goneAway() {
        if (sources.isNotEmpty()) {
            saveMappings(null)
        }
        super.goneAway()
        onGoneAway?.invoke()
    }

    private fun typedToken(): String = String(tokenField.password).trim()

    private fun addToken() {
        val store = settings ?: run {
            setStatus("Open a data file to save the token.")
            return
        }
        val typed = typedToken()
        if (typed.isEmpty()) {
            setStatus("Paste a personal access token first.")
            return
        }
        if (busy || SyncService.inFlight) return
        val id = PatIndexCodec.newId()
        store.setPat(id, typed, "Starling", historyWalked = false)
        tokenField.text = ""
        reloadPatList()
        walkNewPat(id, typed)
    }

    private fun walkNewPat(id: String, token: String) {
        val store = settings ?: return
        busy = true
        setBusyButtons(false)
        showProgress("Validating…", 0.02)
        setStatus("Validating token and reading account history. This happens once.")
        MdNotify.log("validating new PAT")
        object : SwingWorker<com.moneydance.modules.features.starling.sync.LoadedSources, Progress>() {
            override fun doInBackground(): com.moneydance.modules.features.starling.sync.LoadedSources {
                return SourceLoader.walkHistory(token, store) { text, p ->
                    publish(Progress(text, p))
                }
            }

            override fun process(chunks: List<Progress>) {
                val last = chunks.last()
                showProgress(last.text, last.progress)
            }

            override fun done() {
                busy = false
                setBusyButtons(true)
                try {
                    val loaded = get()
                    store.updatePatMeta(id, description = loaded.holderLabel, historyWalked = true)
                    reloadPatList()
                    showSources(loaded.sources)
                    hideProgress()
                    setStatus("Token saved as ${loaded.holderLabel}. ${loaded.sources.size} accounts and Spaces.")
                    MdNotify.bar(mdGUI, "token saved", 1.0)
                } catch (e: Exception) {
                    store.removePat(id)
                    reloadPatList()
                    hideProgress()
                    val cause = e.cause ?: e
                    val msg = cause.message ?: "Could not reach Starling."
                    setStatus(msg)
                    MdNotify.log("validate failed: ${cause.javaClass.simpleName}: $msg", cause)
                    MdNotify.bar(mdGUI, msg, 0.0)
                }
            }
        }.execute()
    }

    private fun removeToken() {
        val store = settings ?: return
        val row = patList.selectedValue ?: run {
            setStatus("Select a token to remove.")
            return
        }
        store.removePat(row.id)
        reloadPatList()
        setStatus("Token removed.")
        if (store.tokens().isEmpty()) {
            sources = emptyList()
            mappingPanel.setSources(emptyList())
        }
    }

    private fun saveMappings(okMessage: String?): List<AccountMapping> {
        val fromTable = mappingPanel.collectMappings()
        val maps = AccountMapping.keepUnlisted(
            fromTable,
            settings?.mappings().orEmpty(),
            sources.map { it.id }.toSet()
        )
        settings?.setMappings(maps)
        mappingPanel.setSavedMappings(maps)
        if (okMessage != null) setStatus(okMessage)
        return maps
    }

    private fun refreshAccounts() {
        if (busy) return
        if (SyncService.inFlight) {
            setStatus("Import is running. The list will update when it finishes.")
            return
        }
        val store = settings ?: run {
            setStatus("Open a data file to continue.")
            return
        }
        val tokens = store.tokens()
        if (tokens.isEmpty()) {
            setStatus("Add a personal access token first.")
            return
        }
        busy = true
        setBusyButtons(false)
        setStatus("Loading accounts…")
        MdNotify.bar(mdGUI, "loading accounts", 0.15)
        object : SwingWorker<List<MappableSource>, Void>() {
            override fun doInBackground(): List<MappableSource> {
                val loaded = SourceLoader.loadActive(tokens, store.catalogue())
                return loaded.sources
            }

            override fun done() {
                busy = false
                setBusyButtons(true)
                try {
                    val list = get()
                    showSources(list)
                    val n = list.size
                    setStatus(if (n == 1) "1 account." else "$n accounts and Spaces.")
                    MdNotify.bar(mdGUI, if (n == 1) "1 account" else "$n accounts", 1.0)
                } catch (e: Exception) {
                    val cause = e.cause ?: e
                    val msg = cause.message ?: "Could not reach Starling."
                    setStatus(msg)
                    MdNotify.log("refresh failed: ${cause.javaClass.simpleName}: $msg", cause)
                    MdNotify.bar(mdGUI, msg, 0.0)
                }
            }
        }.execute()
    }

    private fun syncNow() {
        if (busy || SyncService.inFlight) return
        val store = settings
        val openBook = book
        if (store == null || openBook == null) {
            setStatus("Open a data file to import.")
            return
        }
        persistTypedTokenIfAny()
        if (store.tokens().isEmpty()) {
            setStatus("Add a personal access token first.")
            return
        }
        val maps = saveMappings("Saving mappings…")
        val current = if (sources.isNotEmpty()) sources else reconstructSources(maps)
        SyncService.start(
            book = openBook,
            settings = store,
            gui = mdGUI,
            mappings = maps,
            sources = current,
            onStatus = { showStatus(it) },
            onBusy = { setImportBusy(it) },
            onSources = { showSources(it) },
            onMappings = { showSavedMappings(it) }
        )
    }

    fun showStatus(text: String) {
        setStatus(text)
    }

    fun setImportBusy(running: Boolean) {
        busy = running
        setBusyButtons(!running)
    }

    private fun persistTypedTokenIfAny() {
        val store = settings ?: return
        val typed = typedToken()
        if (typed.isEmpty()) return
        store.setPat(PatIndexCodec.newId(), typed, "Personal access token", historyWalked = false)
        tokenField.text = ""
        reloadPatList()
    }

    fun showSavedMappings(mappings: List<AccountMapping>) {
        mappingPanel.setSavedMappings(mappings)
    }

    fun showSources(list: List<MappableSource>) {
        sources = list
        val saved = settings?.mappings().orEmpty()
        val named = saved.map { mapping ->
            val src = list.firstOrNull { it.id == mapping.sourceId } ?: return@map mapping
            mapping.withSource(src)
        }
        if (named != saved) settings?.setMappings(named)
        mappingPanel.setSavedMappings(named)
        mappingPanel.setSources(list)
    }

    private fun showSavedAccountRows() {
        val maps = settings?.mappings().orEmpty()
        if (maps.isEmpty() && settings?.catalogue().isNullOrEmpty()) return
        val fromCat = settings?.catalogue().orEmpty().map { e ->
            MappableSource(
                id = MappableSource.idFor(e.accountUid, e.categoryUid),
                accountUid = e.accountUid,
                categoryUid = e.categoryUid,
                name = e.name,
                parentName = e.parentName,
                currency = "GBP",
                kind = e.kind,
                archived = false
            )
        }
        if (fromCat.isNotEmpty()) showSources(fromCat)
    }

    private fun reconstructSources(maps: List<AccountMapping>): List<MappableSource> {
        if (sources.isNotEmpty()) return sources
        return maps.map { mapping ->
            val parts = mapping.sourceId.split(":", limit = 2)
            MappableSource(
                id = mapping.sourceId,
                accountUid = parts.getOrNull(0).orEmpty(),
                categoryUid = parts.getOrNull(1) ?: mapping.sourceId,
                name = mapping.sourceName ?: mapping.sourceId,
                parentName = mapping.parentName.orEmpty(),
                currency = "GBP",
                kind = SourceKind.MAIN,
                archived = false
            )
        }
    }

    private fun reloadPatList() {
        patModel.clear()
        settings?.pats()?.forEach { p ->
            val token = settings.patToken(p.id).orEmpty()
            val mask = if (token.isEmpty()) "" else "  ${ApiKeyMask.lastFour(token)}"
            patModel.addElement(PatRow(p.id, "${p.description.ifBlank { "Starling" }}$mask"))
        }
    }

    private fun setBusyButtons(enabled: Boolean) {
        val hasFile = settings != null && book != null
        val on = enabled && hasFile
        tokenField.isEnabled = hasFile
        addButton.isEnabled = on
        removeButton.isEnabled = on
        refreshButton.isEnabled = on
        syncButton.isEnabled = on
        importOnOpenBox.isEnabled = hasFile
    }

    private fun setStatus(text: String) {
        statusArea.text = text
        statusArea.caretPosition = 0
    }

    private fun showProgress(text: String, fraction: Double) {
        progress.isVisible = true
        progress.value = (fraction.coerceIn(0.0, 1.0) * 100).toInt()
        progress.string = text
        MdNotify.bar(mdGUI, text, fraction)
    }

    private fun hideProgress() {
        progress.isVisible = false
        progress.string = " "
    }

    private data class Progress(val text: String, val progress: Double)
    private data class PatRow(val id: String, val label: String) {
        override fun toString(): String = label
    }

    companion object {
        const val DOCS_URL: String =
            "https://github.com/dvdoug/moneydance-starling/blob/master/docs/user/setup.md"
    }
}
