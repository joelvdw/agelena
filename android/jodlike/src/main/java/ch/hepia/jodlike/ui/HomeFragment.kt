package ch.hepia.jodlike.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.hepia.agelena.Agelena
import ch.hepia.agelena.Device
import ch.hepia.agelena.MessageListener
import ch.hepia.agelena.StateListener
import ch.hepia.jodlike.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Home fragment displaying favorite channels
 */
class HomeFragment : Fragment() {
    private val FCT_ID = 1262

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as MainActivity).setToolbarTitle(getString(R.string.app_name))
        (activity as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(false)

        view.findViewById<ConstraintLayout>(R.id.cslCreation).let {
            it.visibility = View.GONE
            it.setOnClickListener {
                (activity as AppCompatActivity).onBackPressed()
            }
        }
        view.findViewById<LinearLayout>(R.id.lnlCreation).setOnClickListener {  }

        if (arguments?.getBoolean("add") == true) {
            Global.location = Locations.Plus
            displayAdd(view)
        }
        Global.displayPanel = { displayAdd(view) }
        Global.hidePanel = { hideAdd(view) }

        view.findViewById<Button>(R.id.btnCreateChan).setOnClickListener {
            val edt = view.findViewById<EditText>(R.id.edtChanName)
            if (edt.text.isNotEmpty()) {
                val name = edt.text.toString().trim()
                if (!Global.channels.containsKey(name)) {
                    Global.channels[name] = Channel(name, mutableListOf(),
                        unread = 0,
                        favorite = true
                    )
                }
                edt.text.clear()

                // Hide keyboard
                val imm: InputMethodManager =
                    requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.rootView.windowToken, 0)

                activity?.onBackPressed()
                GlobalScope.launch {
                    delay(100)
                    Global.location = Locations.Messages
                    val b = Bundle()
                    b.putString("channel", name)
                    findNavController().navigate(R.id.action_Home_to_messagesFragment, b)
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.chan_name_needed), Toast.LENGTH_SHORT).show()
            }
        }

        val rcv = view.findViewById<RecyclerView>(R.id.rcvChannels)
        val favs = Global.channels.values.filter { it.favorite }.toMutableList()
        val adapter = FavChannelAdapter(favs, findNavController())
        rcv.layoutManager = LinearLayoutManager(requireContext())
        rcv.adapter = adapter

        view.findViewById<TextView>(R.id.txvNoChannel).visibility =
            if (favs.isEmpty()) View.VISIBLE else View.GONE

        Global.listeners[FCT_ID] = {
            adapter.newMessage(it)
        }

        startComponents()
    }

    private fun displayAdd(root: View) {
        root.findViewById<ImageButton>(R.id.imbPlus).setColorFilter(requireContext().getColor(R.color.tintColor))
        root.findViewById<ImageButton>(R.id.imbHome).setColorFilter(requireContext().getColor(R.color.noTintColor))

        root.findViewById<ConstraintLayout>(R.id.cslCreation).visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_down)
        root.findViewById<LinearLayout>(R.id.lnlCreation).startAnimation(anim)
    }

    private fun hideAdd(root: View) {
        root.findViewById<ImageButton>(R.id.imbPlus).setColorFilter(requireContext().getColor(R.color.noTintColor))
        root.findViewById<ImageButton>(R.id.imbHome).setColorFilter(requireContext().getColor(R.color.tintColor))

        val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
        root.findViewById<LinearLayout>(R.id.lnlCreation).startAnimation(anim)
        GlobalScope.launch {
            delay(400)
            activity?.runOnUiThread {
                root.findViewById<ConstraintLayout>(R.id.cslCreation).visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Global.listeners.remove(FCT_ID)

        val l = Global.locationsStack.pop()
        if (Global.location == Locations.Plus) {
            Global.locationsStack.pop()
        }
        Global.location = l
    }

    private fun startComponents() {
        Agelena.start()
    }
}
