package ch.hepia.jodlike.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.navigation.fragment.findNavController
import ch.hepia.jodlike.Global
import ch.hepia.jodlike.Locations
import ch.hepia.jodlike.R

/**
 * Menu fragment used for navigation
 */
class MenuFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.imbHome).let {
            it.setOnClickListener {
                if (Global.location == Locations.Plus) {
                    Global.location = Locations.Home
                    Global.hidePanel?.let { it() }
                } else if (Global.location != Locations.Home) {
                    Global.location = Locations.Home
                    findNavController().navigate(R.id.Home)
                }
            }

            it.setColorFilter(requireContext().getColor(if (Global.location == Locations.Home) R.color.tintColor else R.color.noTintColor))
        }

        view.findViewById<ImageButton>(R.id.imbChannels).let {
            it.setOnClickListener {
                if (Global.location != Locations.All) {
                    Global.location = Locations.All
                    findNavController().navigate(R.id.allChannelsFragment)
                }
            }

            it.setColorFilter(requireContext().getColor(if (Global.location == Locations.All) R.color.tintColor else R.color.noTintColor))
        }

        view.findViewById<ImageButton>(R.id.imbPlus).let {
            it.setOnClickListener { _: View ->
                if (Global.location == Locations.Home) {
                    Global.location = Locations.Plus
                    Global.displayPanel?.let { it() }
                } else if (Global.location != Locations.Plus) {
                    Global.location = Locations.Home
                    val b = Bundle()
                    b.putBoolean("add", true)
                    findNavController().navigate(R.id.Home, b)
                }
            }

            it.setColorFilter(requireContext().getColor(if (Global.location == Locations.Plus) R.color.tintColor else R.color.noTintColor))
        }

        view.findViewById<ImageButton>(R.id.imbSettings).let {
            it.setOnClickListener {
                if (Global.location != Locations.Settings) {
                    Global.location = Locations.Settings
                    findNavController().navigate(R.id.settingsFragment)
                }
            }

            it.setColorFilter(requireContext().getColor(
                if (Global.location == Locations.Settings || Global.location == Locations.About)
                    R.color.tintColor else R.color.noTintColor)
            )
        }
    }
}