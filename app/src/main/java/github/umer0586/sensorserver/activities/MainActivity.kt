package github.umer0586.sensorserver.activities


import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.navigation.NavigationBarView
import github.umer0586.sensorserver.R
import github.umer0586.sensorserver.databinding.ActivityMainBinding
import github.umer0586.sensorserver.fragments.AvailableSensorsFragment
import github.umer0586.sensorserver.fragments.ServerFragment


class MainActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener
{

    private lateinit var binding : ActivityMainBinding
    companion object
    {

        private val TAG: String = MainActivity::class.java.simpleName

        // Fragments Positions
        private const val POSITION_SERVER_FRAGMENT = 0
        private const val POSITION_AVAILABLE_SENSORS_FRAGMENT = 1
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
       //toolBarBinding = ToolbarBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // Set a Toolbar to replace the ActionBar.
        setSupportActionBar(binding.toolbar.root)


        binding.dashboard.bottomNavView.selectedItemId = R.id.navigation_server
        binding.dashboard.bottomNavView.setOnItemSelectedListener(this)












        binding.dashboard.viewPager.isUserInputEnabled = false
        binding.dashboard.viewPager.adapter = MyFragmentStateAdapter(this)








    }






    override fun onPause()
    {
        super.onPause()
        Log.d(TAG, "onPause()")


    }




    override fun onNavigationItemSelected(item: MenuItem): Boolean
    {
        when (item.itemId)
        {
            R.id.navigation_available_sensors ->
            {
                binding.dashboard.viewPager.setCurrentItem(POSITION_AVAILABLE_SENSORS_FRAGMENT, false)
                supportActionBar?.title = "Available Topics"
                return true
            }



            R.id.navigation_server ->
            {
                binding.dashboard.viewPager.setCurrentItem(POSITION_SERVER_FRAGMENT, false)
                supportActionBar?.title = "Sensor Server"
                return true
            }
        }
        return false
    }

    private inner class MyFragmentStateAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity)
    {

        override fun createFragment(pos: Int): Fragment
        {
            when (pos)
            {
                POSITION_SERVER_FRAGMENT -> return ServerFragment()
                POSITION_AVAILABLE_SENSORS_FRAGMENT -> return AvailableSensorsFragment()
            }
            return ServerFragment()
        }

        override fun getItemCount(): Int
        {
            return 2
        }
    }


}