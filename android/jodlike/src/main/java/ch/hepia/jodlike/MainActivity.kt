package ch.hepia.jodlike

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import ch.hepia.agelena.Agelena
import ch.hepia.agelena.Device
import ch.hepia.agelena.MessageListener
import ch.hepia.agelena.StateListener
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

/**
 * Main activity. Manages permissions, navigation and application starting
 */
class MainActivity : AppCompatActivity() {

    private val stateListener = object : StateListener {
        override fun onStartError() {
            Toast.makeText(applicationContext, getString(R.string.failed_start), Toast.LENGTH_SHORT).show()
        }

        override fun onDeviceConnected(device: Device) {
            Global.nbDevicesNearby += 1
            updateUserCounter()
        }

        override fun onDeviceLost(device: Device) {
            Global.nbDevicesNearby -= 1
            updateUserCounter()
        }
    }

    private val messageListener = object : MessageListener {
        override fun onBroadcastMessageReceived(message: ch.hepia.agelena.message.Message) {
            val msg = Message.fromAgelenaMessage(
                message, true
            ) ?: return

            if (Global.channels.containsKey(msg.channel)) {
                Global.channels[msg.channel]?.let { it.unread += 1 }
                Global.channels[msg.channel]?.messages?.add(msg)
            } else {
                Global.channels[msg.channel] =
                    Channel(msg.channel, mutableListOf(msg), unread = 1, favorite = false)
            }

            Global.listeners.forEach {
                it.value(msg)
            }
        }

        override fun onMessageSent(message: ch.hepia.agelena.message.Message) {
            Global.channels.values.find { c ->
                c.messages.any { it.messageId == message.id }
            }?.messages?.find { it.messageId == message.id }?.sent = true

            Global.sendCallback?.let {
                it(message.id, true)
            }
        }

        override fun onMessageFailed(message: ch.hepia.agelena.message.Message, errorCode: Int) {
            Log.e("Jodlike", "Failed to send message ${message.id}. Error: $errorCode")
            Global.sendCallback?.let {
                it(message.id, false)
            }
        }
    }

    private fun updateUserCounter() {
        setUserCounter(Global.nbDevicesNearby)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        initActivity()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Change application language
     */
    fun setLocale(locale: Locale) {
        val res: Resources = resources
        val dm: DisplayMetrics = res.displayMetrics
        val conf = Configuration(res.configuration)

        Global.locale = locale
        Locale.setDefault(locale)
        conf.setLocale(locale)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
            applicationContext.createConfigurationContext(conf)
        } else {
            resources.updateConfiguration(conf, dm)
        }

        val refresh = Intent(this, MainActivity::class.java)
        finish()
        startActivity(refresh)
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            overrideConfiguration.setLocale(Global.locale)
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(updateBaseContextLocale(base))
    }

    private fun updateBaseContextLocale(context: Context?): Context? {
        context ?: return context

        Locale.setDefault(Global.locale)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
            return updateResourcesLocale(context, Global.locale)
        }

        return updateResourcesLocaleLegacy(context, Global.locale)
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private fun updateResourcesLocale(context: Context, locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    @SuppressWarnings("deprecation")
    private fun updateResourcesLocaleLegacy(context: Context, locale: Locale): Context {
        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        configuration.locale = locale
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return context
    }

    /**
     * Request all needed permissions
     * @return If all permissions are granted
     */
    private fun requestPermissions(): Boolean {
        var all = true
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADMIN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
                ),
                0
            )
            all = false
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                2
            )
            all = false
        }

        return all
    }

    /**
     * Init activity and search for a saved username
     */
    private fun initActivity() {
        fab.hide()
        setUserCounter(0)
        setUserCounterVisibility(false)
        supportActionBar!!.setDisplayHomeAsUpEnabled(false)
        supportActionBar!!.title = ""

        val pref = applicationContext.getSharedPreferences(Global.SHARED_PREF, Context.MODE_PRIVATE)

        if (pref.contains(Global.PREF_CHANNELS)) {
            pref.getStringSet(Global.PREF_CHANNELS, mutableSetOf())?.forEach {
                if (it != null && it.isNotBlank()) {
                    Global.channels[it] = Channel(it, mutableListOf(),
                        unread = 0,
                        favorite = true
                    )
                }
            }
        }

        val user = pref.getString(Global.PREF_USERNAME, "") ?: ""
        if (user == "") {
            findNavController(R.id.nav_host_fragment).navigate(R.id.action_blankFragment_to_FirstLaunch)
            return
        } else {
            Global.username = user
            managePerms()
        }
    }

    fun managePerms() {
        if (requestPermissions()) {
            startAgelena()
        }
    }

    private fun startAgelena() {
        var e = Agelena.initialize(applicationContext, stateListener, messageListener)
        if (e != Agelena.SUCCESS)
            Toast.makeText(
                applicationContext,
                "Agelena failed to start. Error: $e",
                Toast.LENGTH_SHORT
            ).show()

        GlobalScope.launch {
            while (e != Agelena.SUCCESS) {
                delay(2000)
                e = Agelena.initialize(applicationContext, stateListener, messageListener)
            }

            Global.locationsStack.clear()
            Global.location = Locations.Home
            findNavController(R.id.nav_host_fragment).navigate(R.id.action_blankFragment_to_Home)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            managePerms()
        } else {
            Toast.makeText(this, getString(R.string.permission_msg), Toast.LENGTH_LONG).show()
        }
    }

    fun setToolbarTitle(text: String) {
        findViewById<TextView>(R.id.toolbar_title).text = text
    }

    fun setUserCounter(value: Int) {
        runOnUiThread {
            findViewById<TextView>(R.id.txvNearby).text = value.toString()
        }
    }

    fun setUserCounterVisibility(visible: Boolean) {
        runOnUiThread {
            findViewById<LinearLayout>(R.id.lnlCounter).visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    override fun onBackPressed() {
        if (Global.locationsStack.size > 1) {
            val l = Global.locationsStack.pop()

            if (l == Locations.Plus || l == Locations.About || l == Locations.Lang) {
                Global.hidePanel?.let { it() }
            } else {
                super.onBackPressed()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Agelena.close()

        val pref =
            applicationContext.getSharedPreferences(Global.SHARED_PREF, Context.MODE_PRIVATE)
        pref.edit().putStringSet(Global.PREF_CHANNELS, Global.channels.filter { it.value.favorite }.keys).apply()
    }
}
