package ch.hepia.jodlike.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ch.hepia.jodlike.Channel
import ch.hepia.jodlike.Global
import ch.hepia.jodlike.Message
import ch.hepia.jodlike.R

/**
 * Adapter used to display all channels
 */
class AllChannelAdapter(private val channels: MutableList<Channel>, private val context: Context): RecyclerView.Adapter<AllChannelAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var txvTitle: TextView = itemView.findViewById(R.id.txvTitle)
        var imbAdd: ImageButton = itemView.findViewById(R.id.imbAdd)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val view: View = inflater.inflate(R.layout.element_all_channel, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = channels.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.txvTitle.text = channels[position].name
        if (channels[position].unread > 0)
            holder.txvTitle.setTextColor(context.getColor(R.color.colorPrimary))

        holder.imbAdd.setImageDrawable(if (channels[position].favorite) context.getDrawable(R.drawable.ic_baseline_remove_24) else context.getDrawable(R.drawable.ic_baseline_add_24))
        holder.imbAdd.setOnClickListener {
            val v = !channels[position].favorite
            Global.channels[channels[position].name]?.favorite = v
            channels[position].favorite = v
            holder.imbAdd.setImageDrawable(if (!v) context.getDrawable(R.drawable.ic_baseline_add_24) else context.getDrawable(R.drawable.ic_baseline_remove_24))
        }
    }

    fun newMessage(msg: Message) {
        val ch = channels.find { it.name == msg.channel }
        if (ch == null) {
            channels.add(0, Global.channels[msg.channel]
                ?: Channel(msg.channel, mutableListOf(), unread = 1, favorite = false))
            notifyItemInserted(0)
        } else {
            val i = channels.indexOf(ch)
            notifyItemChanged(i)
        }
    }
}