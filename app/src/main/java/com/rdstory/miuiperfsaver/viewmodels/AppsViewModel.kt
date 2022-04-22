package com.rdstory.miuiperfsaver.viewmodels

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Process
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.rdstory.miuiperfsaver.utils.PackageInfoUtil
import com.rdstory.miuiperfsaver.utils.TaskExecutor

class AppsViewModel : ViewModel() {
    private val mInstalledPackages = MutableLiveData<List<PackageInfo>>()

    init {
        mInstalledPackages.value = ArrayList()
    }

    val installedPackages: LiveData<List<PackageInfo>>
        get() = mInstalledPackages

    fun updatePackageList(context: Context) {
        TaskExecutor.execute {
            val priority = Process.getThreadPriority(Process.myTid())
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            mInstalledPackages.postValue(packages)
            Process.setThreadPriority(priority)
            PackageInfoUtil.load(pm, packages)
        }
    }
}
