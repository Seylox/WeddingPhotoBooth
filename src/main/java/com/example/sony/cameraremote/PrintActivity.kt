package com.example.sony.cameraremote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AlertDialog
import android.view.View
import android.view.WindowManager
import android.widget.Button
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

    lateinit var completeFileName: String
    lateinit var collagesPathName: String
    lateinit var simpleFileName: String

    var photoAlreadyMoved = false

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
    }

    private fun copyPhotoToPrintDirectory() {
        if (!photoAlreadyMoved) {
            photoAlreadyMoved = true

            // saving number of pictures printed to sharedprefs
            val sharedPreferences = getSharedPreferences(getString(R.string.preference_file_key),
                    Context.MODE_PRIVATE)
            var numberPicturesPrinted = sharedPreferences.getInt(
                    SampleCameraActivity.numberPicturesPrintedPrefsString, 0)
            if (Constants.AMOUNT_MAX_PICTURES_IN_PRINTER - numberPicturesPrinted > 0) {
                numberPicturesPrinted++
            } else {
                numberPicturesPrinted = 1
            }
            val editor = sharedPreferences.edit()
            editor.putInt(SampleCameraActivity.numberPicturesPrintedPrefsString, numberPicturesPrinted)
            editor.apply()

            val printPathName = applicationContext
                    .getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    .path + "/print/"
            val printPath = File(printPathName)
            printPath.mkdirs()

            val printFileName = printPathName + simpleFileName

            Timber.d("--- copying $completeFileName to $printFileName")
            FileUtils.copyFileOrDirectory(completeFileName, printPathName)
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
        var numberPicturesPrinted = sharedPreferences.getInt(
                SampleCameraActivity.numberPicturesPrintedPrefsString, 0)

        if (Constants.AMOUNT_MAX_PICTURES_IN_PRINTER - numberPicturesPrinted > 0) {
            // TODO: "foto wird übertragen und dann gedruckt"
            // TODO: progress? (not possible, but indicator)

            copyPhotoToPrintDirectory()
            showPicturesPrintedDialog()
        } else {
            // TODO "not enough pictures in printer"
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setTitle(getString(R.string.kein_fotopapier_im_drucker))
            alertDialogBuilder.setMessage("Bitte Fotopapier nachlegen, sonst kann ich nicht" +
                    " weitermachen :( Bitte geh zu Thomas Geymayer oder Bernd Kampl, die wissen wie :)")
            alertDialogBuilder.setPositiveButton("Papier ist nachgelegt", { dialog, which ->
                copyPhotoToPrintDirectory()
                showPicturesPrintedDialog()
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
            // TODO "OK, fotopapier ist nachgelegt!"
        }
    }

    private fun showPicturesPrintedDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle(getString(R.string.foto_druckt___))
        alertDialogBuilder.setMessage("Das Foto wird übertragen und dann sofort gedruckt. Der" +
                " Drucker lässt das Foto am Ende des Druckvorgangs fallen, bitte nicht zu früh" +
                " daran ziehen. Druck dauert ca. 1 Minute. Du kannst in der Zwischenzeit schon" +
                " neue Fotos machen :)")
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
