package com.wbzmm.startv

/** 跨 Activity 共享的频道列表 (数据量小, 进程内单例即可, 无需序列化传递) */
object ChannelStore {
    var channels: List<Channel> = emptyList()
}
