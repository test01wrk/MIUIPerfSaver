package com.rdstory.miuiperfsaver.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.rdstory.miuiperfsaver.Constants.JOYOSE_ACTIVITY
import com.rdstory.miuiperfsaver.Constants.JOYOSE_PKG
import com.rdstory.miuiperfsaver.Constants.SETTINGS_SP_KEY
import com.rdstory.miuiperfsaver.Constants.START_JOYOSE_CMD
import com.rdstory.miuiperfsaver.JoyoseProfileRule
import com.rdstory.miuiperfsaver.R
import com.rdstory.miuiperfsaver.adapters.JoyoseSettingAdapter

class JoyoseFragment : Fragment() {
    private lateinit var mSharedPreferences: SharedPreferences
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        mSharedPreferences = requireContext().getSharedPreferences(SETTINGS_SP_KEY, Context.MODE_PRIVATE)
        val root: View = inflater.inflate(R.layout.fragment_apps, container, false)
        val packages: RecyclerView = root.findViewById(R.id.packages)
        packages.adapter = JoyoseSettingAdapter(getSettingItems())
        return root
    }

    private fun getSettingItems(): List<JoyoseSettingAdapter.SettingItem> {
        val list = arrayListOf<JoyoseSettingAdapter.SettingItem>(
            JoyoseSettingAdapter.ProfileRuleItem().apply {
                title = getString(R.string.joyose_profile_process_rule)
                desc = getString(R.string.joyose_profile_process_rule_desc)
                selections = arrayListOf(
                    Pair(JoyoseProfileRule.BLOCK, getString(R.string.joyose_profile_process_rule_block)),
                    Pair(JoyoseProfileRule.RM_APP_LIST, getString(R.string.joyose_profile_process_rule_rm_app_list)),
                    Pair(JoyoseProfileRule.RM_APP_DFPS, getString(R.string.joyose_profile_process_rule_rm_app_dfps))
                )
            }
        )
        val resolveInfo = requireContext().packageManager.resolveActivity(Intent().apply {
            component = ComponentName.createRelative(JOYOSE_PKG, JOYOSE_ACTIVITY)
        }, 0)
        if (resolveInfo != null) {
            list.add(JoyoseSettingAdapter.GotoLocalSettingsButtonItem().apply {
                title = getString(R.string.joyose_open_local_settings)
                desc = getString(R.string.joyose_open_local_settings_desc, START_JOYOSE_CMD)
                button = getString(R.string.open)
            })
        }
        return list
    }
}