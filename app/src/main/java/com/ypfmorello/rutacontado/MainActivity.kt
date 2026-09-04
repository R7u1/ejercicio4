package com.ypfmorello.rutacontado

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var infiniaInput: EditText
    private lateinit var dieselInput: EditText
    private lateinit var preview: ImageView
    private lateinit var shareButton: Button
    private lateinit var saveButton: Button
    private var generatedBitmap: Bitmap? = null
    private val prefs by lazy { getSharedPreferences("precios", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(6, 26, 77)
        window.navigationBarColor = Color.rgb(6, 26, 77)
        setContentView(buildUi())
        loadLastPrices()
        preview.setImageBitmap(drawPoster(null, null))
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(5, 22, 67))
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "YPF RUTA CONTADO"
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Generador de precios con descuento"
            textSize = 15f
            setTextColor(Color.rgb(55, 195, 240))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(22))
        })

        infiniaInput = makePriceInput("Precio surtidor · Infinia Diesel")
        dieselInput = makePriceInput("Precio surtidor · Diesel 500")
        root.addView(infiniaInput)
        root.addView(space(10))
        root.addView(dieselInput)
        root.addView(space(16))

        root.addView(Button(this).apply {
            text = "CALCULAR Y GENERAR IMAGEN"
            textSize = 15f
            isAllCaps = false
            setOnClickListener { generateImage() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

        root.addView(TextView(this).apply {
            text = "Infinia Diesel: 8% · Diesel 500: 4% · App YPF Ruta: 2% para ambos.\nTodos se calculan por separado sobre el precio de surtidor."
            textSize = 12.5f
            setTextColor(Color.LTGRAY)
            setPadding(dp(2), dp(12), dp(2), dp(14))
        })

        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(space(16))

        shareButton = Button(this).apply {
            text = "COMPARTIR POR WHATSAPP"
            isEnabled = false
            setOnClickListener { shareGeneratedImage() }
        }
        root.addView(shareButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(space(10))

        saveButton = Button(this).apply {
            text = "GUARDAR IMAGEN"
            isEnabled = false
            setOnClickListener { saveToGallery() }
        }
        root.addView(saveButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        return scroll
    }

    private fun makePriceInput(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 18f
        setTextColor(Color.BLACK)
        setHintTextColor(Color.DKGRAY)
        setBackgroundColor(Color.WHITE)
        setPadding(dp(14), 0, dp(14), 0)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58))
    }

    private fun space(h: Int) = TextView(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    private fun loadLastPrices() {
        infiniaInput.setText(prefs.getString("infinia", ""))
        dieselInput.setText(prefs.getString("diesel", ""))
    }

    private fun generateImage() {
        val infinia = parsePrice(infiniaInput.text.toString())
        val diesel = parsePrice(dieselInput.text.toString())
        if (infinia == null || diesel == null || infinia <= BigDecimal.ZERO || diesel <= BigDecimal.ZERO) {
            Toast.makeText(this, "Ingresá los dos precios de surtidor.", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString("infinia", infiniaInput.text.toString().trim())
            .putString("diesel", dieselInput.text.toString().trim()).apply()

        val output = drawPoster(infinia, diesel)
        generatedBitmap?.recycle()
        generatedBitmap = output
        preview.setImageBitmap(output)
        cacheGeneratedImage(output)
        shareButton.isEnabled = true
        saveButton.isEnabled = true
        Toast.makeText(this, "Imagen lista.", Toast.LENGTH_SHORT).show()
    }

    private fun drawPoster(infinia: BigDecimal?, diesel: BigDecimal?): Bitmap {
        val w = 1254
        val h = 1254
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val navy = Color.rgb(3, 19, 62)
        val blue = Color.rgb(0, 69, 190)
        val cyan = Color.rgb(0, 188, 235)
        val red = Color.rgb(203, 22, 20)
        val green = Color.rgb(0, 126, 56)
        val yellow = Color.rgb(217, 164, 0)

        c.drawColor(navy)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(5, 37, 105)
        c.drawRect(0f, 690f, w.toFloat(), h.toFloat(), p)
        p.color = blue
        p.strokeWidth = 3f
        for (i in 0..6) c.drawLine(0f, 760f + i * 70f, w.toFloat(), 620f + i * 60f, p)

        drawFitText(c, "YPF RUTA CONTADO", 70f, 145f, 1110f, 118f, Color.WHITE, true)
        p.color = cyan
        p.strokeWidth = 2f
        c.drawLine(115f, 182f, 1138f, 182f, p)
        drawFitText(c, "BENEFICIOS EN COMBUSTIBLES", 118f, 272f, 1018f, 71f, cyan, true)

        drawDiscountCard(c, RectF(135f, 310f, 625f, 820f), red, "Tarjeta Roja Batán", "8%", "DE DESCUENTO", "en Infinia Diesel", infinia?.let { formatPrice(discounted(it, 8)) })
        drawDiscountCard(c, RectF(655f, 310f, 1135f, 820f), red, "Tarjeta Roja Batán", "4%", "DE DESCUENTO", "en Diesel 500", diesel?.let { formatPrice(discounted(it, 4)) })

        val appRect = RectF(255f, 840f, 1008f, 1197f)
        p.style = Paint.Style.FILL
        p.color = navy
        c.drawRoundRect(appRect, 34f, 34f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = cyan
        c.drawRoundRect(appRect, 34f, 34f, p)
        p.style = Paint.Style.FILL
        p.color = blue
        c.drawRect(260f, 845f, 1003f, 914f, p)
        drawFitText(c, "APP YPF RUTA", 350f, 904f, 560f, 61f, Color.WHITE, true)
        drawFitText(c, "2%", 466f, 1038f, 330f, 128f, Color.WHITE, true)
        drawFitText(c, "DE DESCUENTO", 485f, 1085f, 310f, 40f, Color.WHITE, true)

        drawFuelPriceBox(c, RectF(280f, 1091f, 610f, 1182f), green, "Infinia Diesel", infinia?.let { formatPrice(discounted(it, 2)) })
        drawFuelPriceBox(c, RectF(640f, 1091f, 970f, 1182f), yellow, "Diesel 500", diesel?.let { formatPrice(discounted(it, 2)) })

        p.color = navy
        c.drawRect(0f, 1198f, w.toFloat(), h.toFloat(), p)
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        drawFitText(c, "$date  •  YPF MORELLO", 350f, 1240f, 560f, 38f, Color.WHITE, true)
        return bmp
    }

    private fun drawDiscountCard(c: Canvas, r: RectF, accent: Int, header: String, pct: String, line: String, fuel: String, price: String?) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(5, 24, 76)
        p.style = Paint.Style.FILL
        c.drawRoundRect(r, 30f, 30f, p)
        p.color = accent
        c.drawRoundRect(RectF(r.left, r.top, r.right, r.top + 88f), 30f, 30f, p)
        c.drawRect(r.left, r.top + 45f, r.right, r.top + 90f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        c.drawRoundRect(r, 30f, 30f, p)
        p.style = Paint.Style.FILL
        drawFitText(c, header, r.left + 54f, r.top + 67f, r.width() - 108f, 49f, Color.WHITE, true)
        drawFitText(c, pct, r.left + 92f, r.top + 265f, r.width() - 184f, 165f, Color.WHITE, true)
        drawFitText(c, line, r.left + 67f, r.top + 320f, r.width() - 134f, 43f, Color.WHITE, true)
        p.color = accent
        c.drawRect(r.left + 66f, r.top + 340f, r.right - 66f, r.top + 344f, p)
        drawFitText(c, fuel, r.left + 65f, r.top + 407f, r.width() - 130f, 46f, Color.WHITE, true)
        val box = RectF(r.left + 30f, r.bottom - 90f, r.right - 30f, r.bottom - 18f)
        p.color = Color.WHITE
        c.drawRoundRect(box, 14f, 14f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = accent
        c.drawRoundRect(box, 14f, 14f, p)
        p.style = Paint.Style.FILL
        if (price != null) drawFitText(c, price, box.left + 22f, box.centerY() + 17f, box.width() - 44f, 44f, Color.rgb(5, 24, 76), true)
    }

    private fun drawFuelPriceBox(c: Canvas, r: RectF, accent: Int, fuel: String, price: String?) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = accent
        p.style = Paint.Style.FILL
        c.drawRoundRect(r, 20f, 20f, p)
        drawFitText(c, fuel, r.left + 20f, r.top + 34f, r.width() - 40f, 32f, Color.WHITE, true)
        val inner = RectF(r.left + 7f, r.top + 43f, r.right - 7f, r.bottom - 7f)
        p.color = Color.WHITE
        c.drawRoundRect(inner, 12f, 12f, p)
        if (price != null) drawFitText(c, price, inner.left + 18f, inner.centerY() + 14f, inner.width() - 36f, 33f, Color.rgb(5, 24, 76), true)
    }

    private fun drawFitText(c: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float, startSize: Float, color: Int, bold: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = startSize
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        while (p.measureText(text) > maxWidth && p.textSize > 18f) p.textSize -= 1f
        c.drawText(text, x, baseline, p)
    }

    private fun discounted(price: BigDecimal, percentage: Int): BigDecimal =
        price.multiply(BigDecimal(100 - percentage)).divide(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)

    private fun parsePrice(raw: String): BigDecimal? {
        var s = raw.trim().replace("$", "").replace(" ", "")
        if (s.isBlank()) return null
        val comma = s.lastIndexOf(',')
        val dot = s.lastIndexOf('.')
        s = when {
            comma >= 0 && dot >= 0 -> if (comma > dot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            comma >= 0 -> s.replace(',', '.')
            dot >= 0 -> {
                val digitsAfter = s.length - dot - 1
                if (digitsAfter == 3 && s.count { it == '.' } == 1) s.replace(".", "") else s
            }
            else -> s
        }
        return s.toBigDecimalOrNull()
    }

    private fun formatPrice(value: BigDecimal): String {
        val symbols = DecimalFormatSymbols(Locale("es", "AR")).apply {
            decimalSeparator = ','
            groupingSeparator = '.'
        }
        val formatter = DecimalFormat("#,##0.00", symbols).apply { roundingMode = RoundingMode.HALF_UP }
        return "$ ${formatter.format(value)}"
    }

    private fun cacheGeneratedImage(bitmap: Bitmap) {
        FileOutputStream(File(cacheDir, "ypf_ruta_contado.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shareGeneratedImage() {
        if (generatedBitmap == null) return
        val uri = Uri.parse("content://com.ypfmorello.rutacontado.generated/ypf_ruta_contado.png")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, "YPF Ruta Contado", uri)
        }
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try { startActivity(Intent(intent).setPackage(pkg)); return } catch (_: ActivityNotFoundException) { }
        }
        startActivity(Intent.createChooser(intent, "Compartir imagen"))
    }

    private fun saveToGallery() {
        val bitmap = generatedBitmap ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "YPF_Ruta_Contado_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YPF Morello")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); contentResolver.update(uri, values, null, null)
                Toast.makeText(this, "Guardada en Fotos · YPF Morello", Toast.LENGTH_LONG).show(); return
            }
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/png"; putExtra(Intent.EXTRA_TITLE, "YPF_Ruta_Contado.png")
        }
        startActivityForResult(intent, 7001)
    }

    @Deprecated("Compatibilidad con Android 8/9")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val bitmap = generatedBitmap ?: return
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Toast.makeText(this, "Imagen guardada.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
