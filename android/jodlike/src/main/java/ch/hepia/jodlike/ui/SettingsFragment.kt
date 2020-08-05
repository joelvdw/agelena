package ch.hepia.jodlike.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import ch.hepia.jodlike.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

/**
 * Settings and about fragment
 */
class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as MainActivity).setToolbarTitle(getString(R.string.app_name))
        (activity as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(false)

        view.findViewById<TextView>(R.id.txvUsername).text = Global.username ?: ""

        val csl = view.findViewById<ConstraintLayout>(R.id.cslAbout)
        csl.let {
            it.visibility = View.GONE
            it.setOnClickListener {
                (activity as AppCompatActivity).onBackPressed()
            }
        }
        val cslL = view.findViewById<ConstraintLayout>(R.id.cslLang)
        cslL.let {
            it.visibility = View.GONE
            it.setOnClickListener {
                (activity as AppCompatActivity).onBackPressed()
            }
        }
        view.findViewById<LinearLayout>(R.id.lnlAbout).setOnClickListener {  }

        view.findViewById<ImageButton>(R.id.imbAbout).setOnClickListener {
            displayAbout(view)
            Global.location = Locations.About
        }
        Global.hidePanel = {
            hideAbout(view)
            hideLang(view)
        }

        view.findViewById<ImageButton>(R.id.imbLanguage).setOnClickListener {
            displayLang(view)
            Global.location = Locations.Lang
        }
        val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            resources.configuration.locales[0]
        else
            resources.configuration.locale

        view.findViewById<RadioButton>(R.id.rdbEn).isChecked = current.language == Locale.ENGLISH.language
        view.findViewById<RadioButton>(R.id.rdbFr).isChecked = current.language == Locale.FRENCH.language

        view.findViewById<Button>(R.id.btnLang).setOnClickListener {
            val sel = if (view.findViewById<RadioButton>(R.id.rdbEn).isChecked)
                Locale.ENGLISH
            else
                Locale.FRENCH

            if (current.language != sel.language) {
                (activity as MainActivity).setLocale(sel)
            } else
                activity?.onBackPressed()
        }

        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val edt = view.findViewById<EditText>(R.id.edtUsername)
            if (edt.text.isNotEmpty()) {
                val name = edt.text.toString().trim()

                val pref =
                    requireContext().getSharedPreferences(Global.SHARED_PREF, Context.MODE_PRIVATE)
                pref.edit().putString(Global.PREF_USERNAME, name).apply()
                Global.username = name
                view.findViewById<TextView>(R.id.txvUsername).text = name

                edt.text.clear()

                // Hide keyboard
                val imm: InputMethodManager =
                    requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.rootView.windowToken, 0)
            }
        }
    }

    private fun displayAbout(root: View) {
        root.findViewById<ConstraintLayout>(R.id.cslAbout).visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_down)
        root.findViewById<LinearLayout>(R.id.lnlAbout).startAnimation(anim)
    }

    private fun hideAbout(root: View) {
        val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
        root.findViewById<LinearLayout>(R.id.lnlAbout).startAnimation(anim)
        GlobalScope.launch {
            delay(400)
            activity?.runOnUiThread {
                root.findViewById<ConstraintLayout>(R.id.cslAbout).visibility = View.GONE
            }
        }
    }

    private fun displayLang(root: View) {
        root.findViewById<ConstraintLayout>(R.id.cslLang).visibility = View.VISIBLE
        val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_down)
        root.findViewById<LinearLayout>(R.id.lnLang).startAnimation(anim)
    }

    private fun hideLang(root: View) {
        val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
        root.findViewById<LinearLayout>(R.id.lnLang).startAnimation(anim)
        GlobalScope.launch {
            delay(400)
            activity?.runOnUiThread {
                root.findViewById<ConstraintLayout>(R.id.cslLang).visibility = View.GONE
            }
        }
    }
}