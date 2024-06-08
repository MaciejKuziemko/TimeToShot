package com.example.aplikacja.timetoshot.kotlin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacja.timetoshot.R
import com.example.aplikacja.timetoshot.databinding.FragmentHistoryBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

class HistoryFragment : BaseFragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var dataList: MutableList<MainFragment.Shots>
    private lateinit var adapter: ShotsAdapter

    private var _binding: FragmentHistoryBinding? = null

    private val binding: FragmentHistoryBinding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setProgressBar(binding.progressBar)
        auth = Firebase.auth

        with(binding) {
            signOutButton.setOnClickListener { signOut() }
            infoIcon.setOnClickListener {
                showInformationDialog()
            }

            exitAppButton.setOnClickListener {
                showExitConfirmationDialog()
            }
            historyButton.setOnClickListener{
                findNavController().navigate(R.id.action_main)
            }
        }

        db = FirebaseFirestore.getInstance()
        recyclerView = requireView().findViewById(R.id.result)
        recyclerView.layoutManager = LinearLayoutManager(context)
        dataList = mutableListOf()
        adapter = ShotsAdapter(dataList)
        recyclerView.adapter = adapter

        fetchData()
    }

    private fun fetchData() {
        db.collection("shots")
            .orderBy("data",Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val item = document.toObject(MainFragment.Shots::class.java)
                    dataList.add(item)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error getting documents: $exception", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }

    private fun signOut() {
        auth.signOut()
        findNavController().navigate(R.id.action_emailpassword)
    }

    private fun showExitConfirmationDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Exit Confirmation")
            .setMessage("Are you sure you want to exit the application?")
            .setPositiveButton("Yes") { dialog, which ->
                // Zamknięcie aplikacji po kliknięciu "Yes"
                requireActivity().finish()
            }
            .setNegativeButton("No") { dialog, which ->
                // Anulowanie zamknięcia aplikacji po kliknięciu "No"
                dialog.dismiss()
            }
        builder.show()
    }
    private fun showInformationDialog() {
        val dialogMessage = "You can try your shooting skills with us. Click start, when you are ready to shoot. We can track your first shot and show you when you got your first target down"
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("How it works?")
            .setMessage(dialogMessage)
            .setPositiveButton("OK", null)
        val dialog = builder.create()
        dialog.show()
    }

    private fun updateUI(user: FirebaseUser?) {
        hideProgressBar()
        if (user != null) {
            binding.signedInButtons.visibility = View.VISIBLE
        } else {
            binding.signedInButtons.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
