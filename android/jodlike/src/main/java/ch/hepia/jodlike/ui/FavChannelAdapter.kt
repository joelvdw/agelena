package ch.hepia.jodlike.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import ch.hepia.jodlike.*

/**
 * Adapter used to display favorite channels
 */
class FavChannelAdapter(private val channels: MutableList<Channel>, private val navController: NavController): RecyclerView.Adapter<FavChannelAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var channelElement : ConstraintLayout = itemView.findViewById(R.id.cslChannel)
        var txvTitle: TextView = itemView.findViewById(R.id.txvTitle)
        var txvMsgCount: TextView = itemView.findViewById(R.id.txvMsgCounter)
        var crdUnread: CardView = itemView.findViewById(R.id.cdvUnread)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val view: View = inflater.inflate(R.layout.element_fav_channel, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = channels.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.txvTitle.text = channels[position].name
        holder.crdUnread.visibility = if (channels[position].unread > 0) View.VISIBLE else View.GONE
        holder.txvMsgCount.text = channels[position].unread.toString()
        holder.channelElement.setOnClickListener {
            Global.location = Locations.Messages
            val b = Bundle()
            b.putString("channel", channels[position].name)
            navController.navigate(R.id.action_Home_to_messagesFragment, b)
        }
    }

    fun newMessage(msg: Message) {
        channels.find { it.name == msg.channel }?.let {
            notifyItemChanged(channels.indexOf(it))
        }
    }
}