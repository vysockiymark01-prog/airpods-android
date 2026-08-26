package io.github.vysockiymark01_prog.airpods.ble

enum class NoiseControlMode { OFF, TRANSPARENCY, ANC, ADAPTIVE }

/**
 * AACP (Apple Accessory Communication Protocol) noise-control command bytes, keyed by device
 * model ID so different hardware generations that use different byte values can coexist.
 *
 * The values below for the "identifier 0x0D" listening-mode command come from the
 * community-documented AACP packet format (see README "Источники протокола"). This identifier
 * has been consistent across every AirPods Pro/Max generation published so far — that's why one
 * entry currently covers all of them. If Apple changes it for a future model (explicitly called
 * out as a risk for AirPods Pro 3 in the spec), add a new entry here rather than editing the
 * shared one.
 *
 * This table is intentionally data, not code: [RemoteCommandTableStore] can overlay a JSON blob
 * fetched from a config URL on top of these defaults, so a wrong/incomplete byte sequence for a
 * new model doesn't require a Play Store app update to fix — see that class for the format.
 */
object AacpCommandTable {

    /** AACP header for every control packet: 4-byte header + 2-byte little-endian opcode. */
    private val PACKET_HEADER = byteArrayOf(0x04, 0x00, 0x04, 0x00)
    private const val OPCODE_CONTROL_COMMAND = 0x09
    private const val IDENTIFIER_LISTENING_MODE = 0x0D

    private fun listeningModePacket(modeByte: Int): ByteArray = PACKET_HEADER +
        byteArrayOf(OPCODE_CONTROL_COMMAND.toByte(), 0x00, IDENTIFIER_LISTENING_MODE.toByte(), modeByte.toByte(), 0x00, 0x00, 0x00)

    /** Default (built-in) command bytes, applicable to every model with [AirPodsModel.supportsAnc]. */
    private val defaultModeBytes = mapOf(
        NoiseControlMode.OFF to 0x01,
        NoiseControlMode.ANC to 0x02,
        NoiseControlMode.TRANSPARENCY to 0x03,
        NoiseControlMode.ADAPTIVE to 0x04,
    )

    /**
     * Per-model override map, populated at runtime from [RemoteCommandTableStore]. Empty by
     * default — the app ships only with [defaultModeBytes].
     */
    @Volatile
    private var remoteOverrides: Map<Int, Map<NoiseControlMode, Int>> = emptyMap()

    fun applyRemoteOverrides(overrides: Map<Int, Map<NoiseControlMode, Int>>) {
        remoteOverrides = overrides
    }

    /**
     * Returns the AACP packet to switch [model] to [mode], or null if we have no known-good
     * command for this model — callers must show "не поддерживается для этой модели" rather
     * than guessing.
     */
    fun packetFor(model: AirPodsModel, rawModelId: Int, mode: NoiseControlMode): ByteArray? {
        val overrideByte = remoteOverrides[rawModelId]?.get(mode)
        if (overrideByte != null) return listeningModePacket(overrideByte)

        if (!model.supportsAnc && model != AirPodsModel.UNKNOWN) return null
        if (model == AirPodsModel.UNKNOWN) return null // never guess for an unrecognized model

        val modeByte = defaultModeBytes[mode] ?: return null
        return listeningModePacket(modeByte)
    }
}

/**
 * Loads a JSON overlay for [AacpCommandTable] from a URL the user can configure in Settings —
 * this is the "возможность удалённого обновления констант" the spec calls for, since the AACP
 * byte values for brand-new hardware (e.g. AirPods Pro 3 Auto-ANC) may only become known after
 * this app is already installed.
 *
 * Expected JSON shape:
 * ```json
 * {
 *   "0x2A20": { "OFF": 1, "ANC": 2, "TRANSPARENCY": 3, "ADAPTIVE": 4 }
 * }
 * ```
 * Keys are hex model IDs (as broadcast in the Proximity Pairing packet), values map mode name to
 * the single data byte sent in the AACP listening-mode command.
 */
object RemoteCommandTableStore {
    fun parse(json: String): Map<Int, Map<NoiseControlMode, Int>> {
        // Minimal hand-rolled parser to avoid pulling in a JSON dependency for a tiny, rarely
        // fetched config file. Replace with kotlinx.serialization if the schema grows.
        val result = mutableMapOf<Int, Map<NoiseControlMode, Int>>()
        val modelRegex = Regex("\"0x([0-9A-Fa-f]{4})\"\\s*:\\s*\\{([^}]*)}")
        val entryRegex = Regex("\"(OFF|ANC|TRANSPARENCY|ADAPTIVE)\"\\s*:\\s*(\\d+)")
        for (m in modelRegex.findAll(json)) {
            val modelId = m.groupValues[1].toInt(16)
            val body = m.groupValues[2]
            val modes = entryRegex.findAll(body).associate {
                NoiseControlMode.valueOf(it.groupValues[1]) to it.groupValues[2].toInt()
            }
            if (modes.isNotEmpty()) result[modelId] = modes
        }
        return result
    }
}
