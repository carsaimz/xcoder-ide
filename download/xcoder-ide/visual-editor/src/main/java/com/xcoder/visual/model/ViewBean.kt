/**
 * Core widget data model for the visual editor.
 * Based on Sketchware-IA's ViewBean.java (487 lines).
 *
 * Represents a single widget (View or ViewGroup) in a layout. Each ViewBean
 * carries its own type, identity, transform state, and sub-beans for layout,
 * text, and image properties.
 *
 * This is the central data class used throughout the visual editor, XML parser,
 * XML generator, and Kotlin code generator.
 */
package com.xcoder.visual.model

import android.os.Parcel
import android.os.Parcelable

/**
 * ViewBean — the universal widget model.
 *
 * A single instance describes one View/ViewGroup. Layout containers
 * hold a list of child ViewBeans through their parent-child relationships
 * (see [parent], [parentType], [index]).
 */
data class ViewBean(
    // ── Identity ──────────────────────────────────────────────────────
    /** View type constant from [TYPE_*]. Corresponds to an Android widget class. */
    var type: Int = TYPE_TEXTVIEW,
    /** XML resource id, e.g. "button1" (without the @+id/ prefix). */
    var id: String = "",
    /** Human-readable display name (auto-generated or user-set). */
    var name: String = "",

    // ── Hierarchy ─────────────────────────────────────────────────────
    /** Id of the parent widget. Empty string or null means this is the root. */
    var parent: String = "",
    /** Type constant of the parent widget. */
    var parentType: Int = 0,
    /** Z-order index among siblings. */
    var index: Int = 0,

    // ── Transform ─────────────────────────────────────────────────────
    /** Whether the view is enabled. */
    var enabled: Boolean = true,
    /** Whether the view is clickable. */
    var clickable: Boolean = false,
    /** View alpha 0.0 – 1.0. */
    var alpha: Float = 1f,
    /** Translation X in pixels. */
    var translationX: Float = 0f,
    /** Translation Y in pixels. */
    var translationY: Float = 0f,
    /** Scale X. */
    var scaleX: Float = 1f,
    /** Scale Y. */
    var scaleY: Float = 1f,

    // ── Sub-beans ─────────────────────────────────────────────────────
    /** Layout/dimension/spacing properties. */
    var layout: LayoutBean = LayoutBean(),
    /** Text display properties (only meaningful for text-bearing widgets). */
    var text: TextBean = TextBean(),
    /** Image display properties (only meaningful for ImageView). */
    var image: ImageBean = ImageBean()
) : Parcelable {

    companion object {
        // ── View type constants ─────────────────────────────────────────
        // Layouts
        const val TYPE_LINEAR_LAYOUT = 0
        const val TYPE_RELATIVE_LAYOUT = 1
        const val TYPE_FRAME_LAYOUT = 2
        const val TYPE_CONSTRAINT_LAYOUT = 3
        const val TYPE_SCROLL_VIEW = 4
        const val TYPE_HORIZONTAL_SCROLL_VIEW = 5
        const val TYPE_RECYCLER_VIEW = 6
        const val TYPE_COORDINATOR_LAYOUT = 7
        const val TYPE_CARD_VIEW = 8
        const val TYPE_MATERIAL_CARD_VIEW = 9

        // Widgets
        const val TYPE_BUTTON = 100
        const val TYPE_TEXTVIEW = 101
        const val TYPE_EDITTEXT = 102
        const val TYPE_IMAGEVIEW = 103
        const val TYPE_CHECKBOX = 104
        const val TYPE_RADIOBUTTON = 105
        const val TYPE_SWITCH = 106
        const val TYPE_PROGRESS_BAR = 107
        const val TYPE_SEEK_BAR = 108
        const val TYPE_SPINNER = 109
        const val TYPE_WEBVIEW = 110
        const val TYPE_VIDEO_VIEW = 111
        const val TYPE_RATING_BAR = 112
        const val TYPE_TOGGLE_BUTTON = 113
        const val TYPE_IMAGE_BUTTON = 114
        const val TYPE_TEXT_CLOCK = 115
        const val TYPE_CHRONOMETER = 116
        const val TYPE_VIEW = 117
        const val TYPE_SPACE = 118
        const val TYPE_GUIDELINE = 119

        // ── Type ↔ XML tag name mapping ────────────────────────────────
        private val TYPE_TO_CLASS_NAME: Map<Int, String> = mapOf(
            TYPE_LINEAR_LAYOUT to "LinearLayout",
            TYPE_RELATIVE_LAYOUT to "RelativeLayout",
            TYPE_FRAME_LAYOUT to "FrameLayout",
            TYPE_CONSTRAINT_LAYOUT to "androidx.constraintlayout.widget.ConstraintLayout",
            TYPE_SCROLL_VIEW to "ScrollView",
            TYPE_HORIZONTAL_SCROLL_VIEW to "HorizontalScrollView",
            TYPE_RECYCLER_VIEW to "androidx.recyclerview.widget.RecyclerView",
            TYPE_COORDINATOR_LAYOUT to "androidx.coordinatorlayout.widget.CoordinatorLayout",
            TYPE_CARD_VIEW to "androidx.cardview.widget.CardView",
            TYPE_MATERIAL_CARD_VIEW to "com.google.android.material.card.MaterialCardView",
            TYPE_BUTTON to "Button",
            TYPE_TEXTVIEW to "TextView",
            TYPE_EDITTEXT to "EditText",
            TYPE_IMAGEVIEW to "ImageView",
            TYPE_CHECKBOX to "CheckBox",
            TYPE_RADIOBUTTON to "RadioButton",
            TYPE_SWITCH to "Switch",
            TYPE_PROGRESS_BAR to "ProgressBar",
            TYPE_SEEK_BAR to "SeekBar",
            TYPE_SPINNER to "Spinner",
            TYPE_WEBVIEW to "WebView",
            TYPE_VIDEO_VIEW to "VideoView",
            TYPE_RATING_BAR to "RatingBar",
            TYPE_TOGGLE_BUTTON to "ToggleButton",
            TYPE_IMAGE_BUTTON to "ImageButton",
            TYPE_TEXT_CLOCK to "TextClock",
            TYPE_CHRONOMETER to "Chronometer",
            TYPE_VIEW to "View",
            TYPE_SPACE to "Space",
            TYPE_GUIDELINE to "androidx.constraintlayout.widget.Guideline"
        )

        /** Simple XML tag → type constant (short names only, for parsing). */
        private val TAG_TO_TYPE: Map<String, Int> = TYPE_TO_CLASS_NAME.entries.associate { (k, v) ->
            v.substringAfterLast('.') to k
        }

        /** Set of type constants that represent ViewGroup (layout) types. */
        val LAYOUT_TYPES: Set<Int> = setOf(
            TYPE_LINEAR_LAYOUT, TYPE_RELATIVE_LAYOUT, TYPE_FRAME_LAYOUT,
            TYPE_CONSTRAINT_LAYOUT, TYPE_SCROLL_VIEW, TYPE_HORIZONTAL_SCROLL_VIEW,
            TYPE_RECYCLER_VIEW, TYPE_COORDINATOR_LAYOUT, TYPE_CARD_VIEW, TYPE_MATERIAL_CARD_VIEW
        )

        /** Set of type constants that support text properties. */
        val TEXT_TYPES: Set<Int> = setOf(
            TYPE_BUTTON, TYPE_TEXTVIEW, TYPE_EDITTEXT, TYPE_CHECKBOX,
            TYPE_RADIOBUTTON, TYPE_SWITCH, TYPE_TOGGLE_BUTTON,
            TYPE_IMAGE_BUTTON, TYPE_TEXT_CLOCK, TYPE_CHRONOMETER
        )

        /** Set of type constants that support image properties. */
        val IMAGE_TYPES: Set<Int> = setOf(
            TYPE_IMAGEVIEW, TYPE_IMAGE_BUTTON, TYPE_BUTTON
        )

        @JvmField val CREATOR: Parcelable.Creator<ViewBean> = object : Parcelable.Creator<ViewBean> {
            override fun createFromParcel(source: Parcel): ViewBean = ViewBean(source)
            override fun newArray(size: Int): Array<ViewBean?> = arrayOfNulls(size)
        }

        // ── Lookup helpers ─────────────────────────────────────────────

        /** Get the fully-qualified Android class name for a type constant. */
        @JvmStatic
        fun getClassName(type: Int): String = TYPE_TO_CLASS_NAME[type] ?: "View"

        /** Get the simple class name (last segment of package) for a type constant. */
        @JvmStatic
        fun getSimpleName(type: Int): String = getClassName(type).substringAfterLast('.')

        /** Resolve an XML tag name to a type constant. Returns null if unknown. */
        @JvmStatic
        fun fromTagName(tag: String): Int? {
            val simple = tag.substringAfterLast('.')
            return TAG_TO_TYPE[simple]
        }

        /** Returns true if the given type constant represents a layout (ViewGroup). */
        @JvmStatic
        fun isLayout(type: Int): Boolean = type in LAYOUT_TYPES

        /** Returns true if the given type constant supports text properties. */
        @JvmStatic
        fun isTextWidget(type: Int): Boolean = type in TEXT_TYPES

        /** Returns true if the given type constant supports image properties. */
        @JvmStatic
        fun isImageWidget(type: Int): Boolean = type in IMAGE_TYPES
    }

    // ── Parcelable ─────────────────────────────────────────────────────
    constructor(source: Parcel) : this(
        type = source.readInt(),
        id = source.readString() ?: "",
        name = source.readString() ?: "",
        parent = source.readString() ?: "",
        parentType = source.readInt(),
        index = source.readInt(),
        enabled = source.readInt() != 0,
        clickable = source.readInt() != 0,
        alpha = source.readFloat(),
        translationX = source.readFloat(),
        translationY = source.readFloat(),
        scaleX = source.readFloat(),
        scaleY = source.readFloat(),
        layout = source.readParcelable(LayoutBean::class.java.classLoader) ?: LayoutBean(),
        text = source.readParcelable(TextBean::class.java.classLoader) ?: TextBean(),
        image = source.readParcelable(ImageBean::class.java.classLoader) ?: ImageBean()
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.apply {
            writeInt(type)
            writeString(id)
            writeString(name)
            writeString(parent)
            writeInt(parentType)
            writeInt(index)
            writeInt(if (enabled) 1 else 0)
            writeInt(if (clickable) 1 else 0)
            writeFloat(alpha)
            writeFloat(translationX)
            writeFloat(translationY)
            writeFloat(scaleX)
            writeFloat(scaleY)
            writeParcelable(layout, flags)
            writeParcelable(text, flags)
            writeParcelable(image, flags)
        }
    }

    override fun describeContents(): Int = 0

    // ── Convenience accessors ─────────────────────────────────────────

    /** The fully-qualified Android class name for this widget. */
    val className: String get() = getClassName(type)

    /** The simple class name (e.g. "Button", "TextView"). */
    val simpleClassName: String get() = getSimpleName(type)

    /** Whether this widget is a layout container. */
    val isLayout: Boolean get() = isLayout(type)

    /** Whether this widget supports text properties. */
    val isTextWidget: Boolean get() = isTextWidget(type)

    /** Whether this widget supports image properties. */
    val isImageWidget: Boolean get() = isImageWidget(type)

    // ── Class info builder ────────────────────────────────────────────

    /**
     * Build a [ClassInfo] object containing import statements and view type
     * information needed for code generation.
     */
    fun buildClassInfo(): ClassInfo {
        val fqcn = className
        val simple = simpleClassName
        val pkg = if ('.' in fqcn) fqcn.substringBeforeLast('.') else "android.view"
        return ClassInfo(
            fullyQualifiedName = fqcn,
            simpleName = simple,
            packageName = pkg
        )
    }

    // ── Clone ─────────────────────────────────────────────────────────

    /** Deep clone of this ViewBean including all sub-beans. */
    fun deepClone(): ViewBean = ViewBean(
        type = type,
        id = "${id}_copy",
        name = "${name}_copy",
        parent = parent,
        parentType = parentType,
        index = index,
        enabled = enabled,
        clickable = clickable,
        alpha = alpha,
        translationX = translationX,
        translationY = translationY,
        scaleX = scaleX,
        scaleY = scaleY,
        layout = layout.clone(),
        text = text.clone(),
        image = image.clone()
    )

    // ── Data class for class info ─────────────────────────────────────

    /**
     * Holds resolved class information for a widget type, used by code generators.
     */
    data class ClassInfo(
        val fullyQualifiedName: String,
        val simpleName: String,
        val packageName: String
    ) {
        /** The import statement for this class. */
        val importStatement: String get() = "import $fullyQualifiedName"

        /** True if this class is part of the android.widget package (no explicit import needed in many cases). */
        val isAndroidWidget: Boolean get() = packageName == "android.widget"

        /** True if this class is part of android.view (no explicit import needed). */
        val isAndroidView: Boolean get() = packageName == "android.view"

        /** True if this class needs an explicit import in generated code. */
        val needsImport: Boolean get() = !isAndroidWidget && !isAndroidView
    }
}
