package com.vzlpr.controller

import android.app.Application
import com.vzlpr.controller.data.repo.AppRepository

class VzApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 预热单例仓储
        AppRepository.get(this)
    }
}
