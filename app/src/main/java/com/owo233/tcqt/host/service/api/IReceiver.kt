package com.owo233.tcqt.host.service.api

fun interface IReceiver {
    fun onReceive(data: ByteArray)
}
