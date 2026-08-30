package io.github.vysockiymark01_prog.airpods.ble

/** Battery level of a single component, as broadcast by the earbuds. */
sealed class BatteryLevel {
    data class Percent(val value: Int) : BatteryLevel() // rounded to nearest 10%, per firmware limits
    object Unavailable : BatteryLevel()
}

data class AirPodsStatus(
    val model: AirPodsModel,
    val rawModelId: Int,
    val leftBattery: BatteryLevel,
    val rightBattery: BatteryLevel,
    val caseBattery: BatteryLevel,
    val leftInEar: Boolean,
    val rightInEar: Boolean,
    val leftInCase: Boolean,
    val rightInCase: Boolean,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    val lidOpen: Boolean,
    /** Elapsed-time-since-boot ms when this reading was captured — for "обновлено N мин назад". */
    val observedAtElapsedRealtimeMs: Long,
)

/**
 * Parses Apple's "Proximity Pairing" manufacturer-specific BLE advertisement (company ID 0x004C,
 * message type 0x07). This is a broadcast, unencrypted-metadata packet any nearby BLE scanner can
 * read — no pairing or connection required.
 *
 * Byte layout (see README "Источники протокола" for the reverse-engineering sources this is based
 * on — OpenPods / AAP (librepods) / capod, cross-checked against each other):
 *
 * ```
 * offset  size  field
 * 0       1     prefix, always 0x07
 * 1       1     length, always 0x19 (25 bytes follow)
 * 2       1     pairing status (0x01 = paired to this device's Apple ID, 0x00 = pairing mode)
 * 3-4     2     device model, big-endian
 * 5       1     status bitfield (ear detection / in-case flags — see below)
 * 6       1     pod battery: high nibble = right, low nibble = left (0-9 -> 0-90%, 0xA-0xE -> 100%, 0xF -> n/a)
 * 7       1     high nibble = case battery (same encoding); low nibble = charging flags
 * 8       1     lid: bit 3 = lid open, bits 0-2 = rolling counter
 * 9       1     device color (unused here)
 * 10      1     connection state (unused here)
 * 11-26   16    encrypted payload (undocumented, not needed for battery/ear-detection)
 * ```
 *
 * Status byte (offset 5) bit layout:
 * bit0-1 = right ear-detection bits, bit2 = both pods in case, bit3 = left ear-detection bit,
 * bit4 = exactly one pod in case, bit5 = 1 if left pod is "primary" (the one broadcasting),
 * bit6 = the broadcasting pod itself is in its case.
 */
object ProximityPairingParser {

    private const val APPLE_COMPANY_ID = 0x004C
    private const val MESSAGE_TYPE_PROXIMITY_PAIRING = 0x07
    private const val EXPECTED_LENGTH = 0x19

    /**
     * @param manufacturerSpecificData the raw bytes Android hands back for company ID 0x004C
     *        (i.e. `scanRecord.getManufacturerSpecificData(0x004C)`), WITHOUT the company-ID
     *        prefix that some platforms include — see [io.github.vysockiymark01_prog.airpods.ble.AirPodsScanService]
     *        for how this is extracted from the raw scan record.
     */
    fun parse(manufacturerSpecificData: ByteArray, elapsedRealtimeMs: Long): AirPodsStatus? {
        if (manufacturerSpecificData.size < 27) return null
        val bytes = manufacturerSpecificData
        if ((bytes[0].toInt() and 0xFF) != MESSAGE_TYPE_PROXIMITY_PAIRING) return null
        if ((bytes[1].toInt() and 0xFF) != EXPECTED_LENGTH) return null

        val modelId = ((bytes[3].toInt() and 0xFF) shl 8) or (bytes[4].toInt() and 0xFF)
        val model = AirPodsModel.fromId(modelId)

        val status = bytes[5].toInt() and 0xFF
        val bothInCase = (status shr 2) and 0x1 == 1
        val oneInCase = (status shr 4) and 0x1 == 1
        val leftIsPrimary = (status shr 5) and 0x1 == 1
        val thisPodInCase = (status shr 6) and 0x1 == 1
        val rightEarBit = status and 0x1
        val leftEarBit = (status shr 3) and 0x1

        // The two ear-detection bits are relative to which pod is "primary" (broadcasting). We
        // normalize to absolute left/right using leftIsPrimary + the in-case flags, matching the
        // interpretation used by OpenPods/librepods.
        val leftInEar = leftEarBit == 0
        val rightInEar = rightEarBit == 0

        val leftInCase: Boolean
        val rightInCase: Boolean
        when {
            bothInCase -> {
                leftInCase = true; rightInCase = true
            }
            oneInCase -> {
                // Whichever pod is NOT primary is the one sitting alone in the case, unless the
                // broadcasting pod itself reports being in the case.
                if (thisPodInCase) {
                    leftInCase = leftIsPrimary
                    rightInCase = !leftIsPrimary
                } else {
                    leftInCase = !leftIsPrimary
                    rightInCase = leftIsPrimary
                }
            }
            else -> {
                leftInCase = false; rightInCase = false
            }
        }

        val podByte = bytes[6].toInt() and 0xFF
        val highNibble = (podByte shr 4) and 0xF
        val lowNibble = podByte and 0xF
        // Just like the charging flags below, this nibble pair is "primary pod" / "secondary pod",
        // not "right pod" / "left pod" directly — which physical earbud is primary (the one whose
        // radio is currently broadcasting) changes over time. The previous version of this parser
        // ignored that and always read the high nibble as the right pod's battery, which silently
        // swapped left/right (and therefore also showed the wrong number under the wrong earbud)
        // every time the non-default pod happened to be primary. Same primary/secondary bit as
        // leftIsPrimary above.
        val rightRaw = if (leftIsPrimary) lowNibble else highNibble
        val leftRaw = if (leftIsPrimary) highNibble else lowNibble

        val caseAndChargeByte = bytes[7].toInt() and 0xFF
        val caseRaw = (caseAndChargeByte shr 4) and 0xF
        val chargeFlags = caseAndChargeByte and 0xF
        // bit0/bit1 = pod charging (assignment depends on which pod is primary), bit2 = case charging
        val primaryCharging = (chargeFlags and 0x1) == 1
        val secondaryCharging = (chargeFlags and 0x2) == 0x2
        val caseCharging = (chargeFlags and 0x4) == 0x4
        val leftCharging = if (leftIsPrimary) primaryCharging else secondaryCharging
        val rightCharging = if (leftIsPrimary) secondaryCharging else primaryCharging

        val lidByte = bytes[8].toInt() and 0xFF
        val lidOpen = (lidByte shr 3) and 0x1 == 1

        return AirPodsStatus(
            model = model,
            rawModelId = modelId,
            leftBattery = decodeBattery(leftRaw),
            rightBattery = decodeBattery(rightRaw),
            caseBattery = decodeBattery(caseRaw),
            leftInEar = leftInEar,
            rightInEar = rightInEar,
            leftInCase = leftInCase,
            rightInCase = rightInCase,
            leftCharging = leftCharging,
            rightCharging = rightCharging,
            caseCharging = caseCharging,
            lidOpen = lidOpen,
            observedAtElapsedRealtimeMs = elapsedRealtimeMs,
        )
    }

    private fun decodeBattery(nibble: Int): BatteryLevel = when (nibble) {
        0xF -> BatteryLevel.Unavailable
        in 0x0..0x9 -> BatteryLevel.Percent(nibble * 10)
        in 0xA..0xE -> BatteryLevel.Percent(100)
        else -> BatteryLevel.Unavailable
    }
}
