package com.eastonseidel.joyncon

import android.app.Application
import com.eastonseidel.joyncon.service.ConnectionState
import com.eastonseidel.joyncon.shizuku.ShizukuHelper

class JoynConApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuHelper.init(this)
        ConnectionState.init(this)
    }
}
