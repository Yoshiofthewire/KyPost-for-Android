package com.urlxl.mail.data

import android.content.Context
import androidx.room.Room

class DataGraph(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "llama_mail.db").build()
}

/** Standalone singleton, kept independent of PushGraph/LlamaApp — mirrors how PushGraph itself
 *  stands alone rather than nesting inside another graph. */
object DataRuntime {
    @Volatile
    private var graphInstance: DataGraph? = null

    fun graph(context: Context): DataGraph {
        return graphInstance ?: synchronized(this) {
            graphInstance ?: DataGraph(context.applicationContext).also { graphInstance = it }
        }
    }
}
