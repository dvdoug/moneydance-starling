package com.moneydance.modules.features.starling.ui

import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.AccountBook
import com.moneydance.modules.features.starling.api.MappableSource
import com.moneydance.modules.features.starling.settings.AccountMapping
import com.moneydance.modules.features.starling.sync.MdAccess
import com.moneydance.modules.features.starling.sync.MdAccounts
import java.awt.BorderLayout
import javax.swing.DefaultCellEditor
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

class AccountMappingPanel(
    book: AccountBook?
) : JPanel(BorderLayout(0, 8)) {

    private val mdChoices: List<AccountChoice> = listOf(AccountChoice(null)) +
        (book?.let { MdAccounts.listMappable(it) } ?: emptyList()).map { AccountChoice(it) }

    private val model = MappingTableModel(mdChoices)
    private val table = JTable(model)
    private var saved: List<AccountMapping> = emptyList()

    init {
        table.rowHeight = 26
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.fillsViewportHeight = true
        table.tableHeader.reorderingAllowed = false
        table.putClientProperty("terminateEditOnFocusLost", true)
        table.columnModel.getColumn(1).cellEditor = DefaultCellEditor(JComboBox(mdChoices.toTypedArray()))
        table.columnModel.getColumn(0).preferredWidth = 300
        table.columnModel.getColumn(1).preferredWidth = 240
        table.columnModel.getColumn(2).preferredWidth = 100
        add(JScrollPane(table), BorderLayout.CENTER)
        add(
            JLabel("<html>Choose <b>— not mapped —</b> to skip that Space. Spending from an unmapped Spending Space is imported into the current account.</html>"),
            BorderLayout.SOUTH
        )
    }

    fun setSavedMappings(mappings: List<AccountMapping>) {
        saved = mappings
    }

    fun setSources(sources: List<MappableSource>) {
        model.setSources(sources, saved)
    }

    fun collectMappings(): List<AccountMapping> {
        if (table.isEditing) table.cellEditor.stopCellEditing()
        return model.collect(saved)
    }

    private class MappingTableModel(
        private val mdChoices: List<AccountChoice>
    ) : AbstractTableModel() {
        private val columns = arrayOf("Starling account", "Import into", "From")
        private val rows = mutableListOf<MappingRow>()

        fun setSources(sources: List<MappableSource>, saved: List<AccountMapping>) {
            rows.clear()
            sources.forEach { src ->
                val existing = saved.firstOrNull { it.sourceId == src.id }
                rows.add(
                    MappingRow(
                        source = src,
                        mdUuid = existing?.moneydanceAccountUuid,
                        startDate = existing?.syncStartDate ?: AccountMapping.defaultStartDate()
                    )
                )
            }
            fireTableDataChanged()
        }

        fun collect(saved: List<AccountMapping>): List<AccountMapping> {
            return rows.mapNotNull { row ->
                val uuid = row.mdUuid ?: return@mapNotNull null
                AccountMapping(
                    sourceId = row.source.id,
                    moneydanceAccountUuid = uuid,
                    syncStartDate = row.startDate?.trim()?.ifBlank { null },
                    lastPostedDate = saved.firstOrNull { it.sourceId == row.source.id }?.lastPostedDate,
                    sourceName = row.source.name,
                    parentName = row.source.parentName.takeIf { it.isNotBlank() }
                )
            }
        }

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex > 0

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.source.displayName
                1 -> choiceFor(row.mdUuid)
                else -> row.startDate.orEmpty()
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val row = rows[rowIndex]
            when (columnIndex) {
                1 -> row.mdUuid = (aValue as? AccountChoice)?.account?.let { MdAccess.uuid(it) }
                2 -> row.startDate = aValue?.toString()
            }
            fireTableCellUpdated(rowIndex, columnIndex)
        }

        private fun choiceFor(uuid: String?): AccountChoice {
            if (uuid == null) return mdChoices.first()
            return mdChoices.firstOrNull { it.account != null && MdAccess.uuid(it.account) == uuid }
                ?: mdChoices.first()
        }
    }

    private data class MappingRow(
        val source: MappableSource,
        var mdUuid: String?,
        var startDate: String?
    )

    class AccountChoice(val account: Account?) {
        override fun toString(): String = account?.let { MdAccess.fullAccountName(it) } ?: "— not mapped —"

        override fun equals(other: Any?): Boolean {
            if (other !is AccountChoice) return false
            val a = account
            val b = other.account
            return when {
                a == null && b == null -> true
                a != null && b != null -> MdAccess.uuid(a) == MdAccess.uuid(b)
                else -> false
            }
        }

        override fun hashCode(): Int = account?.let { MdAccess.uuid(it) }?.hashCode() ?: 0
    }
}
