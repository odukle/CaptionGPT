package com.odukle.captiongpt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.RecyclerView
import com.odukle.captiongpt.databinding.ItemVewCaptionBinding

class CaptionsAdapter(private val captionList: List<String>, private val context: Context): RecyclerView.Adapter<CaptionsAdapter.CaptionViewHolder>() {

    inner class CaptionViewHolder(binding: ItemVewCaptionBinding): RecyclerView.ViewHolder(binding.root) {
        val tvCaption = binding.tvCaption
        val btnCopy = binding.btnCopy
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CaptionViewHolder {
        val binding = ItemVewCaptionBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return CaptionViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return captionList.size
    }

    override fun onBindViewHolder(holder: CaptionViewHolder, position: Int) {
        val text = captionList[position].trim()
        holder.tvCaption.text = text

        holder.btnCopy.setOnClickListener {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("Label", text)
            clipboardManager.setPrimaryClip(clipData)
            context.shortToast("Copied to clipboard 🗒️")
        }
    }
}