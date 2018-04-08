package com.example.sony.cameraremote

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v4.print.PrintHelper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import com.squareup.picasso.Picasso
import timber.log.Timber
import java.io.File


class PrintActivity : Activity() {

    val mImageView by lazy {
        findViewById<ImageView>(R.id.imageView)
    }

    val deletePicturesButton by lazy {
        findViewById<Button>(R.id.delete_pictures_button)
    }

    val printPicturesButton by lazy {
        findViewById<Button>(R.id.print_pictures_button)
    }

    lateinit var fileToPrintString: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_print)

        // disable screen timeout while app is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val filename = intent.getStringExtra("IMAGEFILENAME")
        fileToPrintString = filename

        Timber.d("--- file from intent: $filename")

        val file = File(filename)

        Timber.d("--- file actually: $file")
        Picasso.with(this).isLoggingEnabled = true
        Picasso.with(this).load(file).into(mImageView)
    }

    private fun doPhotoPrint() {
        // TODO instead of printing this i need to move it to a separate folder!

        val photoPrinter = PrintHelper(this)
        photoPrinter.scaleMode = PrintHelper.SCALE_MODE_FILL
        val bitmapToPrint = BitmapFactory.decodeFile(fileToPrintString)
        photoPrinter.printBitmap("PictureToPrint", bitmapToPrint)

//        val bitmap = BitmapFactory.decodeResource(resources,
//                R.drawable.droids)
//        photoPrinter.printBitmap("droids.jpg - test print", bitmap)
    }

    public fun onClickDeletePicturesButton(view: View) {
        finish()
    }

    public fun onClickPrintPicturesButton(view: View) {
        // TODO!
//        doPhotoPrint()
    }

    /**
     * Use Sticky Immersive Mode
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }
}
