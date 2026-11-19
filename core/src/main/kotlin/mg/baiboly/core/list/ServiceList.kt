package mg.baiboly.core.list

import mg.baiboly.core.ref.Passage

/** One entry of a service list. [isSide] is the "side verse" tag. */
data class ListItem(val passage: Passage, val isSide: Boolean = false)

/**
 * The service list as an immutable value: every edit returns a new state with
 * the current item kept sensible. No Android dependency.
 */
data class ServiceList(
    val id: Long = 0,
    val title: String = "",
    val date: String = "",
    val items: List<ListItem> = emptyList(),
    val current: Int = -1,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val currentItem: ListItem? get() = items.getOrNull(current)

    fun add(passages: List<Passage>, side: Boolean = false): ServiceList {
        if (passages.isEmpty()) return this
        val newItems = items + passages.map { ListItem(it, side) }
        return copy(items = newItems, current = if (current < 0) items.size else current)
    }

    fun select(index: Int): ServiceList = if (index in items.indices) copy(current = index) else this

    fun next(): ServiceList = if (current < items.lastIndex) copy(current = current + 1) else this
    fun previous(): ServiceList = if (current > 0) copy(current = current - 1) else this
    val hasNext: Boolean get() = current < items.lastIndex
    val hasPrevious: Boolean get() = current > 0

    fun toggleSide(index: Int): ServiceList =
        if (index !in items.indices) this
        else copy(items = items.toMutableList().also { it[index] = it[index].copy(isSide = !it[index].isSide) })

    /** Moves an item from one position to another; the current item follows its item. */
    fun move(from: Int, to: Int): ServiceList {
        if (from !in items.indices || to !in items.indices || from == to) return this
        val list = items.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        val newCurrent = when {
            current == from -> to
            from < current && to >= current -> current - 1
            from > current && to <= current -> current + 1
            else -> current
        }
        return copy(items = list, current = newCurrent)
    }

    fun moveEarlier(index: Int) = move(index, index - 1)
    fun moveLater(index: Int) = move(index, index + 1)

    fun delete(index: Int): ServiceList {
        if (index !in items.indices) return this
        val list = items.toMutableList().also { it.removeAt(index) }
        val newCurrent = when {
            list.isEmpty() -> -1
            index < current -> current - 1
            index == current -> minOf(current, list.lastIndex)
            else -> current
        }
        return copy(items = list, current = newCurrent)
    }
}
