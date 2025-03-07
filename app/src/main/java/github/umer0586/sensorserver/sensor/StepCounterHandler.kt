package github.umer0586.sensorserver.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class StepCounterHandler(
    private val sensorManager: SensorManager,
    context: Context,
    private val onStepCountUpdated: (Int) -> Unit // callback
) : SensorEventListener {

    private var stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var initialStepCount = -1f

    fun startListening() {
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val totalSteps = it.values[0]


            val currentStepCount = totalSteps.toInt()

            // send
            onStepCountUpdated(currentStepCount)
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //
    }
}
