package com.example.aplikacja.timetoshot.kotlin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aplikacja.timetoshot.R

class ShotsAdapter(private val shotsList: List<MainFragment.Shots>) : RecyclerView.Adapter<ShotsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shot, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val shots = shotsList[position]
        holder.bind(shots)
    }

    override fun getItemCount(): Int {
        return shotsList.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        private val firstShotTimeTextView: TextView = itemView.findViewById(R.id.firstShotTimeTextView)
        private val recordTimeTextView: TextView = itemView.findViewById(R.id.recordTimeTextView)
        private val numberOfShotsTextView: TextView = itemView.findViewById(R.id.numberOfShotsTextView)
        private val avgTimePerShotTextView: TextView = itemView.findViewById(R.id.avgTimePerShotTextView)

        fun bind(shots: MainFragment.Shots) {
            dateTextView.text = shots.data!!.toDate().toString()
            firstShotTimeTextView.text = "First Shot Time: ${shots.firstShotTime ?: "N/A"}"
            recordTimeTextView.text = "Record Time: ${shots.recordTime ?: "N/A"}"
            numberOfShotsTextView.text = "Number of Shots: ${shots.numberOfShots ?: "N/A"}"
            avgTimePerShotTextView.text = "Avg Time Per Shot: ${shots.avgTimePerShot ?: "N/A"}"
        }
    }
}