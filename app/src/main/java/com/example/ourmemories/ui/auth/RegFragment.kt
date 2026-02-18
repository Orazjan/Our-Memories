package com.example.ourmemories.ui.auth

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.R
import com.example.ourmemories.ui.entering.EnterActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class RegFragment : Fragment(R.layout.register_fragment) {

    private lateinit var viewModel: RegisterViewModel

    private var lastClickTime: Long = 0

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    viewModel.handleGoogleLogin(idToken)
                } else {
                    Toast.makeText(context, "Ошибка Google: Токен отсутствует", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: ApiException) {
                Log.e("RegFragment", "Ошибка регистрации", e)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )

        viewModel = ViewModelProvider(this)[RegisterViewModel::class.java]

        val btnRegister = view.findViewById<CardView>(R.id.btn_register)
        val tvRegisterLabel = view.findViewById<TextView>(R.id.tvRegisterLabel)

        val tvGoToLogin = view.findViewById<View>(R.id.tv_login)
        val etEmail = view.findViewById<EditText>(R.id.loginTextView)
        val etPassword = view.findViewById<EditText>(R.id.passwordTextView)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val btnGoogleRegister = view.findViewById<View>(R.id.btnGoogleRegister)


        btnRegister.isEnabled = false
        btnRegister.alpha = 0.5f

        fun validateInputs() {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val isEmailValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
            val isPasswordValid = password.length >= 6

            btnRegister.isEnabled = isEmailValid && isPasswordValid
            btnRegister.alpha = if (btnRegister.isEnabled) 1.0f else 0.5f

            if (isEmailValid) etEmail.error = null
            if (isPasswordValid) etPassword.error = null
        }

        fun attemptRegister() {
            if (SystemClock.elapsedRealtime() - lastClickTime < 1000) return
            lastClickTime = SystemClock.elapsedRealtime()

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = getString(R.string.error_invalid_credentials)
                etEmail.requestFocus()
                return
            }

            if (password.length < 6) {
                etPassword.error =
                    getString(R.string.error_invalid_credentials)
                etPassword.requestFocus()
                return
            }

            hideKeyboard(view)
            viewModel.register(email, password)
        }



        etEmail.doAfterTextChanged { validateInputs() }
        etPassword.doAfterTextChanged { validateInputs() }

        btnRegister.setOnClickListener {
            attemptRegister()
        }

        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                if (btnRegister.isEnabled) {
                    attemptRegister()
                }
                true
            } else {
                false
            }
        }

        tvGoToLogin.setOnClickListener {
            (requireActivity() as? EnterActivity)?.showLogin()
        }

        btnGoogleRegister.setOnClickListener {
            if (SystemClock.elapsedRealtime() - lastClickTime < 1000) return@setOnClickListener
            lastClickTime = SystemClock.elapsedRealtime()
            signInWithGoogle()
        }


        observeViewModel(progressBar, btnRegister, tvRegisterLabel, etEmail, etPassword)
    }


    private fun signInWithGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)).requestEmail().build()
        val client = GoogleSignIn.getClient(requireActivity(), gso)
        client.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(client.signInIntent)
        }
    }

    private fun observeViewModel(
        progressBar: ProgressBar, btn: CardView, btnLabel: TextView, et1: EditText, et2: EditText
    ) {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

            if (isLoading) {
                btnLabel.visibility = View.INVISIBLE
                btn.isEnabled = false
                btn.alpha = 0.5f
            } else {
                btnLabel.visibility = View.VISIBLE
                btnLabel.text = getString(R.string.registrating)

                val email = et1.text.toString().trim()
                val pass = et2.text.toString().trim()
                val isValid = Patterns.EMAIL_ADDRESS.matcher(email).matches() && pass.length >= 6

                btn.isEnabled = isValid
                btn.alpha = if (isValid) 1.0f else 0.5f
            }

            et1.isEnabled = !isLoading
            et2.isEnabled = !isLoading
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                viewModel.onToastShown()
            }
        }

        viewModel.authSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                (requireActivity() as? EnterActivity)?.onRegistrationSuccess()
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}