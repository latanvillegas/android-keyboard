    package org.futo.inputmethod.latin.uix.theme.serialization

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.exceptions.TomlDecodingException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.futo.inputmethod.latin.uix.ExtraColors
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.theme.AdvancedThemeOptions
import org.futo.inputmethod.latin.uix.theme.ThemeActionIcon
import org.futo.inputmethod.latin.uix.theme.GenericPalette
import org.futo.inputmethod.latin.uix.theme.KeyBackground
import org.futo.inputmethod.latin.uix.theme.KeyIcon
import org.futo.inputmethod.latin.uix.theme.ThemeDecodingContext
import org.futo.inputmethod.latin.uix.theme.TonalPalette
import org.futo.inputmethod.latin.uix.theme.decodeKeyedBitmaps
import org.futo.inputmethod.latin.uix.theme.decodeOptionalFont
import org.futo.inputmethod.latin.uix.theme.decodeOptionalImage
import org.futo.inputmethod.latin.uix.utils.createNinePatchDrawable
import kotlin.math.abs
import kotlin.math.roundToInt

// Why does TOML throw an exception for floats specified without decimal point?!
object LenientFloatSerializer : KSerializer<Float> {
    override val descriptor = PrimitiveSerialDescriptor("LenientFloat", PrimitiveKind.FLOAT)

    override fun serialize(encoder: Encoder, value: Float) {
        encoder.encodeFloat(value)
    }

    override fun deserialize(decoder: Decoder): Float {
        return try {
            decoder.decodeFloat()
        }catch(e: TomlDecodingException) {
            decoder.decodeInt().toFloat()
        }
    }
}


typealias LFloat = @Serializable(with = LenientFloatSerializer::class) Float

@Suppress("PropertyName")
@Serializable
private data class SerializedTomlFile(
    val name: String = "Untitled theme",
    val author: String = "Anonymous",
    val id: String? = null,
    val version: Int = 1,
    val description: String = "No description specified.",
    val options: Options = Options(),
    val colors: Colors,
    val matchrules: Matchrules = Matchrules(),
    val asset: Asset = Asset(),
) {
    @Serializable
    data class Options(
        val auto_borders: Boolean = true,
        val center_hints: Boolean = false,
        val roundedness: LFloat = 1.0f,

        val scale_text: LFloat = 1.0f,
        val scale_hints: LFloat = 1.0f,
        val mic_icon: String? = null,
        val mic_icon_scale: LFloat = 1.0f,
        // Optional V2 theme extension. Existing TOML ZIPs remain compatible.
        val action_icons: List<ActionIconSpec> = emptyList(),
        val action_icon_color: String? = null,
        val action_button_background_color: String? = null,
        val action_button_foreground_color: String? = null,
        val expanded_actions_bar_color: String? = null,
        val suggestion_font_scale: LFloat = 1.0f,
        val suggestion_text_color: String? = null,
        val suggestion_bar_color: String? = null,
        val suggestion_divider_color: String? = null,
        // Optional 9-slice PNGs; assets may reuse an existing [[asset.border]].
        val suggestion_item_border: String? = null,
        val suggestion_item_pressed_border: String? = null,
        val suggestion_item_background_color: String? = null,
        val suggestion_item_pressed_background_color: String? = null,
        val suggestion_item_radius: LFloat = 12.0f,
        val suggestion_item_inset_horizontal: LFloat = 3.0f,
        val suggestion_item_inset_vertical: LFloat = 2.0f,
        val suggestion_item_hide_dividers: Boolean = true,
        val weight_text: LFloat = 400.0f,
        val weight_hints: LFloat = 500.0f,
        val font: Font? = null,
        val background: Background? = null,
    ) {
        @Serializable
        data class Font(val font: String? = null)

        @Serializable
        data class ActionIconSpec(
            val name: String,
            val asset: String,
            val scale: LFloat = 1.0f,
            val tint: Boolean = false
        )

        @Serializable
        data class Background(
            val image: String,
            val opacity: LFloat = 0.5f,
            val action_bar_opacity: LFloat = 0.5f,

            val cropping: List<LFloat> = listOf(0.0f, 0.0f, 1.0f, 1.0f)
        )
    }

    @Serializable
    data class Colors(
        val primary: String,
        val on_primary: String,
        val primary_container: String,
        val on_primary_container: String,
        val inverse_primary: String,

        val secondary: String,
        val on_secondary: String,
        val secondary_container: String,
        val on_secondary_container: String,

        val tertiary: String,
        val on_tertiary: String,
        val tertiary_container: String,
        val on_tertiary_container: String,

        val background: String,
        val on_background: String,

        val surface: String,
        val on_surface: String,
        val surface_variant: String,
        val on_surface_variant: String,
        val surface_tint: String,
        val inverse_surface: String,
        val inverse_on_surface: String,

        val error: String,
        val on_error: String,
        val error_container: String,
        val on_error_container: String,

        val outline: String,
        val outline_variant: String,
        val scrim: String,

        val surface_bright: String,
        val surface_dim: String,
        val surface_container: String,
        val surface_container_high: String,
        val surface_container_highest: String,
        val surface_container_low: String,
        val surface_container_lowest: String,

        val keyboard_surface: String,
        val keyboard_surface_dim: String,
        val keyboard_container: String,
        val keyboard_container_variant: String,
        val on_keyboard_container: String,
        val keyboard_press: String,
        val keyboard_container_pressed: String,
        val on_keyboard_container_pressed: String,
    )

    @Serializable
    data class Matchrules(
        val border: List<Matchrule> = emptyList(),
        val icon: List<Matchrule> = emptyList()
    ) {
        @Serializable
        data class Matchrule(
            val selector: String,
            val asset: String,
        )
    }

    class ValidationException(val reason: String) : Exception(reason)

    @Serializable
    data class Asset(
        val icon: List<Icon> = emptyList(),
        val border: List<Border> = emptyList()
    ) {
        @Serializable
        data class Icon(
            val name: String,
            val target_density: LFloat = 640.0f
        )

        @Serializable
        data class Border(
            val name: String,
            val target_density: LFloat = 640.0f,
            val background_tint: String = "#ffffff",
            val foreground_tint: String = "#777777",
            val padding: List<LFloat> = listOf(0.0f, 0.0f, 0.0f, 0.0f),
            val slicing: List<LFloat> = listOf(0.0f, 0.0f, 1.0f, 1.0f),
            val gap: List<LFloat> = listOf(1.0f, 1.0f, 1.0f, 1.0f),
            val outline_tint: String? = null,
            val outline_width: LFloat = 3.0f,
        )
    }

    fun lookupBorder(name: String) =
        asset.border.firstOrNull { it.name == name } ?: Asset.Border(name)

    fun lookupIcon(name: String) =
        asset.icon.firstOrNull { it.name == name } ?: Asset.Icon(name)

    private fun String.fromNamedColor() = when(this) {
            "primary" ->    colors.primary
            "on_primary" ->     colors.on_primary
            "primary_container" ->  colors.primary_container
            "on_primary_container" ->   colors.on_primary_container
            "inverse_primary" ->    colors.inverse_primary
            "secondary" ->  colors.secondary
            "on_secondary" ->   colors.on_secondary
            "secondary_container" ->    colors.secondary_container
            "on_secondary_container" ->     colors.on_secondary_container
            "tertiary" ->   colors.tertiary
            "on_tertiary" ->    colors.on_tertiary
            "tertiary_container" ->     colors.tertiary_container
            "on_tertiary_container" ->  colors.on_tertiary_container
            "background" ->     colors.background
            "on_background" ->  colors.on_background
            "surface" ->    colors.surface
            "on_surface" ->     colors.on_surface
            "surface_variant" ->    colors.surface_variant
            "on_surface_variant" ->     colors.on_surface_variant
            "surface_tint" ->   colors.surface_tint
            "inverse_surface" ->    colors.inverse_surface
            "inverse_on_surface" ->     colors.inverse_on_surface
            "error" ->  colors.error
            "on_error" ->   colors.on_error
            "error_container" ->    colors.error_container
            "on_error_container" ->     colors.on_error_container
            "outline" ->    colors.outline
            "outline_variant" ->    colors.outline_variant
            "scrim" ->  colors.scrim
            "surface_bright" ->     colors.surface_bright
            "surface_dim" ->    colors.surface_dim
            "surface_container" ->  colors.surface_container
            "surface_container_high" ->     colors.surface_container_high
            "surface_container_highest" ->  colors.surface_container_highest
            "surface_container_low" ->  colors.surface_container_low
            "surface_container_lowest" ->   colors.surface_container_lowest
            "keyboard_surface" ->   colors.keyboard_surface
            "keyboard_surface_dim" ->   colors.keyboard_surface_dim
            "keyboard_container" ->     colors.keyboard_container
            "keyboard_container_variant" ->     colors.keyboard_container_variant
            "on_keyboard_container" ->  colors.on_keyboard_container
            "keyboard_press" ->     colors.keyboard_press
            "keyboard_container_pressed" ->     colors.keyboard_container_pressed
            "on_keyboard_container_pressed" ->  colors.on_keyboard_container_pressed
            else -> throw IllegalArgumentException("Unknown named color $this")
    }.also { if(!it.startsWith("#")) throw IllegalArgumentException("Core colors must not be aliased") }

    private fun String.toColor(palette: Lazy<TonalPalette>): Color = when {
        this.startsWith("system_") -> palette.value.resolve(substring("system_".length)) ?: throw IllegalArgumentException("Unknown color $this")
        this.startsWith("#") -> Color(parseHexColorStringToARGBLong(this, AlphaOrder.RGBA))
        else -> this.fromNamedColor().toColor(palette)
    }


    fun validate(): Boolean {
        val genericPalette = lazyOf(GenericPalette)
        val colorPairs = listOf(
            "primary:onPrimary" to colors.primary to colors.on_primary,
            "secondary:onSecondary" to colors.secondary to colors.on_secondary,
            "tertiary:onTertiary" to colors.tertiary to colors.on_tertiary,
            "background:onBackground" to colors.background to colors.on_background,
            "surface:onSurface" to colors.surface to colors.on_surface,
            "primaryContainer:onPrimaryContainer" to colors.primary_container to colors.on_primary_container,
            "secondaryContainer:onSecondaryContainer" to colors.secondary_container to colors.on_secondary_container,
            "tertiaryContainer:onTertiaryContainer" to colors.tertiary_container to colors.on_tertiary_container
        )

        for(v in colorPairs) {
            val bgname = v.first.first
            val bg = v.first.second
            val fg = v.second

            val bgc = bg.toColor(genericPalette).compositeOver(colors.background.toColor(genericPalette)).compositeOver(Color.White)
            val fgc = fg.toColor(genericPalette).compositeOver(bgc)

            val contrast = abs(bgc.luminance() - fgc.luminance())
            if(contrast < 0.08) {
                throw ValidationException("Contrast between these colors is far too insufficient and UI elements will not be readable: $bgname (bg=$bg fg=$fg)")
            }
        }

        return true
    }

    fun toKeyboardScheme(ctx: ThemeDecodingContext): KeyboardColorScheme {
        val palette = ctx.palette
        val backgroundImage = options.background?.let { decodeOptionalImage(ctx, it.image) }
        // Shared 9-slice decoder: suggestion pills use exactly the same asset metadata
        // and renderer as keyboard keys, preserving sharp corners when stretched.
        fun decodeBorder(assetName: String, img: ImageBitmap): KeyBackground? {
            val bitmap = img.asAndroidBitmap()
            val meta = lookupBorder(assetName)
            val scale = ctx.context.resources.displayMetrics.densityDpi / meta.target_density
            val w = (bitmap.width * scale).roundToInt()
            val h = (bitmap.height * scale).roundToInt()

            val xRegions = mutableListOf<Pair<Int, Int>>()
            val yRegions = mutableListOf<Pair<Int, Int>>()
            val padding = Rect(0, 0, 0, 0)
            val gap = RectF(1.0f, 1.0f, 1.0f, 1.0f)

            if(meta.slicing.size == 4) {
                xRegions.add((meta.slicing[0] * w).roundToInt() to (meta.slicing[2] * w).roundToInt())
                yRegions.add((meta.slicing[1] * h).roundToInt() to (meta.slicing[3] * h).roundToInt())
            }

            if(meta.padding.size == 4) {
                padding.left = (meta.padding[0] * w).roundToInt()
                padding.top = (meta.padding[1] * h).roundToInt()
                padding.right = ((1.0f - meta.padding[2]) * w).roundToInt()
                padding.bottom = ((1.0f - meta.padding[3]) * h).roundToInt()
            }

            if(meta.gap.size == 4) {
                gap.left = meta.gap[0]
                gap.top = meta.gap[1]
                gap.right = meta.gap[2]
                gap.bottom = meta.gap[3]
            }

            return createNinePatchDrawable(
                bitmap, scale, ctx.context.resources,
                meta.foreground_tint.toColor(palette).toArgb(),
                meta.background_tint.toColor(palette).toArgb(),
                meta.outline_tint?.toColor(palette)?.toArgb(), meta.outline_width,
                xRegions, yRegions, padding, gap
            )
        }

        return KeyboardColorScheme(
            base = ColorScheme(
                primary                 = colors.primary.toColor(palette),
                onPrimary               = colors.on_primary.toColor(palette),
                primaryContainer        = colors.primary_container.toColor(palette),
                onPrimaryContainer      = colors.on_primary_container.toColor(palette),
                inversePrimary          = colors.inverse_primary.toColor(palette),
                secondary               = colors.secondary.toColor(palette),
                onSecondary             = colors.on_secondary.toColor(palette),
                secondaryContainer      = colors.secondary_container.toColor(palette),
                onSecondaryContainer    = colors.on_secondary_container.toColor(palette),
                tertiary                = colors.tertiary.toColor(palette),
                onTertiary              = colors.on_tertiary.toColor(palette),
                tertiaryContainer       = colors.tertiary_container.toColor(palette),
                onTertiaryContainer     = colors.on_tertiary_container.toColor(palette),
                background              = colors.background.toColor(palette),
                onBackground            = colors.on_background.toColor(palette),
                surface                 = colors.surface.toColor(palette),
                onSurface               = colors.on_surface.toColor(palette),
                surfaceVariant          = colors.surface_variant.toColor(palette),
                onSurfaceVariant        = colors.on_surface_variant.toColor(palette),
                surfaceTint             = colors.surface_tint.toColor(palette),
                inverseSurface          = colors.inverse_surface.toColor(palette),
                inverseOnSurface        = colors.inverse_on_surface.toColor(palette),
                error                   = colors.error.toColor(palette),
                onError                 = colors.on_error.toColor(palette),
                errorContainer          = colors.error_container.toColor(palette),
                onErrorContainer        = colors.on_error_container.toColor(palette),
                outline                 = colors.outline.toColor(palette),
                outlineVariant          = colors.outline_variant.toColor(palette),
                scrim                   = colors.scrim.toColor(palette),
                surfaceBright           = colors.surface_bright.toColor(palette),
                surfaceDim              = colors.surface_dim.toColor(palette),
                surfaceContainer        = colors.surface_container.toColor(palette),
                surfaceContainerHigh    = colors.surface_container_high.toColor(palette),
                surfaceContainerHighest = colors.surface_container_highest.toColor(palette),
                surfaceContainerLow     = colors.surface_container_low.toColor(palette),
                surfaceContainerLowest  = colors.surface_container_lowest.toColor(palette),
            ),
            extended = ExtraColors(
                keyboardSurface            = colors.keyboard_surface.toColor(palette),
                keyboardSurfaceDim         = colors.keyboard_surface_dim.toColor(palette),
                keyboardContainer          = colors.keyboard_container.toColor(palette),
                keyboardContainerVariant   = colors.keyboard_container_variant.toColor(palette),
                onKeyboardContainer        = colors.on_keyboard_container.toColor(palette),
                keyboardPress              = colors.keyboard_press.toColor(palette),
                primaryTransparent         = colors.primary.toColor(palette).copy(alpha = 0.5f),
                onSurfaceTransparent       = colors.on_surface.toColor(palette).copy(alpha = 0.5f),
                keyboardContainerPressed   = colors.keyboard_container_pressed.toColor(palette),
                onKeyboardContainerPressed = colors.on_keyboard_container_pressed.toColor(palette),

                hintColor          = null,
                navigationBarColor = null,

                keyboardBackgroundGradient = options.background?.let {
                    SolidColor(colors.keyboard_surface.toColor(palette).copy(alpha = 1.0f - it.opacity))
                },
                advancedThemeOptions = AdvancedThemeOptions(
                    textSizeMultiplier = options.scale_text,
                    hintSizeMultiplier = options.scale_hints,
                    textWeight = options.weight_text,
                    hintWeight = options.weight_hints,

                    centerHints = options.center_hints,

                    keyRoundness = options.roundedness,
                    keyBorders = options.auto_borders,
                    backgroundShader = null,

                    actionBarOpacity = options.background?.action_bar_opacity ?: 0.5f,
                    actionMicIcon = decodeOptionalImage(ctx, options.mic_icon),
                    actionMicIconScale = options.mic_icon_scale,
                    actionIcons = options.action_icons.take(64).mapNotNull { spec ->
                        if (!spec.name.matches(Regex("[a-z][a-z0-9_]{0,79}"))) null
                        else decodeOptionalImage(ctx, spec.asset)?.let { image ->
                            spec.name to ThemeActionIcon(
                                image = image,
                                scale = spec.scale.takeIf { it.isFinite() }
                                    ?.coerceIn(0.2f, 5.0f) ?: 1.0f,
                                tint = spec.tint
                            )
                        }
                    }.toMap(),
                    actionIconColor = options.action_icon_color?.toColor(palette),
                    actionButtonBackgroundColor = options.action_button_background_color?.toColor(palette),
                    actionButtonForegroundColor = options.action_button_foreground_color?.toColor(palette),
                    expandedActionsBarColor = options.expanded_actions_bar_color?.toColor(palette),
                    suggestionFontScale = options.suggestion_font_scale,
                    suggestionTextColor = options.suggestion_text_color?.toColor(palette),
                    suggestionBarColor = options.suggestion_bar_color?.toColor(palette),
                    suggestionDividerColor = options.suggestion_divider_color?.toColor(palette),
                    thumbnailImage = backgroundImage,
                    thumbnailScale = 1.0f,
                    backgroundImage = backgroundImage,
                    backgroundImageVisibleArea = run {
                        if(backgroundImage != null && options.background?.cropping?.size == 4) {

                            Rect(
                                (backgroundImage.width * options.background.cropping[0]).roundToInt(),
                                (backgroundImage.height * options.background.cropping[1]).roundToInt(),
                                (backgroundImage.width * options.background.cropping[2]).roundToInt(),
                                (backgroundImage.height * options.background.cropping[3]).roundToInt(),
                            )
                        } else {
                            null
                        }
                    },
                    suggestionItemBorder = options.suggestion_item_border?.let { name ->
                        decodeOptionalImage(ctx, name)?.let { decodeBorder(name, it) }
                    },
                    suggestionItemPressedBorder = options.suggestion_item_pressed_border?.let { name ->
                        decodeOptionalImage(ctx, name)?.let { decodeBorder(name, it) }
                    },
                    suggestionItemBackgroundColor = options.suggestion_item_background_color?.toColor(palette),
                    suggestionItemPressedBackgroundColor = options.suggestion_item_pressed_background_color?.toColor(palette),
                    suggestionItemRadius = options.suggestion_item_radius.takeIf { it.isFinite() }?.coerceIn(0.0f, 32.0f) ?: 12.0f,
                    suggestionItemInsetHorizontal = options.suggestion_item_inset_horizontal.takeIf { it.isFinite() }?.coerceIn(0.0f, 16.0f) ?: 3.0f,
                    suggestionItemInsetVertical = options.suggestion_item_inset_vertical.takeIf { it.isFinite() }?.coerceIn(0.0f, 16.0f) ?: 2.0f,
                    suggestionItemHideDividers = options.suggestion_item_hide_dividers,
                    keyBackgrounds = decodeKeyedBitmaps(ctx, matchrules.border, keyFn={it.selector}, valFn={it.asset}) { cfg, img ->
                        decodeBorder(cfg.asset, img)
                    },
                    keyIcons = decodeKeyedBitmaps(ctx, matchrules.icon, keyFn={it.selector}, valFn={it.asset}) { cfg, img ->
                        KeyIcon(img.asAndroidBitmap().toDrawable(ctx.context.resources))
                    },
                    font = decodeOptionalFont(ctx, options.font?.font),

                    themeName = name,
                    themeAuthor = author
                ),
            )
        )
    }
}

private val formatVersionRegex = """Format version: (\d\.?\d?)""".toRegex(setOf(RegexOption.IGNORE_CASE))
fun findFormatVersion(v: String): Int? =
    formatVersionRegex.find(v)
        ?.groupValues
        ?.getOrNull(1)
        ?.split(".")
        ?.firstOrNull()
        ?.toIntOrNull()

internal val Toml1 = Toml(inputConfig = TomlInputConfig(ignoreUnknownNames = false))
internal val Toml2 = Toml(inputConfig = TomlInputConfig(ignoreUnknownNames = true))

class TomlZipTheme(string: String) : SerializableTheme {
    val formatVersion = findFormatVersion(string)

    private var parseErrors = ""

    private val parsed = try {
        Toml1.decodeFromString<SerializedTomlFile>(string)
    } catch(e: Exception) {
        parseErrors += e.toString()
        Toml2.decodeFromString<SerializedTomlFile>(string)
    }
    override fun toKeyboardScheme(ctx: ThemeDecodingContext): KeyboardColorScheme {
        return parsed.toKeyboardScheme(ctx)
    }

    override fun validate(): Boolean {
        return parsed.validate()
    }
    override val id: String? get() = parsed.id
    override val thumbnailImage: String? get() = parsed.options.background?.image

    override val name: String get() = parsed.name
    override val author: String get() = parsed.author

    override val errors: String get() = parseErrors
}
