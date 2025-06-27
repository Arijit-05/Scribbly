package com.arijit.notes.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.arijit.notes.R
import com.arijit.notes.utils.AuthListener

class RegisterFragment : Fragment() {
    private lateinit var name: EditText
    private lateinit var email: EditText
    private lateinit var pin: EditText
    private lateinit var joinBtn: CardView
    private var listener: AuthListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is AuthListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement AuthListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_register, container, false)
        name = view.findViewById(R.id.name)
        email = view.findViewById(R.id.email)
        pin = view.findViewById(R.id.pin)
        joinBtn = view.findViewById(R.id.join_btn)

        pin.setOnClickListener {
            Toast.makeText(requireContext(), "Pin must be 4 digits", Toast.LENGTH_SHORT).show()
        }

        joinBtn.setOnClickListener {
            val nameTxt = name.text.toString().trim()
            val emailTxt = email.text.toString().trim()
            val pinTxt = pin.text.toString().trim()

            when {
                nameTxt.isEmpty() ->
                    Toast.makeText(requireContext(), "Hey We need a name to call you", Toast.LENGTH_SHORT).show()

                emailTxt.isEmpty() ->
                    Toast.makeText(requireContext(), "We need your email! Chill we won't be spamming", Toast.LENGTH_SHORT).show()
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(emailTxt).matches() ->
                        Toast.makeText(requireContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show()

                pinTxt.isEmpty() || pinTxt.length != 4 ->
                    Toast.makeText(requireContext(), "Give a proper 4-digit pin", Toast.LENGTH_SHORT).show()

                else ->
                    listener?.onRegisterSubmit(nameTxt, emailTxt, pinTxt)
            }
        }

        return view
    }

}