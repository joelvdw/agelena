package ch.hepia.jodlike.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ch.hepia.jodlike.Global
import ch.hepia.jodlike.MainActivity
import ch.hepia.jodlike.R

/**
 * Fragment used a the first screen to choose a username
 */
class FirstLaunchFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_launch, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnStart).setOnClickListener {
            val edt = view.findViewById<EditText>(R.id.edtUsername)
            if (edt.text.isNotEmpty()) {
                val pref =
                    requireContext().getSharedPreferences(Global.SHARED_PREF, Context.MODE_PRIVATE)
                pref.edit().putString(Global.PREF_USERNAME, edt.text.toString().trim()).apply()
                Global.username = edt.text.toString().trim()

                // Hide keyboard
                val imm: InputMethodManager =
                    requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.rootView.windowToken, 0)

                findNavController().navigate(R.id.action_FirstLaunch_to_blankFragment)
                (activity as MainActivity).managePerms()
            } else {
                Toast.makeText(requireContext(), getString(R.string.username_needed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity?)!!.supportActionBar!!.hide()
    }

    override fun onStop() {
        super.onStop()
        (activity as AppCompatActivity?)!!.supportActionBar!!.show()
    }
}
