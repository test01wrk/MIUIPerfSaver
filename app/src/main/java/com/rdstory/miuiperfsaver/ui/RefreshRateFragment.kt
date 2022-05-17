package com.rdstory.miuiperfsaver.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_SHOW_PERF_SAVED_FIRST
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_SHOW_SYSTEM
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_SORT_ORDER
import com.rdstory.miuiperfsaver.Constants.SORT_ORDER_INSTALL_TIME
import com.rdstory.miuiperfsaver.Constants.SORT_ORDER_LABEL
import com.rdstory.miuiperfsaver.Constants.SORT_ORDER_PACKAGE_NAME
import com.rdstory.miuiperfsaver.Constants.SORT_ORDER_UPDATE_TIME
import com.rdstory.miuiperfsaver.Constants.SETTINGS_SP_KEY
import com.rdstory.miuiperfsaver.R
import com.rdstory.miuiperfsaver.adapters.InstalledPackageAdapter
import com.rdstory.miuiperfsaver.viewmodels.AppsViewModel

class RefreshRateFragment : Fragment() {
    private lateinit var mAppsViewModel: AppsViewModel
    private lateinit var mSharedPreferences: SharedPreferences
    private lateinit var mAdapter: InstalledPackageAdapter
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        mSharedPreferences = requireContext().getSharedPreferences(SETTINGS_SP_KEY, Context.MODE_PRIVATE)
        mAppsViewModel = ViewModelProvider(requireActivity()).get(AppsViewModel::class.java)
        val root: View = inflater.inflate(R.layout.fragment_apps, container, false)
        val packages: RecyclerView = root.findViewById(R.id.packages)
        mAdapter = InstalledPackageAdapter(requireContext(), mSharedPreferences)
        packages.adapter = mAdapter
        mAppsViewModel.installedPackages.observe(
            viewLifecycleOwner,
            mAdapter::updateInstalledPackages
        )
        mAppsViewModel.updatePackageList(requireContext())
        return root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.apps, menu)
        val search = menu.findItem(R.id.action_search).actionView as SearchView
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                mAdapter.setFilterQuery(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                mAdapter.setFilterQuery(newText)
                return true
            }
        })
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        when (mSharedPreferences.getInt(PREF_KEY_SORT_ORDER, SORT_ORDER_LABEL)) {
            SORT_ORDER_LABEL -> menu.findItem(R.id.action_sort_label).isChecked = true
            SORT_ORDER_PACKAGE_NAME -> menu.findItem(R.id.action_sort_package_name).isChecked = true
            SORT_ORDER_INSTALL_TIME -> menu.findItem(R.id.action_sort_install_time).isChecked = true
            SORT_ORDER_UPDATE_TIME -> menu.findItem(R.id.action_sort_update_time).isChecked = true
        }
        menu.findItem(R.id.action_saved_first).isChecked =
            mSharedPreferences.getBoolean(PREF_KEY_SHOW_PERF_SAVED_FIRST, false)
        menu.findItem(R.id.action_show_system).isChecked =
            mSharedPreferences.getBoolean(PREF_KEY_SHOW_SYSTEM, false)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> {
                mAppsViewModel.updatePackageList(requireContext())
                return true
            }
            R.id.action_sort_label -> {
                mSharedPreferences.edit().putInt(PREF_KEY_SORT_ORDER, SORT_ORDER_LABEL).apply()
                requireActivity().invalidateOptionsMenu()
                return true
            }
            R.id.action_sort_package_name -> {
                mSharedPreferences.edit().putInt(PREF_KEY_SORT_ORDER, SORT_ORDER_PACKAGE_NAME)
                    .apply()
                requireActivity().invalidateOptionsMenu()
                return true
            }
            R.id.action_sort_install_time -> {
                mSharedPreferences.edit().putInt(PREF_KEY_SORT_ORDER, SORT_ORDER_INSTALL_TIME)
                    .apply()
                requireActivity().invalidateOptionsMenu()
                return true
            }
            R.id.action_sort_update_time -> {
                mSharedPreferences.edit().putInt(PREF_KEY_SORT_ORDER, SORT_ORDER_UPDATE_TIME)
                    .apply()
                requireActivity().invalidateOptionsMenu()
                return true
            }
            R.id.action_saved_first -> {
                mSharedPreferences.edit()
                    .putBoolean(
                        PREF_KEY_SHOW_PERF_SAVED_FIRST,
                        !mSharedPreferences.getBoolean(PREF_KEY_SHOW_PERF_SAVED_FIRST, false)
                    )
                    .apply()
                requireActivity().invalidateOptionsMenu()
                return true
            }
            R.id.action_show_system -> {
                mSharedPreferences.edit()
                    .putBoolean(
                        PREF_KEY_SHOW_SYSTEM,
                        !mSharedPreferences.getBoolean(PREF_KEY_SHOW_SYSTEM, false)
                    )
                    .apply()
                requireActivity().invalidateOptionsMenu()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}