package com.example.sony.cameraremote

import android.app.Activity
import android.content.Context
import android.content.Intent
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

    companion object {
        private const val EXTRA_COMPLETE_FILENAME = "EXTRA_COMPLETE_FILENAME"
        private const val EXTRA_COLLAGES_PATHNAME = "EXTRA_COLLAGES_PATHNAME"
        private const val EXTRA_SIMPLE_FILENAME = "EXTRA_SIMPLE_FILENAME"

        fun buildPrintStartActivityIntent(context: Context,
                                          completeFileName: String,
                                          collagesPathName: String,
                                          simpleFileName: String): Intent {
            val intent = Intent(context, PrintActivity::class.java)
            intent.putExtra(EXTRA_COMPLETE_FILENAME, completeFileName)
            intent.putExtra(EXTRA_COLLAGES_PATHNAME, collagesPathName)
            intent.putExtra(EXTRA_SIMPLE_FILENAME, simpleFileName)
            return intent
        }
    }

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

        // collect information from intent extras
        val filename = intent.getStringExtra(EXTRA_COMPLETE_FILENAME)
        val collagesPathName = intent.getStringExtra(EXTRA_COLLAGES_PATHNAME)
        val simpleFileName = intent.getStringExtra(EXTRA_SIMPLE_FILENAME)
        fileToPrintString = filename

        Timber.d("--- EXTRA_COMPLETE_FILENAME: $filename")
        Timber.d("--- EXTRA_COLLAGES_PATHNAME: $collagesPathName")
        Timber.d("--- EXTRA_SIMPLE_FILENAME: $simpleFileName")

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
