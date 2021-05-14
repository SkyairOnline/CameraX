package com.example.camerax

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

typealias InflateActivity<T> = (LayoutInflater) -> T
abstract class BaseActivity<T : ViewBinding>(
    private val inflate: InflateActivity<T>
) : AppCompatActivity() {

    private var _binding: T? = null
    val binding get() = _binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = inflate.invoke(layoutInflater)
        setContentView(binding?.root)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}