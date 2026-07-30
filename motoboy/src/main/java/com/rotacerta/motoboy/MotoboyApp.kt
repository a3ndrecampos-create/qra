package com.rotacerta.motoboy

import android.app.Application
import com.rotacerta.motoboy.data.MotoboyRepository

class MotoboyApp : Application() {

    lateinit var repository: MotoboyRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MotoboyRepository(this)
    }
}
