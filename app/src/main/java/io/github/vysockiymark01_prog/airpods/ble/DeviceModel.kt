package io.github.vysockiymark01_prog.airpods.ble

/**
 * Apple "Proximity Pairing" (message type 0x07) device-model IDs, as documented by independent
 * reverse-engineering projects (OpenPods, AAP/librepods, capod — see README "Источники протокола").
 *
 * IMPORTANT: [AIRPODS_PRO_3]'s real broadcast ID is NOT known. As of the time this was written,
 * no community reverse-engineering project has published a confirmed model ID or ANC command set
 * for it — see README "Известные ограничения". It's listed here with a sentinel ID purely so it
 * can be picked from the manual model-override list (see [io.github.vysockiymark01_prog.airpods.ble.ModelOverridePreferences])
 * — auto-detection can never assign it by itself. Any device with a genuinely unrecognized ID
 * (Pro 3 included, until its real ID is confirmed) still gets battery + ear-detection support
 * (that part of the packet format is model-independent); it just falls back to [UNKNOWN]'s
 * generic silhouette/icon and ANC switching is disabled until the real ID is added here.
 */
enum class AirPodsModel(
    val modelId: Int,
    val displayName: String,
    val hasStem: Boolean,
    val supportsAnc: Boolean,
) {
    AIRPODS_1(0x0220, "AirPods (1‑го поколения)", hasStem = true, supportsAnc = false),
    AIRPODS_2(0x0F20, "AirPods (2‑го поколения)", hasStem = true, supportsAnc = false),
    AIRPODS_3(0x1320, "AirPods (3‑го поколения)", hasStem = true, supportsAnc = false),
    AIRPODS_4(0x1920, "AirPods (4‑го поколения)", hasStem = true, supportsAnc = false),
    AIRPODS_4_ANC(0x1B20, "AirPods 4 (ANC)", hasStem = true, supportsAnc = true),
    AIRPODS_MAX_LIGHTNING(0x0A20, "AirPods Max", hasStem = false, supportsAnc = true),
    AIRPODS_MAX_USBC(0x1F20, "AirPods Max (USB‑C)", hasStem = false, supportsAnc = true),
    AIRPODS_PRO_1(0x0E20, "AirPods Pro", hasStem = true, supportsAnc = true),
    AIRPODS_PRO_2_LIGHTNING(0x1420, "AirPods Pro 2", hasStem = true, supportsAnc = true),
    AIRPODS_PRO_2_USBC(0x2420, "AirPods Pro 2 (USB‑C)", hasStem = true, supportsAnc = true),

    /**
     * Manual-selection-only placeholder — see the class doc. -100 is a sentinel that can never
     * come from a real broadcast (Apple's ID field is always 0x0000..0xFFFF), so auto-detection
     * can never assign this by mistake; update to the real ID once one is confirmed publicly.
     */
    AIRPODS_PRO_3(-100, "AirPods Pro 3", hasStem = true, supportsAnc = true),

    UNKNOWN(-1, "Наушники Apple", hasStem = true, supportsAnc = false);

    companion object {
        private val byId = entries.filter { it != UNKNOWN }.associateBy { it.modelId }

        /** [supportsAnc] here is a placeholder — actual command bytes come from [AacpCommandTable]. */
        fun fromId(id: Int): AirPodsModel = byId[id] ?: UNKNOWN
    }
}
