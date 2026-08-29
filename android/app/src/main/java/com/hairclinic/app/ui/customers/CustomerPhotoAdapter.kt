package com.hairclinic.app.ui.customers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hairclinic.app.data.ApiClient
import com.hairclinic.app.data.CustomerPhoto
import com.hairclinic.app.databinding.ItemCustomerPhotoBinding

class CustomerPhotoAdapter(
    private val onClick: (CustomerPhoto) -> Unit,
    private val onDelete: (CustomerPhoto) -> Unit,
) : RecyclerView.Adapter<CustomerPhotoAdapter.VH>() {
    private val items = mutableListOf<CustomerPhoto>()

    fun submit(data: List<CustomerPhoto>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCustomerPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class VH(private val binding: ItemCustomerPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: CustomerPhoto) {
            val context = binding.root.context
            val url = ApiClient.photoUrl(context, photo.url)
            binding.photoImage.load(url, ApiClient.imageLoader(context)) {
                crossfade(true)
            }
            val date = photo.dateText()
            binding.photoDate.text = date
            binding.photoDate.isVisible = date.isNotBlank()
            binding.photoImage.isClickable = true
            binding.photoImage.isFocusable = true
            binding.photoImage.setOnClickListener { onClick(photo) }
            binding.photoDate.setOnClickListener { onClick(photo) }
            binding.root.setOnClickListener { onClick(photo) }
            binding.deleteBtn.setOnClickListener { onDelete(photo) }
        }
    }
}
