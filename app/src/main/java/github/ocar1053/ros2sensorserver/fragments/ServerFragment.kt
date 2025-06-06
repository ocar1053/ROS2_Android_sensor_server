package github.ocar1053.ros2sensorserver.fragments

import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.permissionx.guolindev.PermissionX
import github.ocar1053.ros2sensorserver.R
import github.ocar1053.ros2sensorserver.databinding.FragmentServerBinding
import github.ocar1053.ros2sensorserver.sensor.GpsHandler
import github.ocar1053.ros2sensorserver.sensor.ImuHandler
import github.ocar1053.ros2sensorserver.sensor.OdometryHandler
import github.ocar1053.ros2sensorserver.service.RosbridgeService
import github.ocar1053.ros2sensorserver.service.ServiceBindHelper
import github.ocar1053.ros2sensorserver.sensor.StepCounterHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ServerFragment : Fragment() {
    private lateinit var stepCounterHandler: StepCounterHandler
    private lateinit var GpsHandler: GpsHandler
    private lateinit var imuHandler: ImuHandler
    private lateinit var OdometryHandler: OdometryHandler
    private var websocketService: RosbridgeService? = null
    private lateinit var serviceBindHelper: ServiceBindHelper
    private var connectToRosbridge: Boolean = false
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var bottomSheetContent: View
    private lateinit var sensorManager: SensorManager
    private var _binding : FragmentServerBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val TAG: String = ServerFragment::class.java.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentServerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetContent = layoutInflater.inflate(R.layout.bottom_sheet_dialog, null)
        bottomSheetDialog.setContentView(bottomSheetContent)
        sensorManager = requireContext().applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        serviceBindHelper = ServiceBindHelper(
            context = requireContext(),
            service = RosbridgeService::class.java,
            componentLifecycle = lifecycle
        )


        serviceBindHelper.onServiceConnected { binder ->
            val localBinder = binder as RosbridgeService.LocalBinder
            websocketService = localBinder.service
            Log.d(TAG, "RosbridgeService bound successfully")
        }

        // ui
        binding.startButton.tag = "stopped"


        //BUTTON LOGIC
        binding.startButton.setOnClickListener { v ->
            binding.startButton.isEnabled = false

            // request permission
            PermissionX.init(this)
                .permissions(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACTIVITY_RECOGNITION
                )
                .request { allGranted, _, _ ->
                    if (allGranted) {

                        if (v.tag == "stopped") {
                            connectToRosbridge { success ->
                                if (success) {
                                    v.tag = "started"
                                    binding.startButton.text = "STOP"
                                } else {
                                    v.tag = "stopped"
                                    binding.startButton.text = "START"
                                }
                                binding.startButton.isEnabled = true
                            }
                        } else {
                            disconnectFromRosbridge()
                            v.tag = "stopped"
                            binding.startButton.text = "START"
                            binding.startButton.isEnabled = true
                        }
                    } else {
                        Toast.makeText(requireContext(), "必要權限未授予", Toast.LENGTH_SHORT).show()
                        binding.startButton.isEnabled = true
                    }
                }
        }


        hidePulseAnimation()
        hideServerAddress()
    }
    private fun isValidIpAddress(ip: String): Boolean {
        val ipRegex = Regex(
            pattern = "^((25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\$"
        )
        return ipRegex.matches(ip)
    }

    private fun connectToRosbridge(callback: (Boolean) -> Unit) {
        Log.d(TAG, "connectToRosbridge() called")


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionX.init(this)
                .permissions(android.Manifest.permission.POST_NOTIFICATIONS)
                .request { _, _, _ -> }
        }


        lifecycleScope.launch(Dispatchers.Main) {
            while (websocketService == null) {
                delay(100)
            }

            val ipAddress = binding.ipAddressTextInput.text?.toString()?.trim()
            if (!ipAddress.isNullOrEmpty() && isValidIpAddress(ipAddress)) {
                websocketService?.connectToRosbridge(ipAddress)
                delay(500)
                if (websocketService?.isConnected() == true) {
                    showMessage("Connecting to ROSBridge at: $ipAddress")
                    showServerAddress("ws://$ipAddress:9090")
                    showPulseAnimation()
                    connectToRosbridge = true
                    //advertised

                    websocketService?.advertiseTopic("/imu/data", "sensor_msgs/msg/Imu")
                    websocketService?.advertiseTopic("/odom", "nav_msgs/msg/Odometry")
                    websocketService?.advertiseTopic("/step_counter", "std_msgs/Int32")
                    websocketService?.advertiseTopic("/gps/fix", "sensor_msgs/msg/NavSatFix")

                    // Add delay to allow rosbridge to advertise topics properly
                    delay(1000)

                    // Now initialize and start the sensor handlers
                    stepCounterHandler = StepCounterHandler(sensorManager ,requireContext()) { currentStepCount ->
                        val stepData = org.json.JSONObject().apply {
                            put("data", currentStepCount)
                        }
                        websocketService?.publishMessage("/step_counter", stepData)
                    }

                    OdometryHandler = OdometryHandler((requireContext()),sensorManager) { odometryData ->
                        websocketService?.publishMessage("/odom", odometryData)
                    }

                    GpsHandler = GpsHandler(requireContext(),OdometryHandler) { location ->
                        val gpsData = org.json.JSONObject().apply {
                            // header for time and frame_id
                            put("header", org.json.JSONObject().apply {
                                put("stamp", org.json.JSONObject().apply {

                                    put("secs", System.currentTimeMillis() / 1000)
                                    put("nsecs", 0)
                                })
                                put("frame_id", "gps")
                            })
                            // status 0 for valid data
                            put("status", org.json.JSONObject().apply {
                                put("status", 0)
                                put("service", 1)
                            })
                            // gps data
                            put("latitude", location.latitude)
                            put("longitude", location.longitude)
                            put("altitude", location.altitude)
                            // covariance parameters
                            put("position_covariance", org.json.JSONArray(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)))
                            put("position_covariance_type", 0)
                        }

                        websocketService?.publishMessage("/gps/fix", gpsData)

                    }

                    imuHandler = ImuHandler((requireContext()),sensorManager) { imuData ->
                        websocketService?.publishMessage("/imu/data", imuData)
                    }



                    // listening
                    imuHandler.startListening()
                    stepCounterHandler.startListening()
                    GpsHandler.startListening()
                    OdometryHandler.startListening()
                    callback(true)
                } else {
                    showMessage("connection refuse, ip is wrong")
                    callback(false)
                }

            } else {
                showMessage("Please enter a valid IP address")
                callback(false)
            }
        }
    }

    private fun disconnectFromRosbridge() {
        Log.d(TAG, "disconnectFromRosbridge()")
        if (connectToRosbridge) {
            websocketService?.closeConnection()
            stepCounterHandler.stopListening()
            hideServerAddress()
            hidePulseAnimation()
            showMessage("Disconnected")
            connectToRosbridge = false
        }
    }

    private fun showServerAddress(address: String) {
        binding.cardView.visibility = View.VISIBLE
        binding.serverAddress.visibility = View.VISIBLE
        binding.serverAddress.text = address
    }

    private fun hideServerAddress() {
        binding.cardView.visibility = View.GONE
        binding.serverAddress.visibility = View.GONE
    }

    private fun showPulseAnimation() {
        binding.pulseAnimation.visibility = View.VISIBLE
    }

    private fun hidePulseAnimation() {
        binding.pulseAnimation.visibility = View.INVISIBLE
    }

    private fun showMessage(message: String) {
        view?.let{
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).apply{
                setAnchorView(R.id.bottom_nav_view)
            }.show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
