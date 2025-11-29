package io.nekohasekai.sfa.ui.dashboard

import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.OutboundGroupItem
import io.nekohasekai.libbox.OutboundGroupItemIterator

data class Group(
    val tag: String,
    val type: String,
    val selectable: Boolean,
    var selected: String,
    var isExpand: Boolean,
    var items: List<GroupItem>,
) {
    constructor(item: OutboundGroup) : this(
        item.tag,
        item.type,
        item.selectable,
        item.selected,
        item.isExpand,
        sortItems(item.items.toList().mapIndexed { index, outboundItem ->
            GroupItem(outboundItem, index)
        }),
    )

    companion object {
        private fun sortItems(items: List<GroupItem>): List<GroupItem> {
            return items.sortedWith(
                compareBy<GroupItem> { if (it.hasUrlTestResult) 0 else 1 }
                    .thenBy {
                        if (it.hasUrlTestResult) it.urlTestDelay else it.subscriptionOrder
                    }
            )
        }
    }
}

data class GroupItem(
    val tag: String,
    val type: String,
    val urlTestTime: Long,
    val urlTestDelay: Int,
    val subscriptionOrder: Int,
) {
    constructor(item: OutboundGroupItem, subscriptionOrder: Int) : this(
        item.tag,
        item.type,
        item.urlTestTime,
        item.urlTestDelay,
        subscriptionOrder,
    )

    val hasUrlTestResult: Boolean get() = urlTestTime > 0
}

internal fun OutboundGroupItemIterator.toList(): List<OutboundGroupItem> {
    val list = mutableListOf<OutboundGroupItem>()
    while (hasNext()) {
        list.add(next())
    }
    return list
}
