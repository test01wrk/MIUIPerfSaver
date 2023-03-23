package com.rdstory.miuiperfsaver.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.rdstory.miuiperfsaver.Configuration
import com.rdstory.miuiperfsaver.Constants.LOG_TAG
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

    open class SuCmdButtonItem : SettingItem() {
        var button: String = ""
        var cmd: String = ""
        open fun onCmdResult(code: Int, stdout: String, stderr: String) {}
    }

    open class LargeTextItem : SettingItem() {
        open fun getText() = ""
        var allowCopy = false
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
                }.takeIf { it >= 0 } ?: if (items.isNotEmpty()) 0 else -1
                if (selectIndex >= 0) {
                    spinnerView.setSelection(selectIndex)
                }
            } else if (item is SuCmdButtonItem) {
                buttonView.visibility = View.VISIBLE
                buttonView.text = item.button
                buttonView.setOnClickListener {
                    Thread {
                        val process = Runtime.getRuntime().exec(arrayOf(
                            "su", "-c", item.cmd
                        ))
                        val exitCode = process.waitFor()
                        val stdout = String(process.inputStream.use { it.readBytes() }, Charsets.UTF_8).trim()
                        val stderr = String(process.errorStream.use { it.readBytes() }, Charsets.UTF_8).trim()
                        Log.i(LOG_TAG, "su cmd executed. exitCode: $exitCode, stdout: $stdout, stderr: $stderr")
                        buttonView.post {
                            val msg = if (exitCode == 0) stdout else stderr
                            Toast.makeText(buttonView.context, "[${exitCode}] $msg", Toast.LENGTH_SHORT).show()
                            item.onCmdResult(exitCode, stdout, stderr)
                        }
                    }.start()
                }
            } else if (item is LargeTextItem) {
                descView.setBackgroundColor(descView.resources.getColor(R.color.bg_large_text))
                descView.text = item.getText()
                val padding = descView.resources.getDimensionPixelSize(
                    R.dimen.large_text_item_padding) / (if (descView.length() > 0) 4 else 1)
                descView.setPaddingRelative(padding, padding, padding, padding)
                if (item.allowCopy) {
                    val context = descView.context
                    descView.setOnLongClickListener {
                        AlertDialog.Builder(context)
                            .setTitle(R.string.confirm_copy_text_to_clipboard)
                            .setPositiveButton(R.string.confirm) { dialog, _ ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("", descView.text))
                                Toast.makeText(context, R.string.copy_to_clipboard_success, Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                            .show()
                        return@setOnLongClickListener true
                    }
                } else {
                    descView.setOnLongClickListener(null)
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

    override fun getItemViewType(position: Int): Int {
        return settings[position]::class.java.hashCode()
    }

    override fun getItemCount(): Int {
        return settings.size
    }
}