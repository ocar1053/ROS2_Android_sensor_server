package github.ocar1053.ros2sensorserver.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.ListFragment
import github.ocar1053.ros2sensorserver.R

// Custom data class representing a ROS topic with its type
data class RosTopic(
    val topicName: String,
    val type: String,
)

class AvailableSensorsFragment : ListFragment() {

    // Define the list of ROS topics to be displayed
    private val rosTopics: List<RosTopic> = listOf(
        RosTopic("/step_counter", "std_msgs/Int32"),
        RosTopic("/gps/fix", "sensor_msgs/msg/NavSatFix"),
        RosTopic("/imu/data", "sensor_msgs/msg/Imu"),
        RosTopic("/odom", "nav_msgs/msg/Odometry")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        Log.i(TAG, "onCreateView:")
        // Inflate the layout for this fragment (make sure fragment_available_sensors.xml exists)
        return inflater.inflate(R.layout.fragment_available_sensors, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "onViewCreated:")

        // Set up the adapter with the list of ROS topics using the original adapter structure
        val topicsListAdapter = SensorsListAdapter(requireContext(), rosTopics)
        listView.adapter = topicsListAdapter
    }

    // Override onListItemClick but perform no action when an item is clicked
    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        // No action on item click
    }

    // Original adapter structure modified to work with RosTopic instead of Sensor
    private inner class SensorsListAdapter(context: Context, topics: List<RosTopic>) :
        ArrayAdapter<RosTopic>(context, R.layout.item_sensor, topics) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            // Reuse convertView if available, otherwise inflate a new view from item_sensor.xml
            val view: View = convertView ?: layoutInflater.inflate(R.layout.item_sensor, parent, false)

            // Get the current ROS topic item
            val topic = getItem(position)
            // Find TextViews for displaying the topic name and type
            // Assumes item_sensor.xml contains TextViews with IDs sensor_name and sensor_type
            val topicNameTextView = view.findViewById<AppCompatTextView>(R.id.sensor_name)
            val topicTypeTextView = view.findViewById<AppCompatTextView>(R.id.sensor_type)

            topic?.let {
                // Set the topic name
                topicNameTextView.text = it.topicName
                // Display the topic type with formatted text using HtmlCompat
                topicTypeTextView.text = HtmlCompat.fromHtml(
                    "<font color=\"#5c6bc0\"><b>Type = </b></font>" + it.type,
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                )
            }

            // Set the tag for the view (optional)
            view.tag = topic
            return view
        }
    }

    companion object {
        private val TAG: String = AvailableSensorsFragment::class.java.simpleName
    }
}
