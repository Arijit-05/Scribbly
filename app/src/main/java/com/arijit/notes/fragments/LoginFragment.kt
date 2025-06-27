package com.arijit.notes.fragments

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.arijit.notes.R
import com.arijit.notes.utils.AuthListener
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {
    private lateinit var email: EditText
    private lateinit var pin: EditText
    private lateinit var loginBtn: CardView
    private lateinit var forgotPin: TextView
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
        val view = inflater.inflate(R.layout.fragment_login, container, false)
        email = view.findViewById(R.id.email)
        pin = view.findViewById(R.id.pin)
        loginBtn = view.findViewById(R.id.login_btn)
        forgotPin = view.findViewById(R.id.forgot_pin)

        loginBtn.setOnClickListener {
            val emailTxt: String = email.text.toString().trim()
            val pinTxt: String = pin.text.toString().trim()

            when {
                emailTxt.isEmpty() -> {
                    Toast.makeText(requireContext(), "Email cannot be empty", Toast.LENGTH_SHORT)
                        .show()
                }

                !android.util.Patterns.EMAIL_ADDRESS.matcher(emailTxt).matches() -> {
                    Toast.makeText(
                        requireContext(),
                        "Please enter a valid email address",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                pinTxt.isEmpty() || pinTxt.length != 4 -> {
                    Toast.makeText(requireContext(), "Enter a valid pin", Toast.LENGTH_SHORT).show()
                }

                else -> {
                    listener?.onLoginSubmit(emailTxt, pinTxt)
                }
            }
        }

        forgotPin.setOnClickListener {
            val emailTxt = email.text.toString().trim()
            if (emailTxt.isEmpty()) {
                Toast.makeText(requireContext(), "Enter your email address first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailTxt).matches()) {
                Toast.makeText(requireContext(), "Enter a valid email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Reset PIN Instructions")
                .setMessage(
                    "We'll send a reset link to your email.\n\n" +
                            "While setting a new PIN from the reset link, wrap it like this:\n\n" +
                            "##1234## (for PIN 1234).\n\n" +
                            "In the app, just enter your normal 4-digit PIN."
                )
                .setPositiveButton("Ok") { _, _ ->
                    FirebaseAuth.getInstance().sendPasswordResetEmail(emailTxt)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Reset email sent. Check your inbox.", Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(), "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return view
    }

}