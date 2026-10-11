package com.example.swissquote

import android.app.Application
import com.example.swissquote.di.AppContainer

class SwissquoteApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
