import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class AdvancedOrientationListener(private val activity: Activity, private val lifecycleScope: CoroutineScope) {
    private val sensorManager: SensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var lastOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var lastStableOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var lastOrientationChangeTime = 0L
    private val debounceTime = 200L
    private val stabilityThresholdTime = 800L
    private var deviceAspectRatio: Float = 1.0f

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    val onOrientationChanged = Event1<Int>()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, gravity, 0, gravity.size)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, geomagnetic, 0, geomagnetic.size)
                }
            }

            if (gravity.isNotEmpty() && geomagnetic.isNotEmpty()) {
                val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
                if (success) {
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)

                    val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                    val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                    val newOrientation = when {
                        roll in -155f .. -15f && isWithinThreshold(pitch, 0f, 30.0) -> {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                        roll in 15f .. 155f && isWithinThreshold(pitch, 0f, 30.0) -> {
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        }
                        isWithinThreshold(pitch, -90f, 30.0 * deviceAspectRatio) && roll in -15f .. 15f -> {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                        isWithinThreshold(pitch, 90f, 30.0 * deviceAspectRatio) && roll in -15f .. 15f -> {
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        }
                        else -> lastOrientation
                    }

                    //Logger.i("AdvancedOrientationListener", "newOrientation = ${newOrientation}, roll = ${roll}, pitch = ${pitch}, azimuth = ${azimuth}")

                    if (newOrientation != lastStableOrientation) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastOrientationChangeTime > debounceTime) {
                            lastOrientationChangeTime = currentTime
                            lastStableOrientation = newOrientation

                            lifecycleScope.launch(Dispatchers.Main) {
                                delay(stabilityThresholdTime)
                                if (newOrientation == lastStableOrientation) {
                                    lastOrientation = newOrientation
                                    onOrientationChanged.emit(newOrientation)
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun isWithinThreshold(value: Float, target: Float, threshold: Double): Boolean {
        return Math.abs(value - target) <= threshold
    }

    init {
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_GAME)

        val metrics = activity.resources.displayMetrics
        deviceAspectRatio = (metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat())
        if (deviceAspectRatio == 0.0f)
            deviceAspectRatio = 1.0f

        lastOrientation = activity.resources.configuration.orientation
    }

    fun stopListening() {
        sensorManager.unregisterListener(sensorListener)
    }
}
