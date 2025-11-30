package io.nekohasekai.sfa.ui.dashboard

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.databinding.FragmentDashboardGroupsBinding
import io.nekohasekai.sfa.databinding.ViewDashboardGroupBinding
import io.nekohasekai.sfa.databinding.ViewDashboardGroupItemBinding
import io.nekohasekai.sfa.databinding.ViewMajorSiteLatencyItemBinding
import io.nekohasekai.sfa.ktx.colorForURLTestDelay
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ktx.getAttrColor
import io.nekohasekai.sfa.ktx.text
import io.nekohasekai.sfa.ui.MainActivity
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class GroupsFragment : Fragment(), CommandClient.Handler {

    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var binding: FragmentDashboardGroupsBinding? = null
    private var adapter: Adapter? = null
    private var latencyAdapter: MajorSiteLatencyAdapter? = null
    private var latencyJob: Job? = null
    private val commandClient =
        CommandClient(lifecycleScope, CommandClient.ConnectionType.Groups, this)


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentDashboardGroupsBinding.inflate(inflater, container, false)
        this.binding = binding
        onCreate()
        return binding.root
    }

    private fun onCreate() {
        val activity = activity ?: return
        val binding = binding ?: return
        setupLatencySection(binding)
        adapter = Adapter()
        binding.container.adapter = adapter
        binding.container.layoutManager = LinearLayoutManager(requireContext())
        activity.serviceStatus.observe(viewLifecycleOwner) {
            if (it == Status.Started) {
                commandClient.connect()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        commandClient.disconnect()
        latencyJob?.cancel()
        latencyAdapter = null
    }

    private fun setupLatencySection(binding: FragmentDashboardGroupsBinding) {
        val adapter = MajorSiteLatencyAdapter()
        latencyAdapter = adapter
        binding.latencyList.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.latencyList.adapter = adapter
        adapter.setItems(MajorSiteCatalog.sites.map { MajorSiteLatencyState(it) })
    }

    private var displayed = false
    private fun updateDisplayed(newValue: Boolean) {
        val binding = binding ?: return
        if (displayed != newValue) {
            displayed = newValue
            binding.statusText.isVisible = !displayed
            binding.container.isVisible = displayed
            binding.latencyCard.isVisible = displayed
            if (displayed) {
                startMajorSiteLatencyTesting()
            } else {
                stopMajorSiteLatencyTesting()
            }
        }
    }

    private fun startMajorSiteLatencyTesting() {
        val adapter = latencyAdapter ?: return
        latencyJob?.cancel()
        val initialStates = MajorSiteCatalog.sites.map { MajorSiteLatencyState(it, LatencyStatus.Testing) }
        adapter.setItems(initialStates)
        latencyJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            MajorSiteCatalog.sites.forEachIndexed { index, site ->
                val result = MajorSiteLatencyTester.measure(site)
                withContext(Dispatchers.Main) {
                    adapter.updateItem(
                        index,
                        MajorSiteLatencyState(
                            site = site,
                            status = if (result.success) LatencyStatus.Success else LatencyStatus.Failure,
                            latencyMs = result.latencyMs
                        )
                    )
                }
            }
        }
    }

    private fun stopMajorSiteLatencyTesting() {
        latencyJob?.cancel()
        latencyJob = null
        latencyAdapter?.setItems(MajorSiteCatalog.sites.map { MajorSiteLatencyState(it) })
    }

    override fun onConnected() {
        lifecycleScope.launch(Dispatchers.Main) {
            updateDisplayed(true)
        }
    }

    override fun onDisconnected() {
        lifecycleScope.launch(Dispatchers.Main) {
            updateDisplayed(false)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun updateGroups(newGroups: MutableList<OutboundGroup>) {
        val adapter = adapter ?: return
        activity?.runOnUiThread {
            updateDisplayed(newGroups.isNotEmpty())
            adapter.setGroups(newGroups.map(::Group))
        }
    }

    private class Adapter : RecyclerView.Adapter<GroupView>() {

        private lateinit var groups: MutableList<Group>

        @SuppressLint("NotifyDataSetChanged")
        fun setGroups(newGroups: List<Group>) {
            if (!::groups.isInitialized || groups.size != newGroups.size) {
                groups = newGroups.toMutableList()
                notifyDataSetChanged()
            } else {
                newGroups.forEachIndexed { index, group ->
                    if (this.groups[index] != group) {
                        this.groups[index] = group
                        notifyItemChanged(index)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupView {
            return GroupView(
                ViewDashboardGroupBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
            )
        }

        override fun getItemCount(): Int {
            if (!::groups.isInitialized) {
                return 0
            }
            return groups.size
        }

        override fun onBindViewHolder(holder: GroupView, position: Int) {
            holder.bind(groups[position])
        }
    }

    private class GroupView(val binding: ViewDashboardGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private lateinit var group: Group
        private lateinit var items: List<GroupItem>
        private lateinit var adapter: ItemAdapter
        private var textWatcher: TextWatcher? = null

        @OptIn(DelicateCoroutinesApi::class)
        @SuppressLint("NotifyDataSetChanged")
        fun bind(group: Group) {
            this.group = group
            binding.groupName.text = group.tag
            binding.groupType.text = Libbox.proxyDisplayType(group.type)
            binding.urlTestButton.setOnClickListener {
                GlobalScope.launch {
                    runCatching {
                        Libbox.newStandaloneCommandClient().urlTest(group.tag)
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            binding.root.context.errorDialogBuilder(it).show()
                        }
                    }
                }
            }
            items = group.items
            if (!::adapter.isInitialized) {
                adapter = ItemAdapter(this, group, items.toMutableList())
                binding.itemList.adapter = adapter
                (binding.itemList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations =
                    false
                binding.itemList.layoutManager = GridLayoutManager(binding.root.context, 2)
            } else {
                adapter.group = group
                adapter.setItems(items)
            }
            updateExpand()
        }

        @OptIn(DelicateCoroutinesApi::class)
        private fun updateExpand(isExpand: Boolean? = null) {
            val newExpandStatus = isExpand ?: group.isExpand
            if (isExpand != null) {
                GlobalScope.launch {
                    runCatching {
                        Libbox.newStandaloneCommandClient().setGroupExpand(group.tag, isExpand)
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            binding.root.context.errorDialogBuilder(it).show()
                        }
                    }
                }
            }
            binding.itemList.isVisible = newExpandStatus
            binding.groupSelected.isVisible = !newExpandStatus
            val textView = (binding.groupSelected.editText as MaterialAutoCompleteTextView)
            if (textWatcher != null) {
                textView.removeTextChangedListener(textWatcher)
            }
            if (!newExpandStatus) {
                binding.groupSelected.text = group.selected
                binding.groupSelected.isEnabled = group.selectable
                if (group.selectable) {
                    textView.setSimpleItems(group.items.toList().map { it.tag }.toTypedArray())
                    textWatcher = textView.addTextChangedListener {
                        val selected = textView.text.toString()
                        if (selected != group.selected) {
                            updateSelected(group, selected)
                        }
                        GlobalScope.launch {
                            runCatching {
                                Libbox.newStandaloneCommandClient()
                                    .selectOutbound(group.tag, selected)
                            }.onFailure {
                                withContext(Dispatchers.Main) {
                                    binding.root.context.errorDialogBuilder(it).show()
                                }
                            }
                        }
                    }
                }
            }
            if (newExpandStatus) {
                binding.urlTestButton.isVisible = true
                binding.expandButton.setImageResource(R.drawable.ic_expand_less_24)
            } else {
                binding.urlTestButton.isVisible = false
                binding.expandButton.setImageResource(R.drawable.ic_expand_more_24)
            }
            binding.expandButton.setOnClickListener {
                updateExpand(!binding.itemList.isVisible)
            }
        }

        fun updateSelected(group: Group, itemTag: String) {
            val oldSelected = items.indexOfFirst { it.tag == group.selected }
            group.selected = itemTag
            if (oldSelected != -1) {
                adapter.notifyItemChanged(oldSelected)
            }
        }
    }

    private class ItemAdapter(
        val groupView: GroupView,
        var group: Group,
        private var items: MutableList<GroupItem> = mutableListOf()
    ) :
        RecyclerView.Adapter<ItemGroupView>() {

        @SuppressLint("NotifyDataSetChanged")
        fun setItems(newItems: List<GroupItem>) {
            if (items.size != newItems.size) {
                items = newItems.toMutableList()
                notifyDataSetChanged()
            } else {
                newItems.forEachIndexed { index, item ->
                    if (items[index] != item) {
                        items[index] = item
                        notifyItemChanged(index)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemGroupView {
            return ItemGroupView(
                ViewDashboardGroupItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onBindViewHolder(holder: ItemGroupView, position: Int) {
            holder.bind(groupView, group, items[position])
        }
    }

    private class ItemGroupView(val binding: ViewDashboardGroupItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @OptIn(DelicateCoroutinesApi::class)
        fun bind(groupView: GroupView, group: Group, item: GroupItem) {
            if (group.selectable) {
                binding.itemCard.setOnClickListener {
                    binding.selectedView.isVisible = true
                    groupView.updateSelected(group, item.tag)
                    GlobalScope.launch {
                        runCatching {
                            Libbox.newStandaloneCommandClient().selectOutbound(group.tag, item.tag)
                        }.onFailure {
                            withContext(Dispatchers.Main) {
                                binding.root.context.errorDialogBuilder("select outbound: ${it.localizedMessage}")
                                    .show()
                            }
                        }
                    }
                }
            }
            binding.selectedView.isInvisible = group.selected != item.tag
            binding.itemName.text = item.tag
            binding.itemType.text = Libbox.proxyDisplayType(item.type)
            binding.itemStatus.isVisible = item.urlTestTime > 0
            if (item.urlTestTime > 0) {
                binding.itemStatus.text = "${item.urlTestDelay}ms"
                binding.itemStatus.setTextColor(
                    colorForURLTestDelay(
                        binding.root.context,
                        item.urlTestDelay
                    )
                )
            }
        }
    }

    private class MajorSiteLatencyAdapter :
        RecyclerView.Adapter<MajorSiteLatencyView>() {

        private val items = mutableListOf<MajorSiteLatencyState>()

        fun setItems(newItems: List<MajorSiteLatencyState>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun updateItem(index: Int, newItem: MajorSiteLatencyState) {
            if (index in items.indices) {
                items[index] = newItem
                notifyItemChanged(index)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MajorSiteLatencyView {
            val binding = ViewMajorSiteLatencyItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return MajorSiteLatencyView(binding)
        }

        override fun onBindViewHolder(holder: MajorSiteLatencyView, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    private class MajorSiteLatencyView(
        private val binding: ViewMajorSiteLatencyItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(state: MajorSiteLatencyState) {
            val context = binding.root.context
            binding.siteIcon.setImageResource(state.site.iconRes)
            binding.siteName.setText(state.site.nameRes)
            binding.siteRegion.setText(state.site.region.labelRes)
            when (state.status) {
                LatencyStatus.Idle -> {
                    binding.siteLatency.text =
                        context.getString(R.string.major_site_latency_pending)
                    binding.siteLatency.setTextColor(
                        context.getAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
                    )
                }

                LatencyStatus.Testing -> {
                    binding.siteLatency.text =
                        context.getString(R.string.major_site_latency_testing)
                    binding.siteLatency.setTextColor(
                        context.getAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
                    )
                }

                LatencyStatus.Failure -> {
                    binding.siteLatency.text =
                        context.getString(R.string.major_site_latency_failed)
                    binding.siteLatency.setTextColor(
                        context.getAttrColor(com.google.android.material.R.attr.colorError)
                    )
                }

                LatencyStatus.Success -> {
                    val latency = state.latencyMs ?: 0
                    binding.siteLatency.text =
                        context.getString(R.string.major_site_latency_value, latency)
                    binding.siteLatency.setTextColor(
                        colorForURLTestDelay(context, latency)
                    )
                }
            }
        }
    }
}
