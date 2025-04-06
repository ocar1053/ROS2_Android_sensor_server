package github.umer0586.sensorserver.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.gms.location.DeviceOrientation
import com.google.android.gms.location.DeviceOrientationListener
import com.google.android.gms.location.DeviceOrientationRequest
import com.google.android.gms.location.FusedOrientationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
class OdometryHandler(
    context: Context,
    private val sensorManager: SensorManager,
    private val onOdometryDataUpdated: (odometryJson: JSONObject) -> Unit // callback
) : SensorEventListener {


    private val gyroscopeSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // 用於取得 fused orientation（Google 提供的 API）
    private val fusedOrientationProviderClient: FusedOrientationProviderClient =
        LocationServices.getFusedOrientationProviderClient(context)
    private val fusedExecutor = Executors.newSingleThreadExecutor()
    private val fusedOrientationListener = DeviceOrientationListener { orientation: DeviceOrientation ->
        latestOrientation = orientation.getAttitude()
    }

    // 感測器原始資料
    private var latestGyroscope = FloatArray(3)
    // 預設單位四元數 (w=1)
    private var latestOrientation = FloatArray(4) { 0f }.apply { this[3] = 1f }

    // 經由積分獲取的位置與速度（示範用，長時間會有漂移）
    private var position = FloatArray(3) { 0f }
    private var gpsSpeed: Float = 0f
    private var gpsBearing: Float = 0f
    private var lastGpsTime: Long = -1L

    /**
     * 開始監聽感測器
     */
    fun startListening() {
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        val request = DeviceOrientationRequest.Builder(DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT).build()
        fusedOrientationProviderClient.requestOrientationUpdates(request, fusedExecutor, fusedOrientationListener)
            .addOnSuccessListener { }
            .addOnFailureListener { }
    }

    /**
     * 停止監聽感測器
     */
    fun stopListening() {
        sensorManager.unregisterListener(this)
        fusedOrientationProviderClient.removeOrientationUpdates(fusedOrientationListener)
    }
    fun updateGpsSpeed(speed: Float, bearing: Float, gpsTime: Long) {
        gpsBearing = bearing
        gpsSpeed = speed
        updatePositionWithGps(gpsTime)


    }

    private fun updatePositionWithGps(currentGpsTime: Long) {
        if (lastGpsTime < 0) {
            lastGpsTime = currentGpsTime
            return
        }
        // 計算兩次 GPS 更新的時間間隔 (秒)
        val dt = (currentGpsTime - lastGpsTime) / 1000.0f
        lastGpsTime = currentGpsTime

        // 將 gpsBearing 轉為弧度，計算 x、y 分量
        val bearingRadians = Math.toRadians(gpsBearing.toDouble())
        val vx = gpsSpeed * cos(bearingRadians).toFloat()
        val vy = gpsSpeed * sin(bearingRadians).toFloat()

        // 更新位置 (假設 z 軸不變)
        position[0] += (vx * dt)
        position[1] += (vy * dt)
        // 若需要，可更新 position[2] (例如利用高度差)
    }
    /**
     * 感測器數據回調
     */
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { e ->
            when (e.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    latestGyroscope = e.values.clone()
                }
            }
            onOdometryDataUpdated(buildOdometryJson())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}


    private fun buildOdometryJson(): JSONObject {
        val odomJson = JSONObject()

        // Header: 時間戳與座標系
        val currentTime = System.currentTimeMillis()
        val header = JSONObject().apply {
            put("stamp", JSONObject().apply {
                put("secs", currentTime / 1000)
                put("nsecs", (currentTime % 1000) * 1_000_000)
            })
            put("frame_id", "odom")
        }
        odomJson.put("header", header)

        // child_frame_id
        odomJson.put("child_frame_id", "base_footprint")

        // PoseWithCovariance
        val innerPose = JSONObject().apply {
            val positionJson = JSONObject().apply {
                put("x", position[0].toDouble())
                put("y", position[1].toDouble())
                put("z", position[2].toDouble())
            }
            val orientationJson = JSONObject().apply {
                put("x", latestOrientation.getOrNull(0) ?: 0f)
                put("y", latestOrientation.getOrNull(1) ?: 0f)
                put("z", latestOrientation.getOrNull(2) ?: 0f)
                put("w", latestOrientation.getOrNull(3) ?: 1f)
            }
            put("position", positionJson)
            put("orientation", orientationJson)
        }
        val poseWithCovariance = JSONObject().apply {
            put("pose", innerPose)
            // covariance 可以根據實際需求填寫，這裡暫時全 0
            put("covariance", JSONArray(List(36) { 0.0 }))
        }
        odomJson.put("pose", poseWithCovariance)

        // TwistWithCovariance
        val bearingRadians = Math.toRadians(gpsBearing.toDouble())

        val innerTwist = JSONObject().apply {
            val linearJson = JSONObject().apply {
                put("x", gpsSpeed * cos(bearingRadians))
                put("y",  gpsSpeed * sin(bearingRadians))
                put("z", 0.0)
            }
            val angularJson = JSONObject().apply {
                put("x", latestGyroscope.getOrNull(0) ?: 0f)
                put("y", latestGyroscope.getOrNull(1) ?: 0f)
                put("z", latestGyroscope.getOrNull(2) ?: 0f)
            }
            put("linear", linearJson)
            put("angular", angularJson)
        }
        val twistWithCovariance = JSONObject().apply {
            put("twist", innerTwist)
            // 同樣暫時全 0
            put("covariance", JSONArray(List(36) { 0.0 }))
        }
        odomJson.put("twist", twistWithCovariance)

        return odomJson
    }
}
