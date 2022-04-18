package com.rdstory.miuiperfsaver.viewmodels

import android.content.Context
import android.content.pm.PackageInfo
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AppsViewModel : ViewModel() {
    private val mInstalledPackages = MutableLiveData<List<PackageInfo>>()

    init {
        mInstalledPackages.value = ArrayList()
    }

    val installedPackages: LiveData<List<PackageInfo>>
        get() = mInstalledPackages

    fun updatePackageList(context: Context) {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(0)
        mInstalledPackages.value = packages
    }
}