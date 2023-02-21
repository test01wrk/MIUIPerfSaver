package com.rdstory.miuiperfsaver.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.rdstory.miuiperfsaver.Configuration
import com.rdstory.miuiperfsaver.Constants.CLEAR_JOYOSE_CMD
import com.rdstory.miuiperfsaver.Constants.JOYOSE_ACTIVITY
import com.rdstory.miuiperfsaver.Constants.JOYOSE_PKG
import com.rdstory.miuiperfsaver.Constants.JOYOSE_SERVICE
import com.rdstory.miuiperfsaver.Constants.START_JOYOSE_CMD
import com.rdstory.miuiperfsaver.Constants.START_JOYOSE_SERVICE_CMD
import com.rdstory.miuiperfsaver.Constants.STOP_JOYOSE_SERVICE_CMD
import com.rdstory.miuiperfsaver.JoyoseProfileRule
import com.rdstory.miuiperfsaver.R
import com.rdstory.miuiperfsaver.adapters.JoyoseSettingAdapter
import org.json.JSONObject

class JoyoseFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root: View = inflater.inflate(R.layout.fragment_apps, container, false)
        val packages: RecyclerView = root.findViewById(R.id.packages)
        packages.adapter = JoyoseSettingAdapter(getSettingItems())
        Configuration.setJoyoseProfileCallback {
            root.post {
                val adapter = packages.adapter ?: return@post
                adapter.notifyItemChanged(adapter.itemCount - 1)
            }
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Configuration.setJoyoseProfileCallback(null)
    }

    private fun getSettingItems(): List<JoyoseSettingAdapter.SettingItem> {
        val list = arrayListOf<JoyoseSettingAdapter.SettingItem>(
            JoyoseSettingAdapter.ProfileRuleItem().apply {
                title = getString(R.string.joyose_profile_process_rule)
                desc = getString(R.string.joyose_profile_process_rule_desc)
                selections = arrayListOf(
                    Pair(JoyoseProfileRule.BLOCK_ALL, getString(R.string.joyose_profile_process_rule_block_all)),
                    Pair(JoyoseProfileRule.RM_APP_LIST, getString(R.string.joyose_profile_process_rule_rm_app_list)),
                    Pair(JoyoseProfileRule.RM_APP_DFPS, getString(R.string.joyose_profile_process_rule_rm_app_dfps)),
                    Pair(JoyoseProfileRule.MOD_APP_DFPS, getString(R.string.joyose_profile_process_rule_mod_app_dfps)),
                    Pair(JoyoseProfileRule.KEEP_ALL, getString(R.string.joyose_profile_process_rule_keep_all))
                )
            }
        )
        list.add(object : JoyoseSettingAdapter.SuCmdButtonItem() {
            override fun onCmdResult(code: Int, stdout: String, stderr: String) {
                Configuration.resetJoyoseProfile()
            }
        }.apply {
            title = getString(R.string.joyose_clear_application_data)
            desc = getString(R.string.joyose_su_cmd_desc, CLEAR_JOYOSE_CMD)
            button = getString(R.string.clear_data)
            cmd = CLEAR_JOYOSE_CMD
        })
        requireContext().packageManager.resolveService(Intent().apply {
            component = ComponentName.createRelative(JOYOSE_PKG, JOYOSE_SERVICE)
        }, 0)?.let {
            list.add(JoyoseSettingAdapter.SuCmdButtonItem().apply {
                title = getString(R.string.joyose_start_service)
                desc = getString(R.string.joyose_su_cmd_desc, START_JOYOSE_SERVICE_CMD)
                button = getString(R.string.start_service)
                cmd = START_JOYOSE_SERVICE_CMD
            })
            list.add(JoyoseSettingAdapter.SuCmdButtonItem().apply {
                title = getString(R.string.joyose_stop_service)
                desc = getString(R.string.joyose_su_cmd_desc, STOP_JOYOSE_SERVICE_CMD)
                button = getString(R.string.stop_service)
                cmd = STOP_JOYOSE_SERVICE_CMD
            })
        }
        requireContext().packageManager.resolveActivity(Intent().apply {
            component = ComponentName.createRelative(JOYOSE_PKG, JOYOSE_ACTIVITY)
        }, 0)?.let {
            list.add(JoyoseSettingAdapter.SuCmdButtonItem().apply {
                title = getString(R.string.joyose_open_local_settings)
                desc = getString(R.string.joyose_su_cmd_desc, START_JOYOSE_CMD)
                button = getString(R.string.open)
                cmd = START_JOYOSE_CMD
            })
        }
        list.add(object : JoyoseSettingAdapter.LargeTextItem() {
            override fun getText(): String {
                return Configuration.getJoyoseProfile().takeIf { it.isNotEmpty() }
                    ?.let { JSONObject(it).toString(2) }
                    ?: getString(R.string.joyose_profile_content_empty)
            }
        }.apply {
            title = getString(R.string.joyose_profile_content)
        })
        return list
    }
}