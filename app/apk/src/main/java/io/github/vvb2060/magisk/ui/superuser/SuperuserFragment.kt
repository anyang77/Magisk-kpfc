package io.github.vvb2060.magisk.ui.superuser

import android.os.Bundle
import android.view.View
import io.github.vvb2060.magisk.R
import io.github.vvb2060.magisk.arch.BaseFragment
import io.github.vvb2060.magisk.arch.viewModel
import io.github.vvb2060.magisk.databinding.FragmentSuperuserMd2Binding
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.addItemSpacing
import rikka.recyclerview.fixEdgeEffect
import io.github.vvb2060.magisk.core.R as CoreR

class SuperuserFragment : BaseFragment<FragmentSuperuserMd2Binding>() {

    override val layoutRes = R.layout.fragment_superuser_md2
    override val viewModel by viewModel<SuperuserViewModel>()

    override fun onStart() {
        super.onStart()
        activity?.title = resources.getString(CoreR.string.superuser)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Reference search views to prevent resource shrinking from removing them
        binding.searchLayout
        binding.searchInput

        binding.superuserList.apply {
            addEdgeSpacing(top = R.dimen.l_50, bottom = R.dimen.l1)
            addItemSpacing(R.dimen.l1, R.dimen.l_50, R.dimen.l1)
            fixEdgeEffect()
        }
    }

    override fun onPreBind(binding: FragmentSuperuserMd2Binding) {}

}
