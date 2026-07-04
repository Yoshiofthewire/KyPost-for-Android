package com.urlxl.mail.push

import android.content.Context

class PushGraph(context: Context) {
    val repository = PushRepository(context)
    val relayClient = RelaySyncClient()
    val syncCoordinator = PushSyncCoordinator(repository = repository, relayClient = relayClient)
}

object PushRuntime {
    @Volatile
    private var graphInstance: PushGraph? = null

    fun graph(context: Context): PushGraph {
        return graphInstance ?: synchronized(this) {
            graphInstance ?: PushGraph(context.applicationContext).also { graphInstance = it }
        }
    }
}

