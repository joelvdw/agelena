package ch.hepia.jodlike.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.hepia.jodlike.Global
import ch.hepia.jodlike.MainActivity
import ch.hepia.jodlike.R

/**
 * Fragment displaying all nearby channels
 */
class AllChannelsFragment : Fragment() {
    val FCT_ID = 84572

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_all_channels, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as MainActivity).setToolbarTitle(getString(R.string.all_channels))
        (activity as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(false)

        val rcv = view.findViewById<RecyclerView>(R.id.rcvChannels)
        val adapter = AllChannelAdapter(Global.channels.values.toMutableList(), requireContext())
        rcv.layoutManager = LinearLayoutManager(requireContext())
        rcv.adapter = adapter

        Global.listeners[FCT_ID] = {
            adapter.newMessage(it)
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as MainActivity).setUserCounterVisibility(true)
    }

    override fun onStop() {
        super.onStop()
        (activity as MainActivity).setUserCounterVisibility(false)
    }
}