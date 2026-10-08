package com.termoak.app.data

/**
 * Rules of the import and export screens (the desktop's import dialog and
 * iOS's): what happens to each host of a file, the CSV column mapping and
 * the export passphrase. Pure logic (JVM tests); the engine reads and saves
 * (previewImport, applyImport, exportHosts).
 */
object ImportPlan {
    /** Why a host of the file is a duplicate (same address, port and user). */
    sealed class Dup {
        data object None : Dup()
        /** Of a host already in the target, with its name. */
        data class Existing(val label: String) : Dup()
        /** Of an earlier host of the same file. */
        data object InFile : Dup()
    }

    /** What to do with duplicates (the engine's DuplicatePolicy). */
    enum class Policy { SKIP, UPDATE, COPY }

    /** What happens to a host of the file. */
    sealed class Status {
        data object New : Status()
        /** The user unchecked it. */
        data object Unchecked : Status()
        data class ExistingSkipped(val name: String) : Status()
        data class Updates(val name: String) : Status()
        data class CopyOf(val name: String) : Status()
        data object RepeatSkipped : Status()
        data object RepeatCopy : Status()

        val imports: Boolean get() = this is New || this is Updates || this is CopyOf || this is RepeatCopy
    }

    /** The engine's plan: unchecked hosts are left out, duplicates follow the policy (a repeat inside the file is only created with "copy"). */
    fun status(dup: Dup, policy: Policy, included: Boolean): Status {
        if (!included) return Status.Unchecked
        return when (dup) {
            Dup.None -> Status.New
            is Dup.Existing -> when (policy) {
                Policy.SKIP -> Status.ExistingSkipped(dup.label)
                Policy.UPDATE -> Status.Updates(dup.label)
                Policy.COPY -> Status.CopyOf(dup.label)
            }
            Dup.InFile -> if (policy == Policy.COPY) Status.RepeatCopy else Status.RepeatSkipped
        }
    }

    /** How many hosts the Import button imports. */
    fun importCount(statuses: List<Status>): Int = statuses.count { it.imports }

    // ----- CSV columns (field name → column) -----

    /** The field a column feeds (`null`: none). */
    fun fieldOfColumn(column: Int, mapping: List<Pair<String, Int>>): String? = mapping.firstOrNull { it.second == column }?.first

    /** The mapping after giving [column] the field [field] (`null`: none): a field feeds one column at most, and a column one field. */
    fun assign(field: String?, column: Int, mapping: List<Pair<String, Int>>): List<Pair<String, Int>> {
        val out = mapping.filter { it.second != column && it.first != field }.toMutableList()
        if (field != null) out += field to column
        return out.sortedBy { it.second }
    }

    /** Without an address column nothing can be imported. */
    fun hasAddress(mapping: List<Pair<String, Int>>): Boolean = mapping.any { it.first == "address" }

    /** The example value of a column: the first data row of the sample (which starts with the header row when there is one). */
    fun example(column: Int, sample: List<List<String>>, hasHeader: Boolean): String? {
        val rows = if (hasHeader) sample.drop(1) else sample
        for (row in rows) {
            val v = row.getOrNull(column)?.trim()
            if (!v.isNullOrEmpty()) return v
        }
        return null
    }

    // ----- Export -----

    enum class PassphraseProblem { SHORT, MISMATCH }

    /** An export with secrets needs a passphrase of 8 characters or more, typed twice the same. */
    fun passphraseProblem(first: String, second: String): PassphraseProblem? = when {
        first.length < 8 -> PassphraseProblem.SHORT
        first != second -> PassphraseProblem.MISMATCH
        else -> null
    }
}
