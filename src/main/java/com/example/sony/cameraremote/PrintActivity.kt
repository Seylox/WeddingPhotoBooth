package com.example.sony.cameraremote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.support.v7.app.AlertDialog
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import com.example.sony.cameraremote.utils.Constants
import com.example.sony.cameraremote.utils.FileUtils
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

    val emojiImageview by lazy {
        val view = findViewById<ImageView>(R.id.emoji_imageview)
        view.visibility = if (getUseRandomSymbolFromPrefs()) View.VISIBLE else View.GONE
        view
    }

    lateinit var completeFileName: String
    lateinit var collagesPathName: String
    lateinit var simpleFileName: String

    var photoAlreadyMoved = false

    val showSecretIconRunnable = Runnable {
        emojiImageview.visibility = View.INVISIBLE
    }

    val mainThreadHandler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_print)

        // disable screen timeout while app is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // collect information from intent extras
        completeFileName = intent.getStringExtra(EXTRA_COMPLETE_FILENAME)
        collagesPathName = intent.getStringExtra(EXTRA_COLLAGES_PATHNAME)
        simpleFileName = intent.getStringExtra(EXTRA_SIMPLE_FILENAME)

//        Timber.d("--- EXTRA_COMPLETE_FILENAME: $completeFileName")
//        Timber.d("--- EXTRA_COLLAGES_PATHNAME: $collagesPathName")
//        Timber.d("--- EXTRA_SIMPLE_FILENAME: $simpleFileName")

        val file = File(completeFileName)

        Picasso.with(this).isLoggingEnabled = true
        Picasso.with(this).load(file).into(mImageView)

        // Copy file to /sdcard/Pictures/WeddingPhotoBooth/DSC12345-12348.JPG
//        val externalPicturesPath = Environment.getExternalStoragePublicDirectory(
//                Environment.DIRECTORY_PICTURES)
//        val externalPicturesPhotoBoothPath = File(externalPicturesPath, "WeddingPhotoBooth")
//        externalPicturesPhotoBoothPath.mkdirs()
//        FileUtils.copyFileOrDirectory(completeFileName, externalPicturesPhotoBoothPath.path)

        emojiImageview.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mainThreadHandler.postDelayed(showSecretIconRunnable, 2000)
                }
                MotionEvent.ACTION_UP -> {
                    view.visibility = View.VISIBLE
                    mainThreadHandler.removeCallbacks(showSecretIconRunnable)
                }
            }
            return@setOnTouchListener true
        }
    }

    private fun copyPhotoToPrintDirectory() {
        if (!photoAlreadyMoved) {
            photoAlreadyMoved = true

            // saving number of pictures printed to sharedprefs
            val sharedPreferences = getSharedPreferences(getString(R.string.preference_file_key),
                    Context.MODE_PRIVATE)
            var numberPicturesPrinted = sharedPreferences.getInt(
                    Constants.numberPicturesPrintedPrefsString, 0)
            if (Constants.AMOUNT_MAX_PICTURES_IN_PRINTER - numberPicturesPrinted > 0) {
                numberPicturesPrinted++
            } else {
                numberPicturesPrinted = 1
            }

            var numberPrintsLeft = sharedPreferences.getInt(
                    Constants.numberPrintsLeftInCartridgePrefsString, 0)
            numberPrintsLeft--

            val editor = sharedPreferences.edit()
            editor.putInt(Constants.numberPicturesPrintedPrefsString, numberPicturesPrinted)
            editor.putInt(Constants.numberPrintsLeftInCartridgePrefsString, numberPrintsLeft)
            editor.apply()

            // copying file to /sdcard/Pictures/print/DSCvwxyz.JPG (e.g. DSC04521.JPG)
            val externalPicturesPath = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES)
            val printPath = File(externalPicturesPath, "print")
            printPath.mkdirs()

            Timber.d("--- copying $completeFileName to $printPath")
            FileUtils.copyFileOrDirectory(completeFileName, printPath.path)
        }
    }

    public fun onClickDeletePicturesButton(view: View) {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle(getString(R.string.foto_wirkilch_loeschen_))
        alertDialogBuilder.setMessage(getString(R.string.moechtest_du_das_foto_wirklich_loeschen__es_wird_dann_nicht_gedruckt_))
        alertDialogBuilder.setPositiveButton(getString(R.string.loeschen), { dialog, which ->
            finish()
        })
        alertDialogBuilder.setNegativeButton(getString(R.string.abbrechen), { dialog, which ->
            // nothing
        })
        val alertDialog = alertDialogBuilder.create()
        // Not leave immersive mode when AlertDialog is shown:
        // https://stackoverflow.com/questions/22794049/how-do-i-maintain-the-immersive-mode-in-dialogs
        //Set the dialog to not focusable (makes navigation ignore us adding the window)
        alertDialog.window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        // Show the dialog
        alertDialog.show()
        // Set the dialog to immersive
        alertDialog.window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
        // Clear the not focusable flag from the window
        alertDialog.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    public fun onClickPrintPicturesButton(view: View) {
        // TODO safe guard and progress

        val sharedPreferences = getSharedPreferences(getString(R.string.preference_file_key),
                Context.MODE_PRIVATE)
        val numberPicturesPrinted = sharedPreferences.getInt(
                Constants.numberPicturesPrintedPrefsString, 0)
        val numberPrintsLeft = sharedPreferences.getInt(
                Constants.numberPrintsLeftInCartridgePrefsString, 0)

        val numberPicturesLeft = Constants.AMOUNT_MAX_PICTURES_IN_PRINTER - numberPicturesPrinted

        if (numberPicturesLeft > 0 && numberPrintsLeft > 0) {
            // TODO: "foto wird übertragen und dann gedruckt"
            // TODO: progress? (not possible, but indicator)

            copyPhotoToPrintDirectory()
            showPicturesPrintedDialog()
        } else {
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setTitle(getString(R.string.kein_fotopapier_im_drucker))
            alertDialogBuilder.setMessage(getString(R.string.empty_dialog_message) +
                    "\n\n\nIch habe ${numberPicturesLeft} Fotos in der Lade" +
                    "\nIch habe ${numberPrintsLeft} Drucke in der Cartridge")
            alertDialogBuilder.setPositiveButton(getString(R.string.einstellungen), { dialog, which ->
                showSetPhotosPrintsDialog()
            })
            alertDialogBuilder.setNegativeButton(getString(R.string.abbrechen), { dialog, which ->
                // nothing
            })
            val alertDialog = alertDialogBuilder.create()
            // Not leave immersive mode when AlertDialog is shown:
            // https://stackoverflow.com/questions/22794049/how-do-i-maintain-the-immersive-mode-in-dialogs
            //Set the dialog to not focusable (makes navigation ignore us adding the window)
            alertDialog.window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            // Show the dialog
            alertDialog.show()
            // Set the dialog to immersive
            alertDialog.window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
            // Clear the not focusable flag from the window
            alertDialog.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    private fun showPicturesPrintedDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle(getString(R.string.foto_druckt___))
        alertDialogBuilder.setMessage(getString(R.string.druck_dialog_message))
        alertDialogBuilder.setPositiveButton(getString(R.string.ok), { dialog, which ->
            finish()
        })
        val alertDialog = alertDialogBuilder.create()
        // Not leave immersive mode when AlertDialog is shown:
        // https://stackoverflow.com/questions/22794049/how-do-i-maintain-the-immersive-mode-in-dialogs
        //Set the dialog to not focusable (makes navigation ignore us adding the window)
        alertDialog.window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        // Show the dialog
        alertDialog.show()
        // Set the dialog to immersive
        alertDialog.window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
        // Clear the not focusable flag from the window
        alertDialog.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    private fun showSetPhotosPrintsDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val paperAndPrintsDialog = inflater.inflate(R.layout.dialog_paper_cartridge,
                null)

        val pagesLeftInPrinterEdittext = paperAndPrintsDialog
                .findViewById<EditText>(R.id.pages_left_in_printer_edittext)
        val printsLeftInCartridgeEdittext = paperAndPrintsDialog
                .findViewById<EditText>(R.id.prints_left_in_cartridge_edittext)

        val sharedPreferences = getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE)
        val numberPicturesPrinted = sharedPreferences.getInt(
                Constants.numberPicturesPrintedPrefsString, 0)
        val numberPrintsLeft = sharedPreferences.getInt(
                Constants.numberPrintsLeftInCartridgePrefsString, 0)
        val numberPagesLeft = Constants.AMOUNT_MAX_PICTURES_IN_PRINTER - numberPicturesPrinted

        pagesLeftInPrinterEdittext.setText(numberPagesLeft.toString())
        printsLeftInCartridgeEdittext.setText(numberPrintsLeft.toString())


        alertDialogBuilder.setView(paperAndPrintsDialog)
                .setPositiveButton(getString(R.string.ok)) { dialog, which ->

                    val pagesLeftNumber = try {
                        Integer.parseInt(
                                pagesLeftInPrinterEdittext.text.toString())
                    } catch (e: Exception) {
                        0
                    }

                    val printsLeftNumber = try {
                        Integer.parseInt(
                                printsLeftInCartridgeEdittext.text.toString())
                    } catch (e: Exception) {
                        0
                    }

                    val picturesPrintedNumber = Constants.AMOUNT_MAX_PICTURES_IN_PRINTER - pagesLeftNumber

                    val editor = sharedPreferences.edit()
                    editor.putInt(Constants.numberPicturesPrintedPrefsString, picturesPrintedNumber)
                    editor.putInt(Constants.numberPrintsLeftInCartridgePrefsString, printsLeftNumber)
                    editor.apply()
                }
                .setNegativeButton("Cancel") { dialog, which ->
                    // nothing
                }
        alertDialogBuilder.setTitle("Anzahl Fotopapier / Drucke setzen")
        val alertDialog = alertDialogBuilder.create()
        alertDialog.window!!.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        alertDialog.show()
        alertDialog.window!!.decorView.systemUiVisibility = window.decorView.systemUiVisibility
        alertDialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    private fun getUseRandomSymbolFromPrefs(): Boolean {
        val sharedPreferences = getSharedPreferences(getString(R.string.preference_file_key),
                Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(Constants.useRandomSymbolPrefsString, false)
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
