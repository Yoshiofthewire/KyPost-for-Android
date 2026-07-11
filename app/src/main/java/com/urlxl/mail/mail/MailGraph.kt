package com.urlxl.mail.mail

import android.content.Context
import com.urlxl.mail.SingletonGraph
import com.urlxl.mail.data.DataRuntime
import com.urlxl.mail.push.PushRuntime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MailGraph(context: Context) {
    private val appContext = context.applicationContext
    private val mailCursorStore = MailCursorStore(appContext)
    private val relaySource: MailSource = RelayMailSource(
        pairingProvider = { runBlocking { PushRuntime.graph(appContext).repository.state.first().pairing } },
        cursorProvider = mailCursorStore,
    )

    val repository = MailRepository(
        emailDao = DataRuntime.graph(appContext).database.emailDao(),
        relaySource = relaySource,
    )
}

object MailRuntime {
    private val holder = SingletonGraph(::MailGraph)

    fun graph(context: Context): MailGraph = holder.get(context)
}
