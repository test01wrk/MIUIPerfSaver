package com.rdstory.miuiperfsaver.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.AppCompatTextView
import androidx.collection.SparseArrayCompat
import androidx.recyclerview.widget.RecyclerView
import com.rdstory.miuiperfsaver.Configuration
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_SHOW_PERF_SAVED_FIRST
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_SHOW_SYSTEM
import com.rdstory.miuiperfsaver.Constants.PREF_KEY_SORT_ORDER
import com.rdstory.miuiperfsaver.Constants.SORT_ORDER_INSTALL_TIME
import com.rdstory.miuiperfsaver.Constants.SORT_ORDER_LABEL
import com.rdstory.miuiperfsaver.Constants.SORT_ORDER_PACKAGE_NAME
import com.rdstory.miuiperfsaver.Constants.SORT_ORDER_UPDATE_TIME
import com.rdstory.miuiperfsaver.R
import java.util.*

@SuppressLint("NotifyDataSetChanged")
class InstalledPackageAdapter(context: Context, prefs: SharedPreferences) :
    RecyclerView.Adapter<InstalledPackageAdapter.ViewHolder>(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val mSharedPreferences: SharedPreferences = prefs
    private val mPackageManager: PackageManager = context.packageManager
    private var mInstalledPackages: MutableList<PackageInfoCache> = ArrayList()
    private val mFilteredPackages: MutableList<PackageInfoCache> = ArrayList()
    private var mFilterQuery = ""

    init {
        setHasStableIds(true)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            PREF_KEY_SORT_ORDER, PREF_KEY_SHOW_PERF_SAVED_FIRST, PREF_KEY_SHOW_SYSTEM -> {
                filterAndSort()
                notifyDataSetChanged()
            }
        }
    }

    class PackageInfoCache(var pkg: PackageInfo) {
        var mLabel: CharSequence? = null
        var mIcon: Drawable? = null

        fun getIcon(pm: PackageManager): Drawable {
            return mIcon ?: pm.getApplicationIcon(pkg.applicationInfo).also { mIcon = it }
        }

        fun getLabel(pm: PackageManager): CharSequence {
            return mLabel ?: pm.getApplicationLabel(pkg.applicationInfo).also { mLabel = it }
        }

        val packageName: String
            get() = pkg.packageName

        private fun hasFlag(flag: Int): Boolean {
            return pkg.applicationInfo.flags and flag == flag
        }

        val isDebugApp: Boolean
            get() = hasFlag(ApplicationInfo.FLAG_DEBUGGABLE)
        val isSystemApp: Boolean
            get() = hasFlag(ApplicationInfo.FLAG_SYSTEM)
        val isPerfSaved: Boolean
            get() = Configuration.isPerfSaved(packageName)
        val installTime: Long
            get() = pkg.firstInstallTime
        val updateTime: Long
            get() = pkg.lastUpdateTime

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(mFilteredPackages[position])
    }

    override fun getItemCount(): Int {
        return mFilteredPackages.size
    }

    override fun getItemId(position: Int): Long {
        return mFilteredPackages[position].packageName.hashCode().toLong()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    fun updateInstalledPackages(packages: List<PackageInfo>) {
        val map = SparseArrayCompat<PackageInfoCache>(mInstalledPackages.size)
        for (pkg in mInstalledPackages) map.put(pkg.packageName.hashCode(), pkg)
        mInstalledPackages = ArrayList(packages.size)
        for (newPkg in packages) {
            val pkg = map.get(newPkg.packageName.hashCode()) ?: PackageInfoCache(newPkg)
            pkg.pkg = newPkg
            pkg.mIcon = null
            pkg.mLabel = null
            mInstalledPackages.add(pkg)
        }
        filterAndSort()
        notifyDataSetChanged()
    }

    fun setFilterQuery(query: String) {
        mFilterQuery = query
        filterAndSort()
        notifyDataSetChanged()
    }

    private fun filterSystem(target: List<PackageInfoCache>): MutableList<PackageInfoCache> {
        if (mSharedPreferences.getBoolean(PREF_KEY_SHOW_SYSTEM, false)) return target.toMutableList()
        val filtered: MutableList<PackageInfoCache> = ArrayList(target.size)
        for (pkg in target) if (!pkg.isSystemApp) filtered.add(pkg)
        return filtered
    }

    private fun filterQuery(target: List<PackageInfoCache>): MutableList<PackageInfoCache> {
        if (TextUtils.isEmpty(mFilterQuery)) return target.toMutableList()
        val filtered: MutableList<PackageInfoCache> = ArrayList(target.size)
        for (pkg in target) if (pkg.getLabel(mPackageManager).toString()
                .lowercase(Locale.getDefault())
                .contains(mFilterQuery) ||
            pkg.packageName.lowercase(Locale.getDefault()).contains(mFilterQuery)
        ) filtered.add(pkg)
        return filtered
    }

    private fun filterAndSort() {
        var filtered = filterSystem(mInstalledPackages)
        filtered = filterQuery(filtered)
        val orderComparator = when (mSharedPreferences.getInt(PREF_KEY_SORT_ORDER, SORT_ORDER_LABEL)) {
            SORT_ORDER_LABEL -> Comparator.comparing { pkg: PackageInfoCache ->
                pkg.getLabel(mPackageManager).toString()
            }
            SORT_ORDER_PACKAGE_NAME -> Comparator.comparing { obj: PackageInfoCache -> obj.packageName }
            SORT_ORDER_INSTALL_TIME -> Comparator.comparingLong { obj: PackageInfoCache -> obj.installTime }
                    .reversed()
            SORT_ORDER_UPDATE_TIME -> Comparator.comparingLong { obj: PackageInfoCache -> obj.updateTime }
                    .reversed()
            else -> Comparator.comparing { pkg: PackageInfoCache ->
                pkg.getLabel(mPackageManager).toString()
            }
        }
        if (mSharedPreferences.getBoolean(PREF_KEY_SHOW_PERF_SAVED_FIRST, false)) {
            val perfSavedComparator =
                Comparator { pkg1: PackageInfoCache, pkg2: PackageInfoCache ->
                    return@Comparator if (pkg1.isPerfSaved && !pkg2.isPerfSaved) 1
                    else if (!pkg1.isPerfSaved && pkg2.isPerfSaved) -1
                    else 0
                }
            filtered.sortWith(perfSavedComparator.reversed().thenComparing(orderComparator))
        } else {
            filtered.sortWith(orderComparator)
        }
        mFilteredPackages.clear()
        mFilteredPackages.addAll(filtered)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener, AdapterView.OnItemSelectedListener {

        companion object {
            private fun getTypeFace(system: Boolean, debug: Boolean): Int {
                if (system && debug) return Typeface.BOLD_ITALIC
                if (system) return Typeface.BOLD
                return if (debug) Typeface.ITALIC else Typeface.NORMAL
            }
        }

        private val icon: AppCompatImageView = itemView.findViewById(R.id.icon)
        private val applicationLabel: AppCompatTextView = itemView.findViewById(R.id.application_label)
        private val packageName: AppCompatTextView = itemView.findViewById(R.id.package_name)
        private val fpsSpinner: AppCompatSpinner = itemView.findViewById(R.id.fps_spinner)
        private var pkg: PackageInfoCache? = null

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(pkg: PackageInfoCache) {
            this.pkg = pkg
            val context: Context = itemView.context
            val pm: PackageManager = context.packageManager
            icon.setImageDrawable(pkg.getIcon(pm))
            applicationLabel.text = pkg.getLabel(pm)
            applicationLabel.setTypeface(null, getTypeFace(pkg.isSystemApp, pkg.isDebugApp))
            packageName.text = pkg.packageName
            fpsSpinner.adapter ?: let {
                val items = Configuration.supportedFPS.map { "$it Hz" }.toMutableList().apply {
                    add(0, context.getString(R.string.fps_value_default))
                }
                fpsSpinner.adapter = object : ArrayAdapter<String>(itemView.context, android.R.layout.simple_spinner_item, items) {
                    init {
                        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }
                    override fun getItemId(position: Int): Long {
                        return (position - 1).toLong() // ignore first value
                    }
                }
                fpsSpinner.onItemSelectedListener = this
            }
            fpsSpinner.setSelection(Configuration.fpsIndex(pkg.packageName) + 1)
        }

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val packageName = pkg?.packageName ?: return
            Configuration.setFps(packageName, Configuration.supportedFPS.getOrNull(id.toInt()))
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            // ignore
        }

        override fun onClick(view: View) {
            fpsSpinner.performClick()
        }
    }
}