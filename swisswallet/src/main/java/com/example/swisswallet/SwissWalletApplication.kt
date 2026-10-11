package com.example.swisswallet

import android.app.Application
import com.example.swisswallet.di.AppContainer

class SwissWalletApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
