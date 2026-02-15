package com.example.ourmemories.ui.onboardings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.ourmemories.R
import com.example.ourmemories.data.repositories.MainRepository
import com.example.ourmemories.databinding.FragmentOnboardingStep3Binding
import com.example.ourmemories.ui.entering.EnterActivity
import com.example.ourmemories.ui.main.MainViewModel
import com.example.ourmemories.ui.main.MainViewModelFactory


class OnBoardingStep3Fragment : Fragment() {
    private var _binding: FragmentOnboardingStep3Binding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by viewModels {
        val application = requireActivity().application
        val repository = MainRepository()
        MainViewModelFactory(application, repository)
    }

    private var myInviteCode: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingStep3Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.icHeart.startAnimation(
            AnimationUtils.loadAnimation(
                context, R.anim.heart_beat
            )
        )

        binding.btnCopy.setOnClickListener {
            myInviteCode?.let { code ->
                copyToClipboard(code)
            } ?: run {
                Toast.makeText(context, getString(R.string.code_loading), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLink.setOnClickListener {
            val code = binding.etPartnerCode.text.toString().trim()
            if (code.length == 8) {
                binding.btnLink.isEnabled = false
                viewModel.connectPartner(code, onSuccess = {
                    Toast.makeText(context, getString(R.string.connected), Toast.LENGTH_LONG).show()
                    finishSetup()
                }, onFailure = { error ->
                    binding.btnLink.isEnabled = true
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                })
            } else {
                Toast.makeText(context, "Введите корректный код (8 цифр)", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        binding.btnSkip.setOnClickListener {
            finishSetup()
        }
    }

    private fun observeViewModel() {
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                myInviteCode = user.partnerCode
                binding.tvInviteCode.text = user.partnerCode ?: "..."
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Invite Code", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
    }
//
//    private fun navigateToMain() {
//        val intent = Intent(requireContext(), MainActivity::class.java)
//        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//        startActivity(intent)
//    }

    private fun finishSetup() {
        (requireActivity() as? EnterActivity)?.onAuthSuccess()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}