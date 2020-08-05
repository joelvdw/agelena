package ch.hepia.jodlike.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import ch.hepia.agelena.Agelena
import ch.hepia.jodlike.Global
import ch.hepia.jodlike.MainActivity
import ch.hepia.jodlike.Message
import ch.hepia.jodlike.R
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

/**
 * Message conversation fragment
 */
class MessagesFragment : Fragment() {
    val FCT_ID = 27463
    val messages = mutableMapOf<Int, View>()
    var lastDate: Calendar? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_messages, container, false)

        val key = arguments?.getString("channel")
        Global.channels[key]?.unread = 0
        val channel = Global.channels[key]
        if (channel == null) {
            activity?.onBackPressed()
            return null
        }

        Global.listeners[FCT_ID] = {
            if (it.channel == channel.name) {
                Global.channels[key]?.unread = 0
                displayMessage(inflater, view, it, false)
            }
        }
        Global.sendCallback = { id: Int, success: Boolean ->
            if (success) {
                activity?.runOnUiThread {
                    messages[id]?.findViewById<ImageView>(R.id.imvSent)?.visibility = View.VISIBLE
                }
            } else {
                activity?.runOnUiThread {
                    messages[id]?.findViewById<ImageView>(R.id.imvFailed)?.visibility = View.VISIBLE
                }
            }
        }

        (activity as MainActivity).setToolbarTitle(" ${channel.name} ")
        (activity as AppCompatActivity).supportActionBar!!.setHomeButtonEnabled(true)
        (activity as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        channel.messages.forEach {
            displayMessage(inflater, view, it, it.userId == Agelena.getUserID())
        }

        view.findViewById<ImageView>(R.id.imbSend).setOnClickListener {
            val edt = view.findViewById<EditText>(R.id.edtText)
            if (edt.text.isNotEmpty()) {
                val text = edt.text.toString().trim()
                edt.text.clear()

                val map = mapOf<String, Any>(
                    "username" to Global.username!!,
                    "channel" to channel.name,
                    "text" to text
                )
                val msgA = ch.hepia.agelena.message.Message.Builder().setContent(map).build()

                val msg = Message.fromAgelenaMessage(msgA, false)!!
                Global.channels[channel.name]?.messages?.add(msg)
                displayMessage(inflater, view, msg, true)

                Agelena.sendBroadcastMessage(msgA, Global.TTL)

            }
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Global.listeners.remove(FCT_ID)
    }

    private fun displayMessage(inflater: LayoutInflater, root: View, msg: Message, self: Boolean) {
        if (lastDate == null || lastDate?.get(Calendar.DAY_OF_YEAR) != msg.date.get(Calendar.DAY_OF_YEAR)) {
            lastDate = msg.date
            val dateView = inflater.inflate(
                R.layout.chat_date,
                root.findViewById(R.id.lnlMessages),
                false
            )
            val format = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            dateView.findViewById<TextView>(R.id.txvDate).text = format.format(msg.date.time)
            root.findViewById<LinearLayout>(R.id.lnlMessages).addView(dateView)
        }

        val msgView = inflater.inflate(
            if (self) R.layout.element_message_self else R.layout.element_message,
            root.findViewById(R.id.lnlMessages),
            false
        )
        msgView.findViewById<TextView>(R.id.txvText).text = msg.text
        if (!self) {
            msgView.findViewById<TextView>(R.id.txvUser).let {
                it.text = msg.username
                val color = Global.colors[msg.userId] ?: run {
                    val c: Int = Color.argb(255,
                        Random.nextInt(256),
                        Random.nextInt(256),
                        Random.nextInt(256)
                    )
                    Global.colors[msg.userId] = c
                    c
                }

                it.setTextColor(color)
            }
        }
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        msgView.findViewById<TextView>(R.id.txvHour).text = format.format(msg.date.time)

        if (msg.sent) {
            msgView.findViewById<ImageView>(R.id.imvSent)?.visibility = View.VISIBLE
        }

        root.findViewById<LinearLayout>(R.id.lnlMessages).addView(msgView)

        if (self) {
            messages[msg.messageId] = msgView
        }

        GlobalScope.launch {
            delay(20)
            activity?.runOnUiThread {
                root.findViewById<ScrollView>(R.id.scvMsgs).fullScroll(View.FOCUS_DOWN)
            }
        }
    }
}