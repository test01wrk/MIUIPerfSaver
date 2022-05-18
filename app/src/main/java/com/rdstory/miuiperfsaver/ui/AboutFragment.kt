package com.rdstory.miuiperfsaver.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.fragment.app.Fragment
import com.rdstory.miuiperfsaver.BuildConfig.VERSION_NAME
import com.rdstory.miuiperfsaver.MainApplication
import com.rdstory.miuiperfsaver.R

class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root: View = inflater.inflate(R.layout.fragment_about, container, false)
        val button = root.findViewById<Button>(R.id.github)
        button.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.url_project_page))
                )
            )
        }
        val checkBox = root.findViewById<AppCompatCheckBox>(R.id.checkbox_show_icon)
        val pm = checkBox.context.packageManager
        val mainActivity = ComponentName(checkBox.context, MainActivity::class.java)
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            pm.setComponentEnabledSetting(
                mainActivity,
                if (isChecked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        checkBox.isChecked = pm.getComponentEnabledSetting(mainActivity) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val version: TextView = root.findViewById(R.id.version)
        version.text = getString(R.string.app_version, VERSION_NAME)
        val xposed: Int = MainApplication.getActiveXposedVersion()
        if (xposed != -1) {
            val text: TextView = root.findViewById(R.id.xposed)
            text.text = getString(R.string.xposed_version, xposed)
        }
        return root
    }
}