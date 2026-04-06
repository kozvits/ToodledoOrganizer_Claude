package com.kozvits.toodledo.presentation.ui.tasklist

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.kozvits.toodledo.R
import com.kozvits.toodledo.databinding.FragmentTaskListBinding
import com.kozvits.toodledo.presentation.adapter.TaskAdapter
import com.kozvits.toodledo.presentation.ui.TaskFilter
import com.kozvits.toodledo.presentation.viewmodel.TaskListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TaskListFragment : Fragment() {

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TaskListViewModel by viewModels()
    private lateinit var taskAdapter: TaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        setupMenu()
        setupSwipeRefresh()
        observeViewModel()
    }

    private fun navigateToEdit(taskId: Long) {
        val bundle = bundleOf("taskId" to taskId)
        findNavController().navigate(R.id.editTaskFragment, bundle)
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onTaskClick = { task -> navigateToEdit(task.id) },
            onCheckClick = { task -> viewModel.completeTask(task.id) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskAdapter
        }

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                t: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val task = taskAdapter.getTaskAt(vh.adapterPosition)
                if (dir == ItemTouchHelper.RIGHT) {
                    viewModel.completeTask(task.id)
                    Snackbar.make(binding.root, R.string.task_completed, Snackbar.LENGTH_SHORT).show()
                } else {
                    viewModel.deleteTask(task.id)
                    Snackbar.make(binding.root, R.string.task_deleted, Snackbar.LENGTH_SHORT).show()
                }
            }
        }).attachToRecyclerView(binding.recyclerView)
    }

    private fun setupFab() {
        binding.fabAddTask.setOnClickListener { navigateToEdit(0L) }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupMenu() {
        (requireActivity() as MenuHost).addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_task_list, menu)
                val searchItem = menu.findItem(R.id.action_search)
                val searchView = searchItem.actionView as SearchView
                searchView.queryHint = getString(R.string.search_hint)
                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(q: String?) = false
                    override fun onQueryTextChange(q: String?): Boolean {
                        viewModel.setSearch(q ?: "")
                        return true
                    }
                })
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_filter_folder -> { showFolderFilterDialog(); true }
                    R.id.action_filter_context -> { showContextFilterDialog(); true }
                    R.id.action_show_completed -> { viewModel.toggleShowCompleted(); true }
                    R.id.action_clear_filter -> { viewModel.setFilter(TaskFilter.All); true }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showFolderFilterDialog() {
        val folders = viewModel.folders.value
        if (folders.isEmpty()) return
        val names = folders.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.filter_by_folder)
            .setItems(names) { _, idx ->
                val f = folders[idx]
                viewModel.setFilter(TaskFilter.ByFolder(f.id, f.name))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showContextFilterDialog() {
        val contexts = viewModel.contexts.value
        if (contexts.isEmpty()) return
        val names = contexts.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.filter_by_context)
            .setItems(names) { _, idx ->
                val c = contexts[idx]
                viewModel.setFilter(TaskFilter.ByContext(c.id, c.name))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tasks.collect { tasks ->
                        taskAdapter.submitList(tasks)
                        binding.emptyState.visibility =
                            if (tasks.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.activeFilter.collect { filter ->
                        val label = when (filter) {
                            is TaskFilter.All -> getString(R.string.all_tasks)
                            is TaskFilter.ByFolder -> filter.name
                            is TaskFilter.ByContext -> filter.name
                        }
                        binding.filterChip.text = label
                        binding.filterChip.visibility =
                            if (filter is TaskFilter.All) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
