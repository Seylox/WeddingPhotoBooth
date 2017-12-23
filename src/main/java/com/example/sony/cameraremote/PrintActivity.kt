package com.example.sony.cameraremote

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import com.squareup.picasso.Picasso
import timber.log.Timber
import java.io.File

class PrintActivity : AppCompatActivity() {

    val mImageView by lazy {
        findViewById<ImageView>(R.id.imageView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_print)

        val filename = intent.getStringExtra("IMAGEFILENAME")

        Timber.d("--- file from intent: $filename")

        val file = File(filename)
        Timber.d("--- file actually: $file")
        Picasso.with(this).isLoggingEnabled = true
        Picasso.with(this).load(file).into(mImageView)
    }
}
