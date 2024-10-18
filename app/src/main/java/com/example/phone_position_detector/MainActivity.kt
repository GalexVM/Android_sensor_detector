package com.example.phone_position_detector

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.Socket
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.atan
import kotlin.math.pow

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private lateinit var accelerometerDataTextView: TextView
    private lateinit var totalAccelerometerDataTextView: TextView
    private lateinit var gyroscopeDataTextView: TextView
    private lateinit var lastHighTextView: TextView
    private lateinit var startingVelocityTextView: TextView
    private lateinit var startingAngleTextView: TextView

    private var socket: Socket? = null
    private val outputStreamLock = Any() // Para manejar el acceso al outputStream

    var old_acc = 9.0
    var lastDiff = 0.0
    var radius = 0.267
    var lastRot = 0.0
    var startingAngle = 0.0
    var anx = 0.0
    var any = 0.0
    var anz = 0.0

    var serverIP: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Referencias a los TextViews
        accelerometerDataTextView = findViewById(R.id.accelerometer_data_text)
        gyroscopeDataTextView = findViewById(R.id.gyroscope_data_text)
        totalAccelerometerDataTextView = findViewById(R.id.total_accelerometer_data_text)
        lastHighTextView = findViewById(R.id.last_high)
        startingVelocityTextView = findViewById(R.id.vel_inicial)
        startingAngleTextView = findViewById((R.id.ang_inicial))

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Registrar los sensores
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        gyroscope?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

       //Pedir IP
        showIPDialog()
    }



    @SuppressLint("SetTextI18n")
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    var isAPick = false
                    val ax = it.values[0]
                    val ay = it.values[1]
                    val az = it.values[2]
                    val mediaAcc = sqrt((ax * ax) + (ay * ay) + (az * az).toDouble())
                    val diff = mediaAcc - old_acc
                    if (diff >= 10.0f) {
                        isAPick = true
                        lastDiff = diff
                    }
                    old_acc = mediaAcc



                    val accelerometerData = "Accelerometer\nX: $ax\nY: $ay\nZ: $az"
                    val totalAcceleration = "Total Acceleration:\n$mediaAcc"

                    accelerometerDataTextView.text = accelerometerData
                    totalAccelerometerDataTextView.text = totalAcceleration
                    lastHighTextView.text = "Last high difference: $lastDiff"

                    // Enviar datos al servidor en un hilo separado
                    if(isAPick){
                        sendDataToServer(ax,ay,az,anx,any,anz)
                        //sendDataToServer(lastDiff,lastRot,startingAngle)
                    }else{

                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    val gx = it.values[0]
                    val gy = it.values[1]
                    val gz = it.values[2]
                    //Log.d("Gyroscope", "X: $gx, Y: $gy, Z: $gz")

                    // m/s
                    lastRot = (sqrt(gx*gx + gy*gy + gz*gz).toDouble()) * radius

                    anx = gx.toDouble()
                    any = gy.toDouble()
                    anz = gz.toDouble()

                    //tan-1(vy/vx)
                    startingAngle = atan(gy/gx).toDouble()

                    val gyroscopeData = "Gyroscope\nX: $gx\nY: $gy\nZ: $gz"
                    gyroscopeDataTextView.text = gyroscopeData
                    startingVelocityTextView.text = "Velocidad inicial: $lastRot\n"
                    startingAngleTextView.text = "Ángulo inicial: $startingAngle\n"

                }
                else -> {
                    Log.d("Sensor", "Other sensor detected")
                }
            }
        }
    }

    private fun connectToServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverIP?.let {
                    socket = Socket(it, 5000)
                    Log.d("SocketConnection", "Socket conectado a $it")
                }
            } catch (e: Exception) {
                Log.e("SocketConnection", "Error al conectar al socket", e)
            }
        }
    }

    private fun showIPDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Ingresar IP del Servidor")

        val input = EditText(this)
        builder.setView(input)

        builder.setPositiveButton("OK") { dialog, _ ->
            serverIP = input.text.toString()
            dialog.dismiss()
            connectToServer() // Conectar al servidor una vez obtenida la IP
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun sendDataToServer(a: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                socket?.let {
                    synchronized(outputStreamLock) {
                        val data = JSONObject()
                        data.put("Aceleracion", a)
                        it.getOutputStream().write(data.toString().toByteArray())
                        it.getOutputStream().flush()
                    }
                }
            } catch (e: Exception) {
                Log.e("SocketSend", "Error al enviar datos", e)
            }
        }
    }


    private fun sendDataToServer(acPulse: Double, linVel: Double, startAngle: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                socket?.let {
                    synchronized(outputStreamLock) {
                        val data = JSONObject()
                        data.put("Aceleración máxima", acPulse)
                        data.put("Velocidad inicial", linVel)
                        data.put("Ángulo inicial", startAngle)

                        it.getOutputStream().write(data.toString().toByteArray())
                        it.getOutputStream().flush()
                    }
                }
            } catch (e: Exception) {
                Log.e("SocketSend", "Error al enviar datos", e)
            }
        }
    }

    private fun sendDataToServer(vx: Float, vy: Float, vz: Float, ax: Double, ay: Double, az: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                socket?.let {
                    synchronized(outputStreamLock) {
                        val data = JSONObject()
                        data.put("VelocidadX", if(vx>0) vx.pow((1.0f/1.1f)) else -vx)
                        data.put("VelocidadY", if(vy>0) vx.pow((1.0f/1.1f)) else 0)
                        data.put("VelocidadZ", vz.pow((1.0f/1.1f)))
                        data.put("AngularX", ax)
                        data.put("AngularY", ay)
                        data.put("AngularZ", az)
                        it.getOutputStream().write(data.toString().toByteArray())
                        it.getOutputStream().flush()
                    }
                }
            } catch (e: Exception) {
                Log.e("SocketSend", "Error al enviar datos", e)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Aquí puedes manejar los cambios de precisión del sensor
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        socket?.close() // Cerrar el socket si no se está utilizando
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
    }
}
