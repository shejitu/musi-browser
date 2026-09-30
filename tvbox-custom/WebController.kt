package com.github.tvbox.osc.server

import com.github.tvbox.osc.event.RefreshEvent
import com.yanzhenjie.andserver.annotation.GetMapping
import com.yanzhenjie.andserver.annotation.QueryParam
import com.yanzhenjie.andserver.annotation.ResponseBody
import com.yanzhenjie.andserver.annotation.RestController
import org.greenrobot.eventbus.EventBus

@RestController
class WebController {

    @GetMapping(path = ["/index.html", "/api/remote/version"])
    @ResponseBody
    fun hello(): String {
        return "hello"
    }

    @GetMapping("/api/updateUrl")
    @ResponseBody
    fun play(@QueryParam("url") url: String): String {
        return try {
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_PUSH_URL, url))
            "ok"
        } catch (e: Exception) {
            e.printStackTrace()
            "error:" + e.message
        }
    }

    // 慕思定制: 电脑推送仓库配置 (http://电视IP:9978/api/pushConfig?url=xxx)
    @GetMapping("/api/pushConfig")
    @ResponseBody
    fun pushConfig(@QueryParam("url") url: String): String {
        return try {
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_API_URL_CHANGE, url))
            "ok"
        } catch (e: Exception) {
            "error:" + e.message
        }
    }

    // 慕思定制: 电脑推送 socks 代理, 如 http://电视IP:9978/api/pushProxy?proxy=192.168.1.5:7890
    @GetMapping("/api/pushProxy")
    @ResponseBody
    fun pushProxy(@QueryParam("proxy") proxy: String): String {
        return try {
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_PROXYS_CHANGE, proxy))
            "ok"
        } catch (e: Exception) {
            "error:" + e.message
        }
    }

}