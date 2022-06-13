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
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.AppCompatTextView
import androidx.collection.SparseArrayCompat
import androidx.recyclerview.widget.RecyclerView
import com.rdstory.miuiperfsaver.Configuration
import com.rdstory.miuiperfsaver.Constants.FAKE_PKG_DEFAULT_FPS
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
    private val mFixedHeaders: MutableList<FakePackage> = ArrayList()
    private var mFilterQuery = ""
    private val defaultFpsItem = FakePackage(FakePackageType.DEFAULT_FPS).apply {
        icon = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
        title = context.getString(R.string.global_default_fps)
        description = context.getString(R.string.global_default_fps_desc)
    }

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

    enum class FakePackageType(val pkg: String) {
        DEFAULT_FPS(FAKE_PKG_DEFAULT_FPS)
    }

    class FakePackage(val type: FakePackageType) {
        var icon: Drawable? = null
        var title: String? = null
        var description: String? = null
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
        when {
            position < mFixedHeaders.size -> holder.bind(mFixedHeaders[position])
            else -> holder.bind(mFilteredPackages[position - mFixedHeaders.size])
        }
    }

    override fun getItemCount(): Int {
        return mFixedHeaders.size + mFilteredPackages.size
    }

    override fun getItemId(position: Int): Long {
        return when {
            position < mFixedHeaders.size -> mFixedHeaders[position].type.pkg.hashCode().toLong()
            else -> mFilteredPackages[position - mFixedHeaders.size].packageName.hashCode().toLong()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position < mFixedHeaders.size -> mFixedHeaders[position]::class.java.hashCode()
            else -> mFilteredPackages[position - mFixedHeaders.size]::class.java.hashCode()
        }
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
        mFixedHeaders.clear()
        if (TextUtils.isEmpty(mFilterQuery)) {
            mFixedHeaders.add(defaultFpsItem)
        }
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
        private var fakePkg: FakePackage? = null

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(pkg: FakePackage) {
            this.bind(pkg, null)
        }

        fun bind(pkg: PackageInfoCache) {
            this.bind(null, pkg)
        }

        private fun bind(fakePkg: FakePackage?, pkg: PackageInfoCache?) {
            this.pkg = pkg
            this.fakePkg = fakePkg
            val context: Context = itemView.context
            val pm: PackageManager = context.packageManager
            val iconDrawable = fakePkg?.icon ?: pkg?.getIcon(pm)
            val title = fakePkg?.title ?: pkg?.getLabel(pm)
            val desc = fakePkg?.description ?: pkg?.packageName
            icon.setImageDrawable(iconDrawable)
            applicationLabel.text = title
            pkg?.let {
                applicationLabel.setTypeface(null, getTypeFace(pkg.isSystemApp, pkg.isDebugApp))
            } ?: let {
                applicationLabel.setTypeface(null, getTypeFace(system = true, debug = false))
            }
            packageName.text = desc
            fpsSpinner.adapter = fpsSpinner.adapter ?: createSpinnerAdapter()
            fpsSpinner.onItemSelectedListener = this
            val spinnerIndex = (fakePkg?.type?.pkg ?: pkg?.packageName)?.let {
                Configuration.fpsIndex(it)
            } ?: -1
            fpsSpinner.setSelection(spinnerIndex + 1)
        }

        private fun createSpinnerAdapter(): ArrayAdapter<String> {
            val items = Configuration.supportedFPS.map { "$it Hz" }.toMutableList().apply {
                val defaultResId = if (fakePkg?.type?.pkg === FAKE_PKG_DEFAULT_FPS) {
                    R.string.fps_value_system
                } else {
                    R.string.fps_value_default
                }
                add(0, itemView.context.getString(defaultResId))
            }
            return object : ArrayAdapter<String>(itemView.context, android.R.layout.simple_spinner_item, items) {
                init {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                override fun getItemId(position: Int): Long {
                    return (position - 1).toLong() // ignore first value
                }
            }
        }

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val packageName = fakePkg?.type?.pkg ?: pkg?.packageName ?: return
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