package github.ocar1053.ros2sensorserver

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate


class MyApplication : Application()
{

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // For now, when a device is is dark mode we still use light theme (i-e no use of themes.xml night values)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

}

