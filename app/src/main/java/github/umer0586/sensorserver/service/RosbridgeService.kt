package github.umer0586.sensorserver.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RosbridgeService : Service() {

    private val binder = LocalBinder()
    private var connect = false
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private val TAG = "RosbridgeClientService"

    fun isConnected(): Boolean {
        return connect
    }
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    /**

     * @param ipAddress  (e.g. 192.168.0.100)
     * @param port 9090
     */
    fun connectToRosbridge(ipAddress: String, port: Int = 9090) {
        val url = "ws://$ipAddress:$port"
        Log.d(TAG, "Attempting to connect to ROSBridge at $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object: WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connect = true
                Log.d(TAG, "Connected to ROSBridge at $url")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: $text")

            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connect = false
                Log.e(TAG, "Connection failed to $url", t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Connection closed: $reason")
                connect = false
                onDestroy()
            }
        })
    }
    fun closeConnection() {
        webSocket?.close(1000, "Service destroyed")
    }
    fun subscribeToTopic(topicName: String) {
        val subscribeMessage = JSONObject()
        subscribeMessage.put("op", "subscribe")
        subscribeMessage.put("topic", topicName)
        webSocket?.send(subscribeMessage.toString())
    }

    fun advertiseTopic(topicName: String, messageType: String) {
        val advertiseMessage = JSONObject()
        advertiseMessage.put("op", "advertise")
        advertiseMessage.put("topic", topicName)
        advertiseMessage.put("type", messageType) // 例如 "std_msgs/Int32"
        webSocket?.send(advertiseMessage.toString())
    }

    fun publishMessage(topicName: String, content: JSONObject) {
        val publishMessage = JSONObject()
        publishMessage.put("op", "publish")
        publishMessage.put("topic", topicName)
        publishMessage.put("msg", content)
        webSocket?.send(publishMessage.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Service destroyed")
    }

    inner class LocalBinder : Binder() {
        val service: RosbridgeService
            get() = this@RosbridgeService
    }

}
