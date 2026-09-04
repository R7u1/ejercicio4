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
import android.graphics.Path
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
        preview.setImageBitmap(renderPromotionalImage(null, null, null, null))
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

        val generate = Button(this).apply {
            text = "CALCULAR Y GENERAR IMAGEN"
            textSize = 15f
            isAllCaps = false
            setOnClickListener { generateImage() }
        }
        root.addView(generate, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

        root.addView(TextView(this).apply {
            text = "Descuentos: Infinia 8% · Diesel 500 4% · App YPF Ruta 2% para ambos.\nLos descuentos se calculan por separado sobre el precio de surtidor."
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

    private fun space(h: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

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

        prefs.edit()
            .putString("infinia", infiniaInput.text.toString().trim())
            .putString("diesel", dieselInput.text.toString().trim())
            .apply()

        val infinia8 = discounted(infinia, 8)
        val diesel4 = discounted(diesel, 4)
        val infinia2 = discounted(infinia, 2)
        val diesel2 = discounted(diesel, 2)

        val output = renderPromotionalImage(infinia8, diesel4, infinia2, diesel2)
        generatedBitmap?.recycle()
        generatedBitmap = output
        preview.setImageBitmap(output)
        cacheGeneratedImage(output)
        shareButton.isEnabled = true
        saveButton.isEnabled = true
        Toast.makeText(this, "Imagen lista.", Toast.LENGTH_SHORT).show()
    }

    private fun renderPromotionalImage(
        infinia8: BigDecimal?,
        diesel4: BigDecimal?,
        infinia2: BigDecimal?,
        diesel2: BigDecimal?
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(1254, 1254, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val navy = Color.rgb(2, 14, 55)
        val navy2 = Color.rgb(5, 27, 86)
        val cyan = Color.rgb(0, 170, 238)
        val red = Color.rgb(190, 12, 12)
        val redStroke = Color.rgb(255, 55, 36)
        val green = Color.rgb(3, 135, 35)
        val greenStroke = Color.rgb(55, 220, 56)
        val gold = Color.rgb(205, 145, 0)
        val goldStroke = Color.rgb(255, 196, 0)
        val deepText = Color.rgb(7, 25, 67)

        canvas.drawColor(navy)

        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(10, 55, 138) }
        val leftPoly = Path().apply {
            moveTo(0f, 352f); lineTo(225f, 577f); lineTo(0f, 789f); close()
        }
        canvas.drawPath(leftPoly, shapePaint)
        shapePaint.color = Color.rgb(6, 43, 120)
        val leftPoly2 = Path().apply {
            moveTo(0f, 531f); lineTo(153f, 680f); lineTo(0f, 827f); close()
        }
        canvas.drawPath(leftPoly2, shapePaint)

        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(8, 90, 200)
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        val road = Path().apply {
            moveTo(1170f, 1235f)
            cubicTo(1088f, 1068f, 1177f, 917f, 1254f, 807f)
        }
        canvas.drawPath(road, roadPaint)
        roadPaint.strokeWidth = 4f
        roadPaint.color = Color.rgb(36, 148, 255)
        canvas.drawPath(road, roadPaint)

        drawCenteredText(canvas, "YPF RUTA CONTADO", 627f, 93f, 112f, Color.WHITE, true, 1160f)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyan; strokeWidth = 2f }
        canvas.drawLine(54f, 174f, 1200f, 174f, linePaint)
        drawCenteredText(canvas, "BENEFICIOS EN COMBUSTIBLES", 627f, 227f, 70f, cyan, true, 1160f)

        drawCard(canvas, RectF(64f, 280f, 615f, 770f), redStroke, navy2, 32f)
        drawTopBand(canvas, RectF(64f, 280f, 615f, 360f), red, 32f)
        drawCenteredText(canvas, "Tarjeta Roja Batán", 339.5f, 319f, 48f, Color.WHITE, true, 520f, italic = true)
        drawCenteredText(canvas, "8%", 339.5f, 412f, 96f, Color.WHITE, true, 300f)
        drawCenteredText(canvas, "DE DESCUENTO", 339.5f, 468f, 40f, Color.WHITE, true, 430f)
        canvas.drawRect(197f, 489f, 500f, 493f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = redStroke })
        drawPill(canvas, RectF(170f, 503f, 523f, 561f), green, greenStroke, "Infinia Diesel", 39f)
        drawPriceBox(canvas, RectF(87f, 571f, 592f, 748f), greenStroke, infinia8?.let { formatPrice(it) }, deepText, 88f)

        drawCard(canvas, RectF(635f, 280f, 1185f, 770f), redStroke, navy2, 32f)
        drawTopBand(canvas, RectF(635f, 280f, 1185f, 360f), red, 32f)
        drawCenteredText(canvas, "Tarjeta Roja Batán", 910f, 319f, 48f, Color.WHITE, true, 520f, italic = true)
        drawCenteredText(canvas, "4%", 910f, 412f, 96f, Color.WHITE, true, 300f)
        drawCenteredText(canvas, "DE DESCUENTO", 910f, 468f, 40f, Color.WHITE, true, 430f)
        canvas.drawRect(754f, 489f, 1063f, 493f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = redStroke })
        drawPill(canvas, RectF(730f, 503f, 1078f, 561f), gold, goldStroke, "Diesel 500", 39f)
        drawPriceBox(canvas, RectF(657f, 571f, 1162f, 748f), goldStroke, diesel4?.let { formatPrice(it) }, deepText, 88f)

        drawCard(canvas, RectF(83f, 783f, 1091f, 1179f), cyan, navy2, 30f)
        val appBand = RectF(83f, 783f, 1091f, 852f)
        drawTopBand(canvas, appBand, Color.rgb(10, 67, 184), 30f)
        drawCenteredText(canvas, "APP YPF RUTA", 587f, 819f, 59f, Color.WHITE, true, 830f)
        drawCenteredText(canvas, "2%", 587f, 892f, 73f, Color.WHITE, true, 250f)
        drawCenteredText(canvas, "DE DESCUENTO", 587f, 935f, 29f, Color.WHITE, true, 320f)
        drawPill(canvas, RectF(156f, 947f, 523f, 1004f), green, greenStroke, "Infinia Diesel", 38f)
        drawPill(canvas, RectF(695f, 947f, 1034f, 1004f), gold, goldStroke, "Diesel 500", 38f)
        drawPriceBox(canvas, RectF(104f, 1010f, 593f, 1166f), greenStroke, infinia2?.let { formatPrice(it) }, deepText, 70f)
        drawPriceBox(canvas, RectF(633f, 1010f, 1071f, 1166f), goldStroke, diesel2?.let { formatPrice(it) }, deepText, 70f)

        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        drawCenteredText(canvas, "$date  •  YPF MORELLO", 627f, 1216f, 42f, Color.WHITE, true, 760f)

        return bitmap
    }

    private fun drawCard(canvas: Canvas, rect: RectF, strokeColor: Int, fillColor: Int, radius: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = strokeColor; style = Paint.Style.STROKE; strokeWidth = 4f }
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, stroke)
    }

    private fun drawTopBand(canvas: Canvas, rect: RectF, color: Int, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(rect, radius, radius, paint)
        canvas.drawRect(rect.left, rect.centerY(), rect.right, rect.bottom, paint)
    }

    private fun drawPill(canvas: Canvas, rect: RectF, fillColor: Int, strokeColor: Int, text: String, textSize: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = strokeColor; style = Paint.Style.STROKE; strokeWidth = 3f }
        canvas.drawRoundRect(rect, 16f, 16f, fill)
        canvas.drawRoundRect(rect, 16f, 16f, stroke)
        drawCenteredText(canvas, text, rect.centerX(), rect.centerY(), textSize, Color.WHITE, true, rect.width() - 20f)
    }

    private fun drawPriceBox(canvas: Canvas, rect: RectF, strokeColor: Int, text: String?, textColor: Int, preferredSize: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 250, 250) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = strokeColor; style = Paint.Style.STROKE; strokeWidth = 4f }
        canvas.drawRoundRect(rect, 24f, 24f, fill)
        canvas.drawRoundRect(rect, 24f, 24f, stroke)
        if (!text.isNullOrBlank()) {
            drawCenteredText(canvas, text, rect.centerX(), rect.centerY(), preferredSize, textColor, true, rect.width() - 34f)
        }
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        cx: Float,
        cy: Float,
        preferredSize: Float,
        color: Int,
        bold: Boolean,
        maxWidth: Float,
        italic: Boolean = false
    ) {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, style)
            textSize = preferredSize
        }
        while (paint.measureText(text) > maxWidth && paint.textSize > 20f) paint.textSize -= 1f
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, cx, baseline, paint)
    }

    private fun discounted(price: BigDecimal, percentage: Int): BigDecimal {
        val factor = BigDecimal(100 - percentage).divide(BigDecimal(100))
        return price.multiply(factor).setScale(2, RoundingMode.HALF_UP)
    }

    private fun parsePrice(raw: String): BigDecimal? {
        var s = raw.trim().replace("$", "").replace(" ", "")
        if (s.isBlank()) return null
        val comma = s.lastIndexOf(',')
        val dot = s.lastIndexOf('.')
        s = when {
            comma >= 0 && dot >= 0 -> if (comma > dot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            comma >= 0 -> s.replace('.', '\u0000').replace(',', '.').replace("\u0000", "")
            else -> s
        }
        return s.toBigDecimalOrNull()
    }

    private fun formatPrice(value: BigDecimal): String {
        val symbols = DecimalFormatSymbols(Locale("es", "AR")).apply {
            decimalSeparator = ','
            groupingSeparator = '.'
        }
        val formatter = DecimalFormat("#,##0.00", symbols).apply {
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = true
        }
        return "$ ${formatter.format(value)}"
    }

    private fun cacheGeneratedImage(bitmap: Bitmap) {
        FileOutputStream(File(cacheDir, "ypf_ruta_contado.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
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
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            try {
                startActivity(Intent(intent).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) { }
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
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                Toast.makeText(this, "Guardada en Fotos · YPF Morello", Toast.LENGTH_LONG).show()
                return
            }
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/png"
            putExtra(Intent.EXTRA_TITLE, "YPF_Ruta_Contado.png")
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
