package com.example.ourmemories.ui.auth

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.R
import com.example.ourmemories.databinding.LoginFragmentBinding
import com.example.ourmemories.ui.entering.EnterActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class LoginFragment : Fragment() {

    private var _binding: LoginFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: LoginViewModel
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
                    Toast.makeText(context, "Ошибка Google: Token is null", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: ApiException) {
                Log.e("LoginFragment", "Google sign in failed", e)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LoginFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        binding.btnLogin.isEnabled = false
        binding.btnLogin.alpha = 0.5f

        fun validateInputs() {
            val email = binding.loginTextView.text.toString().trim()
            val password = binding.passwordTextView.text.toString().trim()

            val isEmailValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
            val isPasswordValid = password.length >= 6

            binding.btnLogin.isEnabled = isEmailValid && isPasswordValid
            binding.btnLogin.alpha = if (binding.btnLogin.isEnabled) 1.0f else 0.5f

            if (isEmailValid) binding.loginTextView.error = null
            if (isPasswordValid) binding.passwordTextView.error = null
        }

        fun attemptLogin() {
            if (SystemClock.elapsedRealtime() - lastClickTime < 1000) return
            lastClickTime = SystemClock.elapsedRealtime()

            val email = binding.loginTextView.text.toString().trim()
            val password = binding.passwordTextView.text.toString().trim()

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.loginTextView.error = getString(R.string.error_invalid_credentials)
                binding.loginTextView.requestFocus()
                return
            }

            if (password.length < 6) {
                binding.passwordTextView.error = getString(R.string.error_invalid_credentials)
                return
            }

            hideKeyboard(view)
            viewModel.login(email, password)
        }

        binding.loginTextView.doAfterTextChanged { validateInputs() }
        binding.passwordTextView.doAfterTextChanged { validateInputs() }

        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }

        binding.passwordTextView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                if (binding.btnLogin.isEnabled) {
                    attemptLogin()
                }
                true
            } else {
                false
            }
        }

        binding.tvSignUp.setOnClickListener {
            (activity as? EnterActivity)?.showRegistration()
        }

        binding.btnForgotPassword.setOnClickListener {
            (activity as? EnterActivity)?.showForgotPassword()
        }

        binding.btnGoogleLogin.setOnClickListener {
            if (SystemClock.elapsedRealtime() - lastClickTime < 1000) return@setOnClickListener
            lastClickTime = SystemClock.elapsedRealtime()
            signInWithGoogle()
        }

        observeViewModel()
    }

    private fun signInWithGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)).requestEmail().build()

        val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

            if (isLoading) {
                binding.tvLoginLabel.visibility = View.INVISIBLE
                binding.btnLogin.isEnabled = false
                binding.btnLogin.alpha = 0.5f
            } else {
                binding.tvLoginLabel.visibility = View.VISIBLE

                val email = binding.loginTextView.text.toString().trim()
                val pass = binding.passwordTextView.text.toString().trim()
                val isValid = Patterns.EMAIL_ADDRESS.matcher(email).matches() && pass.length >= 6

                binding.btnLogin.isEnabled = isValid
                binding.btnLogin.alpha = if (isValid) 1.0f else 0.5f
            }

            binding.loginTextView.isEnabled = !isLoading
            binding.passwordTextView.isEnabled = !isLoading
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                viewModel.onToastShown()
            }
        }

        viewModel.loginSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                (requireActivity() as? EnterActivity)?.onAuthSuccess()
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}