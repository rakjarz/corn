package net.rakjarz.app

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import net.rakjarz.corn.Corn
import net.rakjarz.corn.Level

class MainActivity : AppCompatActivity() {
    private var content: TextView? = null
    private var logInput: TextView? = null
    private val TAG = "MainActivity"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logInput = findViewById(R.id.edt_log_input)
        content = findViewById(R.id.tv_content)
        findViewById<Button>(R.id.btn_write).setOnClickListener {
            var message = ""
            if (logInput?.text?.trim()?.also { message = it.toString() }?.isNotEmpty() != true) {
                message = generateMessage()
            }

            Log.d(TAG, message)
            Corn.log(Level.INFO, TAG, message)
            showLogs(20)
//            Toast.makeText(this, "OK", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_show).setOnClickListener { showLogs() }

        findViewById<Button>(R.id.btn_report).setOnClickListener {
            collectReports()
        }

        findViewById<Button>(R.id.btn_delete).setOnClickListener {
            Corn.clear(true)
            content?.text = ""
        }
        findViewById<Button>(R.id.btn_stress).setOnClickListener {
            stress100k()
        }
        findViewById<Button>(R.id.btn_show_all).setOnClickListener {
            showAllLogs()
        }

    }

    private fun collectReports() {
        val t1 = System.currentTimeMillis()
        val files = Corn.collectReports()
        val t2 = System.currentTimeMillis()

        Toast.makeText(this, "collect ${files.size} reports in ${t2 - t1}ms", Toast.LENGTH_SHORT).show()
    }

    private fun stress100k() {
        println("file test")
        val t1 = System.currentTimeMillis()
        var m = 0
        for (i in 1..100000) {
            Corn.log(Level.DEBUG, TAG, "Now that the logs were being written to disk, the next step was to build a mechanism to rotate the logs as needed $i")
            m = i
        }
        Corn.flush()
        val t2 = System.currentTimeMillis()
        println("total time file: ${t2 - t1} last=${m}")
        Toast.makeText(this, "Success ${t2 - t1}ms", Toast.LENGTH_SHORT).show()
    }

    private fun showLogs(limit: Int = 200) {
        Corn.flush {
            runOnUiThread {
                val t1 = System.currentTimeMillis()
                val logs = Corn.getLogsAsStringList(0, limit)
                val t2 = System.currentTimeMillis()
                println("finish show log at: ${t2 - t1}ms")
                Toast.makeText(this, "Success ${t2 - t1}ms", Toast.LENGTH_SHORT).show()

                content?.let { it.text = "" }

                for (i in 0 until logs.size.coerceAtMost(limit)) {
                    content?.append(logs[i])
                    content?.append(System.lineSeparator())
                }
            }
        }
    }

    private fun showAllLogs() {
        Corn.flush {
            runOnUiThread {
                val t1 = System.currentTimeMillis()
                val logs = Corn.getAllLogsAsStringList()
                val t2 = System.currentTimeMillis()
                println("finish show all logs at: ${t2 - t1}ms")
                Toast.makeText(this, "Success ${t2 - t1}ms", Toast.LENGTH_SHORT).show()
                content?.let { it.text = "" }
                for (i in 0 until logs.size.coerceAtMost(200)) {
                    content?.append(logs[i])
                    content?.append(System.lineSeparator())
                }
            }
        }
    }

    private val SAMPLE_LOGS = listOf(
        "MainActivity: Button clicked, initiating action.",
        "NetworkManager: Connecting to WiFi network: \"MyHomeNetwork\".",
        "DatabaseHandler: Error executing SQL query: Table \"users\" not found.",
        "LocationService: Location permission not granted, unable to fetch user location.",
        "FragmentLifecycle: Fragment paused: UserProfileFragment.",
        "VideoPlayer: Video playback started for video ID: 12345.",
        "BluetoothManager: Bluetooth connection lost with device: \"SmartphoneDevice\".",
        "HTTPClient: Failed to connect to server: Connection timed out.",
        "NotificationService: Notification posted: \"New Message - You have 1 unread message.\"",
        "GestureDetector: Swipe gesture detected, navigating to next screen.",
        "ResourceLoader: Loaded resource \"icon.png\" from assets.",
        "PermissionHandler: Insufficient permissions to access camera.",
        "BackgroundTask: Background sync task took longer than expected (1500ms).",
        "LocationProvider: User location updated: Lat: 37.7749, Long: -122.4194.",
        "FileManager: Unable to read file: \"document.txt\". File not found.",
        "UIAnimator: Fading animation applied to view \"logoImageView\".",
        "DatabaseQuery: Executing SELECT query: SELECT * FROM products.",
        "BatteryMonitor: Battery level below 20%, consider charging the device.",
        "VideoDecoder: Error decoding video frame at timestamp 5678ms.",
        "BackgroundService: Service started in background, performing periodic sync."
    )
    private fun generateMessage(): String {
        return SAMPLE_LOGS.random()
    }
}