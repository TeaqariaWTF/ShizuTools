package com.legendsayantan.adbtools

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.card.MaterialCardView
import com.legendsayantan.adbtools.lib.ShizukuRunner
import java.util.Timer
import kotlin.concurrent.timerTask

class PipStarterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent.getStringExtra("package").let { pkg ->
            if (pkg == null) {
                showing = true
                setContentView(R.layout.activity_pip_starter)
                val controls = listOf<MaterialCardView>(
                    findViewById(R.id.skipPrev),
                    findViewById(R.id.rewind),
                    findViewById(R.id.playPause),
                    findViewById(R.id.forward),
                    findViewById(R.id.skipNext)
                )
                val keys = listOf(
                    arrayOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS),
                    arrayOf(KeyEvent.KEYCODE_MEDIA_REWIND,KeyEvent.KEYCODE_DPAD_LEFT),
                    arrayOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
                    arrayOf(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,KeyEvent.KEYCODE_DPAD_RIGHT),
                    arrayOf(KeyEvent.KEYCODE_MEDIA_NEXT)
                )
                getExternalDisplayId { display->
                    Handler(mainLooper).post {
                        controls.forEachIndexed { index, materialCardView ->
                            materialCardView.setOnClickListener {
                                keys[index].forEach { key ->
                                    ShizukuRunner.command("input -d $display keyevent $key",
                                        object : ShizukuRunner.CommandResultListener {})
                                    interacted()
                                }
                            }
                        }
                    }
                }
                val extraBtns = listOf<MaterialCardView>(
                    findViewById(R.id.adSkipButton),
                    findViewById(R.id.fullScreenButton)
                )
                extraBtns[0].setOnClickListener {
                    interacted()
                    val metrics = getWindowParams()
                    getExternalDisplayId {
                        ShizukuRunner.command("input -d $it tap ${(metrics.first * 0.95).toInt()} ${(metrics.second*0.86).toInt()}",
                            object : ShizukuRunner.CommandResultListener {})
                    }
                }
                extraBtns[1].setOnClickListener {
                    disablePip()
                    interacted()
                }

                //outside touch
                findViewById<ConstraintLayout>(R.id.main).setOnClickListener {
                    finish()
                }

                setupAutoHide()
            } else {
                startActivity(packageManager.getLaunchIntentForPackage(pkg))
                playVideo()
                finish()
            }
        }
    }

    private fun setupAutoHide() {
        Timer().schedule(timerTask {
            if (lastInteractionAt >= 0
                && lastInteractionAt + hideTimerInterval < System.currentTimeMillis()
            ) {
                finish()
                cancel()
            }
        }, hideTimerInterval, hideTimerInterval)
    }

    companion object {
        var showing = false
        private const val hideTimerInterval = 3000L
        var lastInteractionAt = 0L
        var interacted = {
            lastInteractionAt = System.currentTimeMillis()
        }
        fun Context.getWindowParams():Pair<Int,Int>{
            val metrics = resources.displayMetrics
            return Pair(metrics.widthPixels,((metrics.widthPixels / (metrics.heightPixels.toFloat() / metrics.widthPixels)) + 100).toInt())
        }
        fun getExternalDisplayId(callback:(Int)->Unit){
            ShizukuRunner.command("dumpsys display | grep 'Display [0-9][0-9]*'",
                object :
                    ShizukuRunner.CommandResultListener {
                    override fun onCommandResult(
                        output: String,
                        done: Boolean
                    ) {
                        if (done) {
                            callback("\\d+".toRegex()
                                .findAll(output)
                                .filter { it.value != "0" }
                                .map { it.value.toInt() }
                                .maxOrNull() ?: 0)
                        }
                    }

                    override fun onCommandError(error: String) {
                        println(error)
                        disablePip()
                    }
                })
        }


        fun Context.handlePip() {
            ShizukuRunner.command("settings get global overlay_display_devices",
                object : ShizukuRunner.CommandResultListener {
                    override fun onCommandResult(output: String, done: Boolean) {
                        if (done) {
                            if (output.trim().contains("null", true)) {
                                enablePip()
                            } else {
                                ShizukuRunner.command("am start -n $packageName/${PipStarterActivity::class.java.canonicalName} --display 0",
                                    object : ShizukuRunner.CommandResultListener {
                                        override fun onCommandError(
                                            error: String
                                        ) {
                                            disablePip()
                                            println(error)
                                        }
                                    })
                            }
                        }
                    }
                })
        }

        fun Context.enablePip() {
            Timer().schedule(timerTask {
                ShizukuRunner.command(
                    "dumpsys window displays | grep -E 'mCurrentFocus'",
                    object : ShizukuRunner.CommandResultListener {
                        override fun onCommandResult(output: String, done: Boolean) {
                            if (done) {
                                val pipPackage = output.split(" ")[4].split("/")[0]
                                val metrics = getWindowParams()
                                ShizukuRunner.command("settings put global overlay_display_devices ${metrics.first}x${metrics.second}/240",
                                    object : ShizukuRunner.CommandResultListener {
                                        override fun onCommandResult(
                                            output: String,
                                            done: Boolean
                                        ) {
                                            if (done) {
                                                Timer().schedule(timerTask {
                                                    getExternalDisplayId { newDisplayId->
                                                        val command =
                                                            "am start -n $packageName/${PipStarterActivity::class.java.canonicalName} --es package $pipPackage --display $newDisplayId"
                                                        ShizukuRunner.command(
                                                            command,
                                                            object :
                                                                ShizukuRunner.CommandResultListener {
                                                                override fun onCommandResult(
                                                                    output: String,
                                                                    done: Boolean
                                                                ) {
                                                                    if (done) {
                                                                        println("PIP started")
                                                                    }
                                                                }

                                                                override fun onCommandError(
                                                                    error: String
                                                                ) {
                                                                    disablePip()
                                                                    println(error)
                                                                }
                                                            })
                                                    }
                                                }, 500)
                                            }
                                        }

                                        override fun onCommandError(error: String) {
                                            disablePip()
                                            println(error)
                                        }
                                    })
                            }
                        }
                    })
            }, 1500)
        }

        fun disablePip() {
            ShizukuRunner.command("settings put global overlay_display_devices null",
                object : ShizukuRunner.CommandResultListener {
                    override fun onCommandResult(output: String, done: Boolean) {
                        playVideo()
                    }
                })
        }

        fun playVideo() {
            Timer().schedule(timerTask {
                ShizukuRunner.command("input keyevent ${KeyEvent.KEYCODE_MEDIA_PLAY}",
                    object : ShizukuRunner.CommandResultListener {})
            }, 1500)
        }
    }
}