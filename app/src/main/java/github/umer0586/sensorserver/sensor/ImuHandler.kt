package github.umer0586.sensorserver.sensor
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI

class ImuHandler(
    private val sensorManager: SensorManager,
    private val onImuDataUpdated: (imuJson: JSONObject) -> Unit // callback
) : SensorEventListener {

    private var accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var gyroscopeSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // store latest sensor data
    private var latestAccelerometer = FloatArray(3)
    private var latestGyroscope = FloatArray(3)
    // store latest orientation quaternion to represent device orientation
    private var latestOrientation = FloatArray(4)

    fun startListening() {
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { e ->
            when (e.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    latestAccelerometer = e.values.clone()
                }
                Sensor.TYPE_GYROSCOPE -> {
                    latestGyroscope = e.values.clone()
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    //convert rotation vector to quaternion
                    val quat = FloatArray(4)
                    // rotation vector is in the format (x*sin(θ/2), y*sin(θ/2), z*sin(θ/2), cos(θ/2))
                    SensorManager.getQuaternionFromVector(quat, e.values)
                    latestOrientation = quat
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