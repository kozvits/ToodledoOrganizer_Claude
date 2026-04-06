package com.kozvits.toodledo.presentation.ui.hotlist

import android.os.Bundle
import android.view.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kozvits.toodledo.R
import com.kozvits.toodledo.databinding.FragmentHotListBinding
import com.kozvits.toodledo.domain.model.SortField
import com.kozvits.toodledo.domain.model.SortOrder
import com.kozvits.toodledo.presentation.adapter.TaskAdapter
import com.kozvits.toodledo.presentation.viewmodel.HotListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HotListFragment : Fragment() {

    private var _binding: FragmentHotListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HotListViewModel by viewModels()
    private lateinit var taskAdapter: TaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHotListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSortControls()
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
    }

    private fun setupSortControls() {
        val fields = SortField.entries
        val fieldNames = fields.map { it.label }.toTypedArray()

        fun showSortDialog(title: String, onSelected: (SortField, SortOrder) -> Unit) {
            var idx = 0
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(fieldNames, 0) { _, i -> idx = i }
                .setNeutralButton("↓ DESC") { _, _ -> onSelected(fields[idx], SortOrder.DESC) }
                .setPositiveButton("↑ ASC")  { _, _ -> onSelected(fields[idx], SortOrder.ASC) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnSort1.setOnClickListener {
            showSortDialog(getString(R.string.sort_primary)) { field, order ->
                viewModel.setSort1(field, order)
                binding.btnSort1.text = "${field.label} ${if (order == SortOrder.DESC) "↓" else "↑"}"
            }
        }
        binding.btnSort2.setOnClickListener {
            showSortDialog(getString(R.string.sort_secondary)) { field, order ->
                viewModel.setSort2(field, order)
                binding.btnSort2.text = "${field.label} ${if (order == SortOrder.DESC) "↓" else "↑"}"
            }
        }
        binding.btnSort3.setOnClickListener {
            showSortDialog(getString(R.string.sort_tertiary)) { field, order ->
                viewModel.setSort3(field, order)
                binding.btnSort3.text = "${field.label} ${if (order == SortOrder.DESC) "↓" else "↑"}"
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hotList.collect { tasks ->
                    taskAdapter.submitList(tasks)
                    binding.emptyState.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvTaskCount.text = "${tasks.size} ${getString(R.string.tasks)}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
