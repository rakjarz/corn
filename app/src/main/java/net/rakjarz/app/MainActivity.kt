package net.rakjarz.app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.hypertrack.hyperlog.HyperLog
import net.rakjarz.corn.Corn
import net.rakjarz.corn.Level

class MainActivity : AppCompatActivity() {
    val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_java_io).setOnClickListener {
            Corn.rotateLogs()
        }

        findViewById<Button>(R.id.btn_generate).setOnClickListener {
            writeLogs()
        }

        findViewById<Button>(R.id.btn_file).setOnClickListener {
            println("file test")
            val t1 = System.currentTimeMillis()
            var m = 0
            for (i in 1..100000) {
                Corn.log(Level.DEBUG, TAG, "Now that the logs were being written to disk, the next step was to build a mechanism to rotate the logs as needed $i")
                m = i
            }
            Corn.flush()
            println("total time file: ${System.currentTimeMillis() - t1} last=${m}")
        }

        findViewById<Button>(R.id.btn_sql).setOnClickListener {
            println("sql test")
            val t1 = System.currentTimeMillis()
            for (i in 1..100000) {
//                Corn.log(Level.DEBUG, TAG, "Now that the logs were being written to disk, the next step was to build a mechanism to rotate the logs as needed $i")
                HyperLog.d(TAG, "Now that the logs were being written to disk, the next step was to build a mechanism to rotate the logs as needed $i")
            }
            println("total time SQL: ${System.currentTimeMillis() - t1}")
        }


    }

    private fun writeLogs() {
        for (i in 1..Int.MAX_VALUE) {
            println("line: $i")
            Corn.log(Level.DEBUG, TAG, "Now that the logs were being written to disk, the next step was to build a mechanism to rotate the logs as needed $i")
        }
    }
}