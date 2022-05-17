package com.rdstory.miuiperfsaver.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.rdstory.miuiperfsaver.Configuration
import com.rdstory.miuiperfsaver.Constants.LOG_TAG
import com.rdstory.miuiperfsaver.Constants.START_JOYOSE_CMD
import com.rdstory.miuiperfsaver.JoyoseProfileRule
import com.rdstory.miuiperfsaver.R

class JoyoseSettingAdapter(private val settings: List<SettingItem>) : RecyclerView.Adapter<JoyoseSettingAdapter.ViewHolder>() {

    open class SettingItem {
        var title: String? = null
        var desc: String? = null
    }

    class ProfileRuleItem : SettingItem() {
        var selections: List<Pair<JoyoseProfileRule, String>>? = null
    }

    class GotoLocalSettingsButtonItem : SettingItem() {
        var button: String? = null
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: AppCompatTextView = itemView.findViewById(R.id.title)
        private val descView: AppCompatTextView = itemView.findViewById(R.id.description)
        private val buttonView: AppCompatButton = itemView.findViewById(R.id.button)
        private val spinnerView: AppCompatSpinner = itemView.findViewById(R.id.spinner)

        fun bind(item: SettingItem) {
            titleView.text = item.title
            descView.text = item.desc
            if (item is ProfileRuleItem) {
                buttonView.visibility = View.GONE
                spinnerView.visibility = View.VISIBLE
                val items = item.selections ?: emptyList()
                val rules = items.map { it.first }
                val ruleNames = items.map { it.second }
                spinnerView.adapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, ruleNames).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                spinnerView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        Configuration.setJoyoseProfileRule(rules[position])
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                val selectIndex = rules.indexOfFirst {
                    it.value == Configuration.joyoseProfileRule
                }.takeIf { it >= 0 }
                spinnerView.setSelection(selectIndex ?: 0)
            } else if (item is GotoLocalSettingsButtonItem) {
                buttonView.visibility = View.VISIBLE
                spinnerView.visibility = View.GONE
                buttonView.text = item.button
                buttonView.setOnClickListener {
                    Thread {
                        val process = Runtime.getRuntime().exec(arrayOf(
                            "su", "-c", START_JOYOSE_CMD
                        ))
                        process.waitFor()
                        val stdout = String(process.inputStream.readBytes(), Charsets.UTF_8)
                        val stderr = String(process.errorStream.readBytes(), Charsets.UTF_8)
                        Log.i(LOG_TAG, "start joyose settings. stdout: $stdout, stderr: $stderr")
                    }.start()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_joyose_setting, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(settings[position])
    }

    override fun getItemCount(): Int {
        return settings.size
    }
}