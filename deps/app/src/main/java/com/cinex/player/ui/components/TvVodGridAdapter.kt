package com.cinex.player.ui.components

import android.content.Context
import android.graphics.*
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cinex.player.R
import com.cinex.player.data.model.Channel

internal class TvVodGridAdapter : RecyclerView.Adapter<TvVodGridAdapter.VH>() {

    var onItemClick: (Channel) -> Unit = {}
    var onNavigateLeft: () -> Unit = {}
    var onNavigateUp: () -> Unit = {}
    var showProgress: Boolean = false
    var areSortChipsVisible: Boolean = true
    private val columnCount = 4

    var items: List<Channel> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    inner class VH(
        val card: TvPosterCard,
        val image: ImageView,
        val name: TextView,
        val progressView: View,
        val episodeBadge: TextView
    ) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        fun dp(v: Int) = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

        val card = TvPosterCard(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFocusable = true
            isClickable = true
        }

        val image = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val gradientOverlay = object : View(ctx) {
            private val gPaint = Paint()
            override fun onDraw(c: Canvas) {
                gPaint.shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(Color.TRANSPARENT, 0xCC000000.toInt()),
                    null, Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gPaint)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)
            ).also { it.gravity = Gravity.BOTTOM }
        }

        val nameView = TextView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.BOTTOM }
            setPadding(dp(6), dp(2), dp(6), dp(6))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val progressView = View(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4)
            ).also { it.gravity = Gravity.BOTTOM }
            setBackgroundColor(0xFFE11D2E.toInt())
            visibility = View.GONE
            pivotX = 0f
        }

        val episodeBadge = TextView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = Gravity.TOP or Gravity.START
                it.setMargins(dp(5), dp(5), 0, 0)
            }
            setPadding(dp(4), dp(2), dp(4), dp(2))
            setTextColor(0xFFF59E0B.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xCC000000.toInt())
                cornerRadius = dp(3).toFloat()
            }
            visibility = View.GONE
        }

        card.addView(image)
        card.addView(gradientOverlay)
        card.addView(nameView)
        card.addView(progressView)
        card.addView(episodeBadge)

        return VH(card, image, nameView, progressView, episodeBadge)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val channel = items[position]

        // Na TV, prioriza sempre posterUrl (TMDB HTTPS) sobre logoUrl (Xtream HTTP):
        // - TMDB usa HTTPS e é acessível em qualquer rede/emulador
        // - Xtream usa HTTP em porta não-padrão, pode ser bloqueado ou inacessível no emulador
        // - Se posterUrl for null (não enriquecido pelo TMDB), usa logoUrl como fallback
        val imageUrls = listOfNotNull(channel.posterUrl, channel.logoUrl)
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

        // Evita cancelar o request em andamento se a URL não mudou (notifyDataSetChanged rebinda
        // todos os itens visíveis — sem este guard, o Coil cancela e reinicia todos os requests,
        // causando um loop onde muitas capas nunca terminam de carregar na TV).
        val primaryUrl = imageUrls.firstOrNull()
        if (holder.image.tag != primaryUrl) {
            holder.image.tag = primaryUrl
            if (imageUrls.isNotEmpty()) {
                holder.image.loadWithFallback(imageUrls)
            } else {
                holder.image.setImageResource(R.drawable.logo_cinex)
            }
        }

        val displayName = if (channel.category == "SERIES" && channel.seasonNumber != null && !channel.seriesName.isNullOrBlank()) {
            val s = channel.seasonNumber.toString().padStart(2, '0')
            val e = (channel.episodeNumber ?: 0).toString().padStart(2, '0')
            val cleanSeries = channel.seriesName.replace(Regex("(?i)\\s*\\(\\d{4}\\)\\s*"), "").trim()
            "S${s}E${e} - $cleanSeries"
        } else {
            channel.name
        }
        holder.name.text = displayName

        if (showProgress && channel.seasonNumber != null && channel.episodeNumber != null) {
            val s = channel.seasonNumber.toString().padStart(2, '0')
            val e = channel.episodeNumber.toString().padStart(2, '0')
            holder.episodeBadge.text = "S$s E$e"
            holder.episodeBadge.visibility = View.VISIBLE
        } else {
            holder.episodeBadge.visibility = View.GONE
        }

        if (showProgress && channel.resumePosition > 0 && channel.totalDuration > 0) {
            val progress = (channel.resumePosition.toFloat() / channel.totalDuration).coerceIn(0f, 1f)
            holder.progressView.visibility = View.VISIBLE
            holder.progressView.scaleX = progress
        } else {
            holder.progressView.visibility = View.GONE
        }

        holder.card.setOnClickListener { onItemClick(channel) }

        holder.card.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val pos = holder.absoluteAdapterPosition
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (pos % columnCount == 0) { onNavigateLeft(); true } else false
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (pos < columnCount && areSortChipsVisible) { onNavigateUp(); true } else false
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        onItemClick(channel); true
                    }
                    else -> false
                }
            } else false
        }

        holder.card.setOnFocusChangeListener { _, hasFocus ->
            holder.card.setFocused(hasFocus)
        }
    }

    override fun getItemCount() = items.size
}

internal class TvPosterCard(context: Context) : FrameLayout(context) {
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * context.resources.displayMetrics.density
    }
    private var isFocusHighlighted = false

    fun setFocused(focused: Boolean) {
        isFocusHighlighted = focused
        scaleX = if (focused) 1.05f else 1.0f
        scaleY = if (focused) 1.05f else 1.0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 1.5f).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    override fun dispatchDraw(canvas: Canvas) {
        val radius = 12f * resources.displayMetrics.density
        val path = Path().apply {
            addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        super.dispatchDraw(canvas)
        canvas.restore()

        if (isFocusHighlighted) {
            val strokeW = focusPaint.strokeWidth / 2f
            val rect = RectF(strokeW, strokeW, width - strokeW, height - strokeW)
            focusPaint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFFE11D2E.toInt(), 0xFFD8A63A.toInt()),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, focusPaint)
        }
    }
}

// Carrega com fallback de URLs: se a primeira falhar (ex: URL HTTP do servidor Xtream),
// tenta a próxima (ex: posterUrl HTTPS do TMDB). Usa tag para detectar reciclagem e evitar
// que o callback de erro dispare em um ViewHolder já reutilizado para outro canal.
private fun ImageView.loadWithFallback(urls: List<String>, index: Int = 0) {
    val url = urls.getOrNull(index) ?: run {
        setImageResource(R.drawable.logo_cinex)
        return
    }
    val view = this  // Captura explícita para uso nos lambdas aninhados
    load(url) {
        size(300, 450)
        memoryCacheKey(url)
        diskCacheKey(url)
        placeholder(R.drawable.logo_cinex)
        error(R.drawable.logo_cinex)
        crossfade(false)
        if (index < urls.lastIndex) {
            listener(onError = { _, _ ->
                // Só tenta o fallback se a view não foi reutilizada para outro canal
                if (view.tag == urls.first()) {
                    view.post { view.loadWithFallback(urls, index + 1) }
                }
            })
        }
    }
}

internal class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacing: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect, view: View,
        parent: RecyclerView, state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val column = position % spanCount
        outRect.left = spacing * column / spanCount
        outRect.right = spacing * (spanCount - 1 - column) / spanCount
        if (position >= spanCount) outRect.top = spacing
        outRect.bottom = 0
    }
}
