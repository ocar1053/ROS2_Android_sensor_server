package github.ocar1053.ros2sensorserver.sensor
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

class ImuHandler(
    context: Context,
    private val sensorManager: SensorManager,
    private val onImuDataUpdated: (imuJson: JSONObject) -> Unit // callback
) : SensorEventListener {

    private var accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private var gyroscopeSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val fusedOrientationProviderClient: FusedOrientationProviderClient =
        LocationServices.getFusedOrientationProviderClient(context)
    private val fusedExecutor = Executors.newSingleThreadExecutor()
    private val fusedOrientationListener = DeviceOrientationListener { orientation: DeviceOrientation ->
        // getAttitude() return [qx, qy, qz, qw]
        latestOrientation = orientation.getAttitude()

    }    // store latest sensor data
    private var latestAccelerometer = FloatArray(3)
    private var latestGyroscope = FloatArray(3)
    // store latest orientation quaternion to represent device orientation
    private var latestOrientation = FloatArray(4) { 0f }.apply { this[3] = 1f }

    fun startListening() {
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        val request = DeviceOrientationRequest.Builder(DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT).build()
        fusedOrientationProviderClient.requestOrientationUpdates(request, fusedExecutor, fusedOrientationListener)
            .addOnSuccessListener {  }
            .addOnFailureListener { }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { e ->
            when (e.sensor.type) {
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    latestAccelerometer = e.values.clone()

                }
                Sensor.TYPE_GYROSCOPE -> {
                    latestGyroscope = e.values.clone()
                }
            }
            onImuDataUpdated(buildImuJson())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    /**
     * convert sensor data to ROS2 Imu message
     */
    private fun buildImuJson(): JSONObject {
        val imuJson = JSONObject()

        // Header: timestamp and frame_id
        val header = JSONObject().apply {
            val currentTime = System.currentTimeMillis()
            put("stamp", JSONObject().apply {
                put("secs", currentTime / 1000)
                put("nsecs", (currentTime % 1000) * 1000000)
            })
            put("frame_id", "imu_link")
        }
        imuJson.put("header", header)

        // Orientation
        val orientation = JSONObject().apply {
            put("x", latestOrientation.getOrNull(0) ?: 0f)
            put("y", latestOrientation.getOrNull(1) ?: 0f)
            put("z", latestOrientation.getOrNull(2) ?: 0f)
            put("w", latestOrientation.getOrNull(3) ?: 1f)
        }
        imuJson.put("orientation", orientation)
        // set covariance to -1 to indicate that the orientation covariance is not available
        imuJson.put("orientation_covariance", JSONArray(List(9) { -1.0 }))

        // Angular velocity (from gyroscope)
        val angularVelocity = JSONObject().apply {
            put("x", latestGyroscope.getOrNull(0) ?: 0f)
            put("y", latestGyroscope.getOrNull(1) ?: 0f)
            put("z", latestGyroscope.getOrNull(2) ?: 0f)
        }
        imuJson.put("angular_velocity", angularVelocity)
        imuJson.put("angular_velocity_covariance", JSONArray(List(9) { -1.0 }))

        // Linear acceleration (from accelerometer)
        val linearAcceleration = JSONObject().apply {
            put("x", latestAccelerometer.getOrNull(0) ?: 0f)
            put("y", latestAccelerometer.getOrNull(1) ?: 0f)
            put("z", latestAccelerometer.getOrNull(2) ?: 0f)
        }
        imuJson.put("linear_acceleration", linearAcceleration)
        imuJson.put("linear_acceleration_covariance", JSONArray(List(9) { -1.0 }))

        return imuJson
    }
}