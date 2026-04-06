package com.kozvits.toodledo.presentation.ui.edittask

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.kozvits.toodledo.R
import com.kozvits.toodledo.databinding.FragmentEditTaskBinding
import com.kozvits.toodledo.domain.model.*
import com.kozvits.toodledo.presentation.viewmodel.EditTaskViewModel
import com.kozvits.toodledo.util.formatDate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.*

@AndroidEntryPoint
class EditTaskFragment : Fragment() {

    private var _binding: FragmentEditTaskBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EditTaskViewModel by viewModels()

    /** taskId передаётся через Bundle (ключ "taskId"), 0 = новая задача */
    private val taskId: Long get() = arguments?.getLong("taskId", 0L) ?: 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupInputListeners()
        observeViewModel()
        viewModel.loadTask(taskId)
    }

    private fun setupMenu() {
        (requireActivity() as MenuHost).addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_edit_task, menu)
            }
            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_save -> { viewModel.save(); true }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupInputListeners() {
        binding.editTitle.doAfterTextChanged { viewModel.updateTitle(it.toString()) }
        binding.editNote.doAfterTextChanged { viewModel.updateNote(it.toString()) }
        binding.editTags.doAfterTextChanged { viewModel.updateTag(it.toString()) }

        binding.switchStar.setOnCheckedChangeListener { _, checked -> viewModel.updateStar(checked) }
        binding.switchHot.setOnCheckedChangeListener { _, checked -> viewModel.updateHot(checked) }

        // Priority spinner
        val priorities = Priority.entries.filter { it != Priority.TOP }
        binding.spinnerPriority.adapter = ArrayAdapter(
            requireContext(), R.layout.item_spinner_dark, priorities.map { it.label }
        )
        binding.spinnerPriority.setSelection(priorities.indexOfFirst { it == Priority.NONE }.coerceAtLeast(0))
        binding.spinnerPriority.onItemSelectedListener = simpleItemSelected { pos ->
            viewModel.updatePriority(priorities[pos])
        }

        // Status spinner
        val statuses = TaskStatus.entries
        binding.spinnerStatus.adapter = ArrayAdapter(
            requireContext(), R.layout.item_spinner_dark, statuses.map { it.label }
        )
        binding.spinnerStatus.onItemSelectedListener = simpleItemSelected { pos ->
            viewModel.updateStatus(statuses[pos])
        }

        // Repeat spinner
        val repeats = RepeatType.entries
        binding.spinnerRepeat.adapter = ArrayAdapter(
            requireContext(), R.layout.item_spinner_dark, repeats.map { it.label }
        )
        binding.spinnerRepeat.onItemSelectedListener = simpleItemSelected { pos ->
            viewModel.updateRepeat(repeats[pos])
        }

        binding.btnDueDate.setOnClickListener { showDatePicker { ts -> viewModel.updateDueDate(ts) } }
        binding.btnStartDate.setOnClickListener { showDatePicker { ts -> viewModel.updateStartDate(ts) } }
    }

    private fun simpleItemSelected(onSelected: (Int) -> Unit) =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) =
                onSelected(pos)
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

    private fun showDatePicker(onDate: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            cal.set(y, m, d, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
            onDate(cal.timeInMillis / 1000L)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun observeViewModel() {
        var suppressWatcher = false

        viewModel.task.observe(viewLifecycleOwner) { task ->
            suppressWatcher = true
            if (binding.editTitle.text.toString() != task.title) binding.editTitle.setText(task.title)
            if (binding.editNote.text.toString() != task.note) binding.editNote.setText(task.note)
            if (binding.editTags.text.toString() != task.tag) binding.editTags.setText(task.tag)
            binding.switchStar.isChecked = task.star
            binding.switchHot.isChecked = task.isHot
            binding.btnDueDate.text =
                if (task.dueDate > 0) formatDate(task.dueDate) else getString(R.string.no_date)
            binding.btnStartDate.text =
                if (task.startDate > 0) formatDate(task.startDate) else getString(R.string.no_date)
            suppressWatcher = false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.folders.collect { folders ->
                        val names = listOf(getString(R.string.no_folder)) + folders.map { it.name }
                        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner_dark, names)
                        binding.spinnerFolder.adapter = adapter
                        binding.spinnerFolder.onItemSelectedListener = simpleItemSelected { pos ->
                            if (pos == 0) viewModel.updateFolder(0L, "")
                            else {
                                val f = folders[pos - 1]
                                viewModel.updateFolder(f.id, f.name)
                            }
                        }
                    }
                }
                launch {
                    viewModel.contexts.collect { contexts ->
                        val names = listOf(getString(R.string.no_context)) + contexts.map { it.name }
                        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner_dark, names)
                        binding.spinnerContext.adapter = adapter
                        binding.spinnerContext.onItemSelectedListener = simpleItemSelected { pos ->
                            if (pos == 0) viewModel.updateContext(0L, "")
                            else {
                                val c = contexts[pos - 1]
                                viewModel.updateContext(c.id, c.name)
                            }
                        }
                    }
                }
            }
        }

        viewModel.saveComplete.observe(viewLifecycleOwner) { done ->
            if (done) {
                Snackbar.make(binding.root, R.string.task_saved, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
