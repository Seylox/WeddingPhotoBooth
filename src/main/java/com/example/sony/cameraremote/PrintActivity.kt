package com.example.sony.cameraremote

import android.app.Activity
import android.app.PendingIntent.getActivity
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import com.squareup.picasso.Picasso
import timber.log.Timber
import java.io.File
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.support.v4.print.PrintHelper



class PrintActivity : Activity() {

    val mImageView by lazy {
        findViewById<ImageView>(R.id.imageView)
    }

    val cameraWifiButton by lazy {
        findViewById<Button>(R.id.camera_wifi_button)
    }

    val printerWifiButton by lazy {
        findViewById<Button>(R.id.printer_wifi_button)
    }

    val printButton by lazy {
        findViewById<Button>(R.id.print_button)
    }

    lateinit var fileToPrintString: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_print)

        val filename = intent.getStringExtra("IMAGEFILENAME")
        fileToPrintString = filename

        Timber.d("--- file from intent: $filename")

        val file = File(filename)

        Timber.d("--- file actually: $file")
        Picasso.with(this).isLoggingEnabled = true
        Picasso.with(this).load(file).into(mImageView)
    }

    private fun doPhotoPrint() {
        val photoPrinter = PrintHelper(this)
        photoPrinter.scaleMode = PrintHelper.SCALE_MODE_FILL
        val bitmapToPrint = BitmapFactory.decodeFile(fileToPrintString)
        photoPrinter.printBitmap("PictureToPrint", bitmapToPrint)

//        val bitmap = BitmapFactory.decodeResource(resources,
//                R.drawable.droids)
//        photoPrinter.printBitmap("droids.jpg - test print", bitmap)
    }

    public fun onClickCameraWifiButton(view: View) {

    }

    public fun onClickPrinterWifiButton(view: View) {

    }

    public fun onClickPrintButton(view: View) {
        doPhotoPrint()
    }
}
