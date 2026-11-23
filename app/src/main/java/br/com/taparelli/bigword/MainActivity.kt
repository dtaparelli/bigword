package br.com.taparelli.bigword

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

private val primary = 0xff26626d.toInt()
private val primaryDark = 0xff134e5c.toInt()
private val accent = 0xfff66f6c.toInt()
private val selected = 0xff00bcd4.toInt()
private val panelSurface = 0xff205b68.toInt()
private val panelOutline = 0xff53838d.toInt()
private val inputColor = 0xff212121.toInt()
private const val MATCH = -1
private const val WRAP = -2

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var settings: Settings
    private lateinit var input: EditText
    private lateinit var bottom: FrameLayout
    private lateinit var modePanel: ScrollView
    private lateinit var infoButton: ImageButton
    private var overlay: View? = null
    private var panel: Panel? = null

    private enum class Panel { COLORS, FONT, INFO }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        settings = Settings(this)
        root = FrameLayout(this).apply {
            setBackgroundColor(primaryDark)
            isFocusableInTouchMode = true
        }
        val sections = column()
        val top = scroll(messageSection()).apply {
            setBackgroundColor(primary)
            isFillViewport = true
        }
        sections.addView(top, LinearLayout.LayoutParams(MATCH, 0, 2f))
        sections.addView(View(this).apply { setBackgroundColor(Color.WHITE) },
            LinearLayout.LayoutParams(MATCH, 1).apply {
                marginStart = dp(38)
                marginEnd = dp(38)
            })
        bottom = FrameLayout(this)
        sections.addView(bottom, LinearLayout.LayoutParams(MATCH, 0, 3f))
        root.addView(sections, FrameLayout.LayoutParams(MATCH, MATCH))

        modePanel = scroll(modeSection()).apply { isFillViewport = true }
        bottom.addView(modePanel, FrameLayout.LayoutParams(MATCH, MATCH))
        infoButton = icon(R.drawable.ic_info, getString(R.string.information), 28, tint = accent) {
            if (panel == Panel.INFO) closePanel(slide = true) else showPanel(Panel.INFO)
        }
        bottom.addView(infoButton, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.END)
            .apply { setMargins(0, 0, dp(12), dp(12)) })
        configureWindow(root, immersive = false)
        root.requestFocus()

        savedInstanceState?.getString("panel")?.let { showPanel(Panel.valueOf(it)) }
        if (savedInstanceState?.getBoolean("favorites") == true) showFavorites()
        registerBack()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("panel", panel?.name)
        outState.putBoolean("favorites", favoritesDialog?.isShowing == true)
    }

    private fun messageSection(): View = column().apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
        addView(label(getString(R.string.message), 14, bold = true))

        val field = FrameLayout(this@MainActivity)
        input = EditText(this@MainActivity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(500))
            hint = getString(R.string.enter_text)
            setText(settings.message)
            setTextColor(inputColor)
            setHintTextColor(0xff757575.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            background = shape(Color.WHITE, 8)
            setPadding(dp(52), 0, dp(52), 0)
        }
        field.addView(input, FrameLayout.LayoutParams(MATCH, dp(50)))
        val clear = icon(R.drawable.ic_close, getString(R.string.clear_message), 24, 0xffbbbbbb.toInt()) {
            input.text.clear()
        }.apply {
            visibility = if (input.text.isEmpty()) View.INVISIBLE else View.VISIBLE
        }
        field.addView(clear, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER_VERTICAL or Gravity.END))
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                settings.message = s.toString()
                clear.visibility = if (s.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        val fieldRow = row().apply {
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(field, LinearLayout.LayoutParams(0, dp(50), 8f))
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        }
        addView(fieldRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })

        val star = icon(R.drawable.ic_star, getString(R.string.favorites), 27, accent) { showFavorites() }
        val starRow = FrameLayout(this@MainActivity).apply {
            addView(star, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        }
        addView(starRow, LinearLayout.LayoutParams(MATCH, dp(48)))

        val big = action(getString(R.string.big), 38, accent, radius = 30) {
            hideKeyboard()
            closePanel()
            startActivity(Intent(this@MainActivity, BigActivity::class.java))
        }.apply { elevation = dp(16).toFloat() }
        addView(big, LinearLayout.LayoutParams(dp(100), dp(100)).apply { topMargin = dp(20) })
    }

    private fun modeSection(): View = column().apply {
        gravity = Gravity.CENTER_VERTICAL
        // Leave room for the floating info button at the end of the scroll area.
        setPadding(dp(20), dp(56), dp(20), dp(56))
        addView(label(getString(R.string.mode), 13, bold = true).apply {
            gravity = Gravity.START
            accessibilityHeading()
        })

        val blinkSwitch = Switch(this@MainActivity).apply {
            text = getString(R.string.blink)
            textSize = 16f
            setTextColor(Color.WHITE)
            minimumHeight = dp(48)
            switchPadding = dp(16)
            isChecked = settings.blink
        }
        val speed = speedControl()
        fun updateVisibleControls() {
            blinkSwitch.visibility = if (settings.mode == Mode.WORDS) View.INVISIBLE else View.VISIBLE
            speed.visibility = if (settings.mode == Mode.FIT && !settings.blink) View.INVISIBLE else View.VISIBLE
        }
        val modes = modeSelector {
            settings.mode = it
            updateVisibleControls()
        }
        addView(modes, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        addView(blinkSwitch, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(10)
            marginStart = dp(4)
            marginEnd = dp(4)
        })
        addView(speed, LinearLayout.LayoutParams(MATCH, WRAP))
        blinkSwitch.setOnCheckedChangeListener { _, checked ->
            settings.blink = checked
            updateVisibleControls()
        }
        updateVisibleControls()

        val palette = palettes[settings.paletteIndex]
        val font = fonts[settings.fontIndex]
        val appearance = column().apply {
            background = panelShape()
            clipToOutline = true
            val swatch = label("Aa", 16, palette.foreground, bold = true).apply {
                background = shape(palette.background, 6)
            }
            addView(pickerRow(getString(R.string.color), palette.label, swatch, Panel.COLORS))
            addView(View(this@MainActivity).apply { setBackgroundColor(panelOutline) },
                LinearLayout.LayoutParams(MATCH, dp(1)).apply {
                    marginStart = dp(14)
                    marginEnd = dp(14)
                })
            val sample = label("Aa", 20).apply { typeface = font.load(this@MainActivity) }
            addView(pickerRow(getString(R.string.font), font.label, sample, Panel.FONT))
        }
        addView(appearance, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
    }

    private fun modeSelector(onSelection: (Mode) -> Unit): View =
        RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            background = panelShape()
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val segments = Mode.entries.map {
                RadioButton(this@MainActivity).apply {
                    id = View.generateViewId()
                    buttonDrawable = null
                    gravity = Gravity.CENTER
                    textSize = 14f
                    minHeight = dp(48)
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    addView(this, RadioGroup.LayoutParams(0, MATCH, 1f))
                }
            }
            fun render() {
                segments.forEachIndexed { index, button ->
                    val mode = Mode.entries[index]
                    val title = getString(mode.labelRes)
                    button.text = if (button.isChecked) getString(R.string.selected_mode, title) else title
                    button.setTextColor(if (button.isChecked) inputColor else Color.WHITE)
                    button.background = ripple(if (button.isChecked) selected else Color.TRANSPARENT, 8)
                }
            }
            check(segments[settings.mode.ordinal].id)
            render()
            setOnCheckedChangeListener { _, id ->
                val index = segments.indexOfFirst { it.id == id }
                if (index >= 0) {
                    onSelection(Mode.entries[index])
                    render()
                }
            }
        }

    private fun speedControl(): View = row().apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), 0, 0, 0)
        val title = label(getString(R.string.speed_percent, settings.speed), 14, bold = true).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minimumWidth = dp(96)
        }
        addView(title, LinearLayout.LayoutParams(WRAP, WRAP))
        addView(SeekBar(this@MainActivity).apply {
            max = 100
            progress = settings.speed
            contentDescription = getString(R.string.speed)
            minimumHeight = dp(48)
            progressTintList = ColorStateList.valueOf(selected)
            thumbTintList = ColorStateList.valueOf(selected)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    title.text = getString(R.string.speed_percent, value)
                    if (fromUser) settings.speed = value
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
    }

    private fun pickerRow(title: String, value: String, sample: View, destination: Panel): View =
        row().apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54)
            setPaddingRelative(dp(14), dp(6), dp(12), dp(6))
            background = ripple(Color.TRANSPARENT, 0)
            contentDescription = getString(R.string.picker_description, title, value)
            isFocusable = true
            setOnClickListener { showPanel(destination) }
            useButtonAccessibilityRole()

            addView(label(title, 16).apply {
                gravity = Gravity.START
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            sample.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(sample, LinearLayout.LayoutParams(dp(38), dp(30)))
            addView(label(value, 13).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
            addView(label("›", 24).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(10) })
        }

    private fun panelShape() = shape(panelSurface, 12).apply { setStroke(dp(1), panelOutline) }

    private fun View.accessibilityHeading() {
        if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }

    private fun showPanel(next: Panel) {
        closePanel()
        panel = next
        modePanel.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val content = when (next) {
            Panel.COLORS -> picker(palettes.size, 3) { index ->
                val palette = palettes[index]
                action(palette.label, 16, palette.background, palette.foreground) {
                    settings.paletteIndex = index
                    refreshModes()
                }
            }
            Panel.FONT -> picker(fonts.size, 2) { index ->
                val font = fonts[index]
                action(font.label, 16, Color.GRAY) {
                    settings.fontIndex = index
                    refreshModes()
                }.apply { typeface = font.load(this@MainActivity) }
            }
            Panel.INFO -> infoSection()
        }
        overlay = content
        bottom.addView(content, bottom.indexOfChild(infoButton), FrameLayout.LayoutParams(MATCH, MATCH))
        if (next == Panel.INFO) {
            infoButton.setColorFilter(primary)
            content.translationX = bottom.width.toFloat()
            content.animate().translationX(0f).setDuration(240).start()
        }
    }

    private fun closePanel(slide: Boolean = false) {
        overlay?.let { content ->
            content.animate().cancel()
            if (slide && panel == Panel.INFO) {
                content.animate().translationX(bottom.width.toFloat()).setDuration(220)
                    .withEndAction { bottom.removeView(content) }.start()
            } else {
                bottom.removeView(content)
            }
        }
        overlay = null
        panel = null
        modePanel.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        infoButton.setColorFilter(accent)
    }

    private fun refreshModes() {
        closePanel()
        modePanel.removeAllViews()
        modePanel.addView(modeSection())
    }

    private fun picker(count: Int, columns: Int, buttonFor: (Int) -> TextView): View =
        scroll(column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(20), dp(8), dp(12))
            for (start in 0 until count step columns) {
                val buttons = row()
                for (index in start until minOf(start + columns, count)) {
                    val button = buttonFor(index)
                    button.setPadding(dp(3), 0, dp(3), 0)
                    buttons.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                        setMargins(dp(4), dp(5), dp(4), dp(5))
                    })
                }
                addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            addView(action(getString(R.string.close), 16, primaryDark, radius = 16) { closePanel() },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(12) })
        }).apply {
            isFillViewport = true
            isClickable = true
            setBackgroundColor(0xff102b31.toInt())
        }

    private fun infoSection(): View = scroll(column().apply {
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(8), dp(24), dp(8))
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        addView(label(getString(R.string.app_version, version), 16, bold = true),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = dp(20) })
        addView(label(getString(R.string.created_by), 16))
        addView(label(getString(R.string.author), 22, bold = true))
        addView(infoIcon(R.drawable.ic_email, getString(R.string.email_author)) {
            openLink("mailto:daniel@taparelli.com.br")
        })
        addView(label(getString(R.string.rate_this_app), 16), spacedLabel())
        addView(infoIcon(R.drawable.ic_star, getString(R.string.rate_app_description)) {
            val appId = "br.com.taparelli.bigword"
            if (!openLink("market://details?id=$appId", reportFailure = false)) {
                openLink("https://play.google.com/store/apps/details?id=$appId")
            }
        })
        addView(label(getString(R.string.zap_me_a_beer), 16), spacedLabel())
        addView(infoIcon(R.drawable.ic_bolt, getString(R.string.copy_lightning_address)) {
            val address = "nearjeans92@walletofsatoshi.com"
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(getString(R.string.lightning_address), address))
            android.app.AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.lightning_copied))
                .setMessage(address)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        })
        addView(action(getString(R.string.privacy_policy), 16, Color.TRANSPARENT, inputColor) {
            openLink("https://dtaparelli.github.io/bigword/privacy.html")
        }.apply {
            minHeight = dp(48)
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        })
    }).apply {
        isFillViewport = true
        isClickable = true
        setBackgroundColor(accent)
    }

    private fun spacedLabel() = LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(25) }

    private fun infoIcon(drawable: Int, description: String, onClick: () -> Unit): ImageButton =
        icon(drawable, description, 40, onClick = onClick).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        }

    private fun openLink(uri: String, reportFailure: Boolean = true): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        true
    } catch (_: ActivityNotFoundException) {
        if (reportFailure) Toast.makeText(this, getString(R.string.no_link_handler), Toast.LENGTH_SHORT).show()
        false
    }

    private var favoritesDialog: Dialog? = null

    private fun showFavorites() {
        hideKeyboard()
        val dialog = Dialog(this)
        val card = column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = shape(Color.WHITE, 12)
        }
        dialog.setContentView(card)
        fun render() {
            val favorites = settings.favorites
            val draft = settings.message.trim()
            card.removeAllViews()
            card.addView(label(getString(R.string.favorites), 18, primaryDark, bold = true))
            if (draft.isNotEmpty() && draft !in favorites) {
                val saveRow = row()
                saveRow.addView(favoriteLabel(draft).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                saveRow.addView(action(getString(R.string.save), 14, accent, radius = 16) {
                    settings.favorites = listOf(draft) + settings.favorites
                    render()
                })
                card.addView(saveRow, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            val list = column()
            if (favorites.isEmpty()) {
                list.addView(label(getString(R.string.no_favorites), 14, primaryDark).apply {
                    setPadding(0, dp(12), 0, dp(12))
                }, LinearLayout.LayoutParams(MATCH, WRAP))
            }
            favorites.forEach { message ->
                val item = row()
                item.addView(favoriteLabel(message).apply {
                    background = ripple(Color.WHITE, 0)
                    setOnClickListener {
                        input.setText(message)
                        dialog.dismiss()
                    }
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                item.addView(icon(R.drawable.ic_delete, getString(R.string.delete_favorite, message), 20, tint = accent) {
                    settings.favorites = settings.favorites.filterNot { it == message }
                    render()
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
                list.addView(item, LinearLayout.LayoutParams(MATCH, WRAP))
                list.addView(View(this).apply { setBackgroundColor(0xffdddddd.toInt()) },
                    LinearLayout.LayoutParams(MATCH, 1))
            }
            val listScroll = scroll(list)
            list.measure(
                View.MeasureSpec.makeMeasureSpec((root.width * .85f).toInt() - dp(32), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val maxListHeight = (root.height * .7f).toInt() - dp(145)
            card.addView(listScroll, LinearLayout.LayoutParams(MATCH,
                list.measuredHeight.coerceAtMost(maxListHeight.coerceAtLeast(dp(48)))))
            card.addView(action(getString(R.string.close), 14, 0xffeeeeee.toInt(), primaryDark, 16) { dialog.dismiss() },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(12) })
        }
        // Restoration can request the dialog before the root has been measured.
        if (root.width == 0) {
            root.post { if (!isFinishing && !isDestroyed) showFavorites() }
            return
        }
        render()
        dialog.setOnDismissListener { favoritesDialog = null }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.6f)
            setLayout((root.width * .85f).toInt(), WRAP)
        }
        favoritesDialog = dialog
    }

    private fun favoriteLabel(value: String) = label(value, 14, primaryDark).apply {
        minimumHeight = dp(48)
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(0, dp(12), dp(12), dp(12))
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
        root.requestFocus()
    }

    @Suppress("DEPRECATION")
    private fun registerBack() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val callback = android.window.OnBackInvokedCallback { handleBack() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
            )
        }
    }

    @Deprecated("Back handling for Android 12 and earlier")
    override fun onBackPressed() = handleBack()

    private fun handleBack() {
        if (panel != null) closePanel() else finish()
    }

    private fun dp(value: Int) = root.dp(value)

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun row() = LinearLayout(this).apply { gravity = Gravity.CENTER }

    private fun scroll(content: View) = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    private fun label(value: String, size: Int, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            gravity = Gravity.CENTER
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun action(
        value: String,
        size: Int,
        backgroundColor: Int,
        color: Int = Color.WHITE,
        radius: Int = 5,
        onClick: () -> Unit,
    ) = label(value, size, color).apply {
        minimumHeight = dp(48)
        background = ripple(backgroundColor, radius)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        isFocusable = true
        setOnClickListener { onClick() }
        useButtonAccessibilityRole()
    }

    private fun View.useButtonAccessibilityRole() {
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: android.view.accessibility.AccessibilityNodeInfo,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        }
    }

    private fun icon(
        drawable: Int,
        description: String,
        size: Int,
        color: Int = Color.TRANSPARENT,
        tint: Int = Color.WHITE,
        onClick: () -> Unit,
    ) = ImageButton(this).apply {
        background = ripple(color, 30)
        setPadding(0, 0, 0, 0)
        scaleType = android.widget.ImageView.ScaleType.CENTER
        val iconSize = dp(size)
        val image = requireNotNull(getDrawable(drawable)).mutate()
        setImageDrawable(object : InsetDrawable(image, 0) {
            override fun getIntrinsicWidth(): Int = iconSize
            override fun getIntrinsicHeight(): Int = iconSize
        })
        setColorFilter(tint)
        contentDescription = description
        setOnClickListener { onClick() }
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun ripple(color: Int, radius: Int) =
        RippleDrawable(ColorStateList.valueOf(0x33ffffff), shape(color, radius), shape(Color.WHITE, radius))
}
