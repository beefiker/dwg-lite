package dev.jaeyoung.dwg

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.math.atan2
import kotlin.math.min

enum class DwgLiteVersion(val header: String) {
    AC1012("AC1012"),
    AC1014("AC1014"),
    AC1015("AC1015"),
    AC1018("AC1018"),
    AC1021("AC1021"),
    AC1024("AC1024"),
    AC1027("AC1027"),
    AC1032("AC1032");

    companion object {
        fun fromHeader(header: String): DwgLiteVersion? {
            return entries.firstOrNull { it.header == header }
        }
    }
}

data class DwgLitePoint(
    val x: Double,
    val y: Double,
    val z: Double = 0.0
)

sealed interface DwgLiteEntity {
    val sourceHandle: Long?

    data class Point(
        val position: DwgLitePoint,
        override val sourceHandle: Long? = null
    ) : DwgLiteEntity

    data class Line(
        val start: DwgLitePoint,
        val end: DwgLitePoint,
        override val sourceHandle: Long? = null
    ) : DwgLiteEntity

    data class Circle(
        val center: DwgLitePoint,
        val radius: Double,
        override val sourceHandle: Long? = null
    ) : DwgLiteEntity

    data class Arc(
        val center: DwgLitePoint,
        val radius: Double,
        val startAngleRadians: Double,
        val endAngleRadians: Double,
        override val sourceHandle: Long? = null
    ) : DwgLiteEntity

    data class Polyline(
        val points: List<DwgLitePoint>,
        val closed: Boolean,
        override val sourceHandle: Long? = null
    ) : DwgLiteEntity

    data class Text(
        val position: DwgLitePoint,
        val value: String,
        val height: Double,
        val rotationRadians: Double,
        override val sourceHandle: Long? = null
    ) : DwgLiteEntity
}

data class DwgLiteDocument(
    val version: DwgLiteVersion,
    val entities: List<DwgLiteEntity>,
    val unsupportedEntityCount: Int = 0
)

enum class DwgLiteUnsupportedReason {
    NOT_DWG,
    UNSUPPORTED_VERSION,
    EMPTY_OR_UNSUPPORTED,
    TOO_LARGE
}

sealed interface DwgLiteParseResult {
    data class Success(val document: DwgLiteDocument) : DwgLiteParseResult
    data class Unsupported(
        val reason: DwgLiteUnsupportedReason,
        val version: DwgLiteVersion? = null
    ) : DwgLiteParseResult
    data class Failure(val message: String) : DwgLiteParseResult
}

class DwgLiteParser(
    private val maxBytes: Int = DefaultMaxBytes,
    private val maxEntities: Int = DefaultMaxEntities
) {
    fun parse(input: InputStream): DwgLiteParseResult {
        val bytes = input.readCapped(maxBytes)
            ?: return DwgLiteParseResult.Unsupported(DwgLiteUnsupportedReason.TOO_LARGE)
        if (bytes.size < DwgVersionHeaderSize) {
            return DwgLiteParseResult.Unsupported(DwgLiteUnsupportedReason.NOT_DWG)
        }

        val header = String(bytes, 0, DwgVersionHeaderSize, StandardCharsets.US_ASCII)
        val version = DwgLiteVersion.fromHeader(header)
            ?: return DwgLiteParseResult.Unsupported(DwgLiteUnsupportedReason.NOT_DWG)

        return when (version) {
            DwgLiteVersion.AC1015 -> parseAc1015(bytes, version)
            else -> DwgLiteParseResult.Unsupported(
                reason = DwgLiteUnsupportedReason.UNSUPPORTED_VERSION,
                version = version
            )
        }
    }

    private fun parseAc1015(bytes: ByteArray, version: DwgLiteVersion): DwgLiteParseResult {
        return runCatching {
            val sections = Ac1015FileHeader.read(bytes)
            val handles = readHandles(sections.slice(bytes, SectionHandles))
                .mapValues { (_, offset) -> offset - sections.objectsBaseOffset }
            val objects = sections.slice(bytes, SectionAcDbObjects)
            val entities = ArrayList<DwgLiteEntity>()
            var unsupportedEntities = 0

            handles.entries
                .sortedBy { it.value }
                .forEach { (handle, offset) ->
                    if (entities.size >= maxEntities) return@forEach
                    val record = DwgObjectRecord.readAt(
                        data = objects,
                        offset = offset.toInt(),
                        version = version
                    ) ?: return@forEach
                    when (record.typeCode) {
                        DwgObjectTypeText -> record.readTextEntity(handle)?.let(entities::add)
                        DwgObjectTypeArc -> record.readArcEntity(handle)?.let(entities::add)
                        DwgObjectTypeCircle -> record.readCircleEntity(handle)?.let(entities::add)
                        DwgObjectTypeLine -> record.readLineEntity(handle)?.let(entities::add)
                        DwgObjectTypePoint -> record.readPointEntity(handle)?.let(entities::add)
                        DwgObjectType3dFace -> record.readFace3dEntity(handle)?.let(entities::add)
                        DwgObjectTypeSolid,
                        DwgObjectTypeTrace -> record.readSolidEntity(handle)?.let(entities::add)
                        DwgObjectTypeMText -> record.readMTextEntity(handle)?.let(entities::add)
                        DwgObjectTypeLwPolyline -> record.readLwPolylineEntity(handle)?.let(entities::add)
                        in DwgFixedEntityTypes -> unsupportedEntities += 1
                    }
                }

            if (entities.isEmpty()) {
                DwgLiteParseResult.Unsupported(
                    reason = DwgLiteUnsupportedReason.EMPTY_OR_UNSUPPORTED,
                    version = version
                )
            } else {
                DwgLiteParseResult.Success(
                    DwgLiteDocument(
                        version = version,
                        entities = entities,
                        unsupportedEntityCount = unsupportedEntities
                    )
                )
            }
        }.getOrElse { error ->
            DwgLiteParseResult.Failure(error.message ?: "Unable to parse DWG")
        }
    }

    private companion object {
        private const val DwgVersionHeaderSize = 6
        private const val DefaultMaxBytes = 16 * 1024 * 1024
        private const val DefaultMaxEntities = 20_000
        private const val DwgObjectTypeText = 1
        private const val DwgObjectTypeArc = 17
        private const val DwgObjectTypeCircle = 18
        private const val DwgObjectTypeLine = 19
        private const val DwgObjectTypePoint = 27
        private const val DwgObjectType3dFace = 28
        private const val DwgObjectTypeSolid = 31
        private const val DwgObjectTypeTrace = 32
        private const val DwgObjectTypeMText = 44
        private const val DwgObjectTypeLwPolyline = 77
        private val DwgFixedEntityTypes = setOf(
            1, 2, 3, 4, 5, 6, 7, 8,
            10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31,
            32, 33, 34, 35, 36, 37, 38, 39, 40, 41,
            43, 44, 45, 46, 47, 74, 77, 78
        )
    }
}

private const val SectionHeader = 0
private const val SectionClasses = 1
private const val SectionHandles = 2
private const val SectionObjFreeSpace = 3
private const val SectionTemplate = 4
private const val SectionAuxHeader = 5
private const val SectionAcDbObjects = 100

private data class SectionLocator(
    val offset: Int,
    val size: Int
)

private data class Ac1015FileHeader(
    val sections: Map<Int, SectionLocator>,
    val objectsBaseOffset: Long
) {
    fun slice(bytes: ByteArray, section: Int): ByteArray {
        val locator = sections[section]
            ?: error("Missing DWG section $section")
        if (locator.offset < 0 || locator.size < 0 || locator.offset + locator.size > bytes.size) {
            error("Invalid DWG section $section range")
        }
        return bytes.copyOfRange(locator.offset, locator.offset + locator.size)
    }

    companion object {
        fun read(bytes: ByteArray): Ac1015FileHeader {
            if (bytes.size < 0x19) error("DWG header is too short")
            val recordCount = bytes.readIntLe(0x15).coerceAtMost(6)
            val sections = linkedMapOf<Int, SectionLocator>()
            var pos = 0x19
            repeat(recordCount) {
                if (pos + 9 > bytes.size) error("DWG locator table is truncated")
                val number = bytes[pos].toInt() and 0xFF
                val offset = bytes.readIntLe(pos + 1)
                val size = bytes.readIntLe(pos + 5)
                sections[number] = SectionLocator(offset, size)
                pos += 9
            }

            val handles = sections[SectionHandles] ?: error("DWG handles section is missing")
            val auxHeader = sections[SectionAuxHeader]
            val objectsOffset = auxHeader?.let { it.offset + it.size } ?: run {
                var fallbackOffset = 0x61
                for (section in listOf(SectionHeader, SectionClasses, SectionObjFreeSpace, SectionTemplate, SectionAuxHeader)) {
                    fallbackOffset += sections[section]?.size ?: 0
                }
                fallbackOffset
            }
            val objectsSize = handles.offset - objectsOffset
            if (objectsSize <= 0) error("DWG object section is empty")

            sections[SectionAcDbObjects] = SectionLocator(objectsOffset, objectsSize)
            return Ac1015FileHeader(
                sections = sections,
                objectsBaseOffset = objectsOffset.toLong()
            )
        }
    }
}

private data class ModularValue(
    val value: Long,
    val bytesRead: Int
)

private fun readHandles(data: ByteArray): Map<Long, Long> {
    val handles = linkedMapOf<Long, Long>()
    var pos = 0
    while (pos + 2 <= data.size) {
        val size = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2
        if (size <= 2 || size > 2048) break

        val chunkEnd = min(pos + size - 2, data.size)
        var lastHandle = 0L
        var lastOffset = 0L
        while (pos < chunkEnd) {
            val handleDelta = readMc(data, pos)
            pos += handleDelta.bytesRead
            val offsetDelta = readSmc(data, pos)
            pos += offsetDelta.bytesRead

            lastHandle += handleDelta.value
            lastOffset += offsetDelta.value
            handles[lastHandle] = lastOffset
        }

        if (pos + 2 <= data.size) pos += 2
    }
    return handles
}

private fun readMc(data: ByteArray, offset: Int): ModularValue {
    var value = 0L
    var shift = 0
    var pos = offset
    while (pos < data.size) {
        val byte = data[pos].toInt() and 0xFF
        pos += 1
        value = value or ((byte and 0x7F).toLong() shl shift)
        if ((byte and 0x80) == 0) break
        shift += 7
    }
    return ModularValue(value, pos - offset)
}

private fun readSmc(data: ByteArray, offset: Int): ModularValue {
    var value = 0L
    var shift = 0
    var pos = offset
    var lastByte = 0
    while (pos < data.size) {
        val byte = data[pos].toInt() and 0xFF
        pos += 1
        lastByte = byte
        if ((byte and 0x80) == 0) {
            value = value or ((byte and 0x3F).toLong() shl shift)
            break
        } else {
            value = value or ((byte and 0x7F).toLong() shl shift)
            shift += 7
        }
    }
    val signed = if ((lastByte and 0x40) != 0) -value else value
    return ModularValue(signed, pos - offset)
}

private data class DwgObjectRecord(
    val typeCode: Int,
    private val reader: DwgMergedReader
) {
    fun readTextEntity(sourceHandle: Long): DwgLiteEntity.Text? {
        return runCatching {
            reader.readCommonEntityData()
            val dataFlags = reader.readByte()
            val elevation = if ((dataFlags and 0x01) == 0) reader.readRawDouble() else 0.0
            val x = reader.readRawDouble()
            val y = reader.readRawDouble()
            if ((dataFlags and 0x02) == 0) {
                reader.readBitDoubleWithDefault(x)
                reader.readBitDoubleWithDefault(y)
            }
            reader.readBitExtrusion()
            reader.readBitThickness()
            if ((dataFlags and 0x04) == 0) reader.readRawDouble()
            val rotation = if ((dataFlags and 0x08) == 0) reader.readRawDouble() else 0.0
            val height = reader.readRawDouble()
            if ((dataFlags and 0x10) == 0) reader.readRawDouble()
            val value = reader.readVariableText()
            if ((dataFlags and 0x20) == 0) reader.readBitShort()
            if ((dataFlags and 0x40) == 0) reader.readBitShort()
            if ((dataFlags and 0x80) == 0) reader.readBitShort()
            reader.readHandle()
            DwgLiteEntity.Text(
                position = DwgLitePoint(x, y, elevation),
                value = value,
                height = height,
                rotationRadians = rotation,
                sourceHandle = sourceHandle
            )
        }.getOrNull()
    }

    fun readMTextEntity(sourceHandle: Long): DwgLiteEntity.Text? {
        return runCatching {
            reader.readCommonEntityData()
            val position = reader.read3BitDouble()
            reader.read3BitDouble()
            val xDirection = reader.read3BitDouble()
            reader.readBitDouble()
            val height = reader.readBitDouble()
            reader.readBitShort()
            reader.readBitShort()
            reader.readBitDouble()
            reader.readBitDouble()
            val value = reader.readVariableText()
            reader.readHandle()
            reader.readBitShort()
            reader.readBitDouble()
            reader.readBit()
            DwgLiteEntity.Text(
                position = position,
                value = value,
                height = height,
                rotationRadians = atan2(xDirection.y, xDirection.x),
                sourceHandle = sourceHandle
            )
        }.getOrNull()
    }

    fun readSolidEntity(sourceHandle: Long): DwgLiteEntity.Polyline? {
        return runCatching {
            reader.readCommonEntityData()
            reader.readBitThickness()
            val elevation = reader.readBitDouble()
            val first = reader.read2RawDouble(elevation)
            val second = reader.read2RawDouble(elevation)
            val third = reader.read2RawDouble(elevation)
            val fourth = reader.read2RawDouble(elevation)
            reader.readBitExtrusion()
            DwgLiteEntity.Polyline(
                points = listOf(first, second, third, fourth).withoutClosingDuplicate(),
                closed = true,
                sourceHandle = sourceHandle
            )
        }.getOrNull()
    }

    fun readFace3dEntity(sourceHandle: Long): DwgLiteEntity.Polyline? {
        return runCatching {
            reader.readCommonEntityData()
            val hasNoFlags = reader.readBit()
            val zValuesAreSame = reader.readBit()
            val x1 = reader.readRawDouble()
            val y1 = reader.readRawDouble()
            val z1 = if (zValuesAreSame) 0.0 else reader.readRawDouble()

            val x2 = reader.readBitDoubleWithDefault(x1)
            val y2 = reader.readBitDoubleWithDefault(y1)
            val z2 = if (zValuesAreSame) z1 else reader.readBitDoubleWithDefault(z1)

            val x3 = reader.readBitDoubleWithDefault(x2)
            val y3 = reader.readBitDoubleWithDefault(y2)
            val z3 = if (zValuesAreSame) z1 else reader.readBitDoubleWithDefault(z2)

            val x4 = reader.readBitDoubleWithDefault(x3)
            val y4 = reader.readBitDoubleWithDefault(y3)
            val z4 = if (zValuesAreSame) z1 else reader.readBitDoubleWithDefault(z3)

            if (!hasNoFlags) reader.readBitShort()
            DwgLiteEntity.Polyline(
                points = listOf(
                    DwgLitePoint(x1, y1, z1),
                    DwgLitePoint(x2, y2, z2),
                    DwgLitePoint(x3, y3, z3),
                    DwgLitePoint(x4, y4, z4)
                ).withoutClosingDuplicate(),
                closed = true,
                sourceHandle = sourceHandle
            )
        }.getOrNull()
    }

    fun readPointEntity(sourceHandle: Long): DwgLiteEntity.Point? {
        return runCatching {
            reader.readCommonEntityData()
            val position = reader.read3BitDouble()
            reader.readBitThickness()
            reader.readBitExtrusion()
            reader.readBitDouble()
            DwgLiteEntity.Point(
                position = position,
                sourceHandle = sourceHandle
            )
        }.getOrNull()
    }

    fun readCircleEntity(sourceHandle: Long): DwgLiteEntity.Circle? {
        return runCatching {
            reader.readCommonEntityData()
            val center = reader.read3BitDouble()
            val radius = reader.readBitDouble()
            reader.readBitThickness()
            reader.readBitExtrusion()
            DwgLiteEntity.Circle(
                center = center,
                radius = radius,
                sourceHandle = sourceHandle
            )
        }.getOrNull()
    }

    fun readArcEntity(sourceHandle: Long): DwgLiteEntity.Arc? {
        return runCatching {
            reader.readCommonEntityData()
            val center = reader.read3BitDouble()
            val radius = reader.readBitDouble()
            reader.readBitThickness()
            reader.readBitExtrusion()
            val startAngle = reader.readBitDouble()
            val endAngle = reader.readBitDouble()
            DwgLiteEntity.Arc(
                center = center,
                radius = radius,
                startAngleRadians = startAngle,
                endAngleRadians = endAngle,
                sourceHandle = sourceHandle
            )
        }.getOrNull()
    }

    fun readLineEntity(sourceHandle: Long): DwgLiteEntity.Line? {
        return runCatching {
            reader.readCommonEntityData()
            val zValuesAreZero = reader.readBit()
            val startX = reader.readRawDouble()
            val endX = reader.readBitDoubleWithDefault(startX)
            val startY = reader.readRawDouble()
            val endY = reader.readBitDoubleWithDefault(startY)
            val (startZ, endZ) = if (zValuesAreZero) {
                0.0 to 0.0
            } else {
                val z = reader.readRawDouble()
                z to reader.readBitDoubleWithDefault(z)
            }
            reader.readBitThickness()
            reader.readBitExtrusion()
            DwgLiteEntity.Line(
                start = DwgLitePoint(startX, startY, startZ),
                end = DwgLitePoint(endX, endY, endZ),
                sourceHandle = sourceHandle
            )
        }.getOrNull()
    }

    fun readLwPolylineEntity(sourceHandle: Long): DwgLiteEntity.Polyline? {
        return runCatching {
            reader.readCommonEntityData()
            val flags = reader.readBitShort()
            val hasConstantWidth = (flags and 0x4) != 0
            val hasElevation = (flags and 0x8) != 0
            val hasThickness = (flags and 0x2) != 0
            val hasNormal = (flags and 0x1) != 0
            val hasBulges = (flags and 0x10) != 0
            val hasWidths = (flags and 0x20) != 0
            val hasVertexIds = (flags and 0x400) != 0

            if (hasConstantWidth) reader.readBitDouble()
            val elevation = if (hasElevation) reader.readBitDouble() else 0.0
            if (hasThickness) reader.readBitThickness()
            if (hasNormal) reader.readBitExtrusion()

            val pointCount = reader.readBitLong()
            if (pointCount !in 1..MaxLwPolylinePointCount) return@runCatching null
            val bulgeCount = if (hasBulges) reader.readBitLong().coerceIn(0, MaxLwPolylinePointCount) else 0
            val vertexIdCount = if (hasVertexIds) reader.readBitLong().coerceIn(0, MaxLwPolylinePointCount) else 0
            val widthCount = if (hasWidths) reader.readBitLong().coerceIn(0, MaxLwPolylinePointCount) else 0

            val points = ArrayList<DwgLitePoint>(pointCount)
            var previousX = reader.readRawDouble()
            var previousY = reader.readRawDouble()
            points += DwgLitePoint(previousX, previousY, elevation)
            repeat(pointCount - 1) {
                previousX = reader.readBitDoubleWithDefault(previousX)
                previousY = reader.readBitDoubleWithDefault(previousY)
                points += DwgLitePoint(previousX, previousY, elevation)
            }

            repeat(bulgeCount) { reader.readBitDouble() }
            repeat(vertexIdCount) { reader.readBitLong() }
            repeat(widthCount) {
                reader.readBitDouble()
                reader.readBitDouble()
            }

            DwgLiteEntity.Polyline(
                points = points,
                closed = (flags and 0x200) != 0,
                sourceHandle = sourceHandle
            )
        }.getOrNull()
    }

    companion object {
        private const val MaxLwPolylinePointCount = 100_000

        fun readAt(data: ByteArray, offset: Int, version: DwgLiteVersion): DwgObjectRecord? {
            if (offset < 0 || offset >= data.size) return null
            var pos = offset
            val size = readModularShort(data, pos)
            pos += size.bytesRead
            val recordSize = size.value.toInt()
            if (recordSize <= 0 || pos + recordSize > data.size) return null

            val mergedData = data.copyOfRange(pos, pos + recordSize)
            val temp = DwgBitReader(mergedData, version)
            temp.readObjectType()
            val handleStartBits = temp.readRawLong()

            val main = DwgBitReader(mergedData, version)
            val handle = DwgBitReader(mergedData, version).apply {
                setPositionInBits(handleStartBits)
            }
            val mergedReader = DwgMergedReader(main, handle, version)
            val typeCode = mergedReader.readObjectType()
            return DwgObjectRecord(typeCode, mergedReader)
        }
    }
}

private fun readModularShort(data: ByteArray, offset: Int): ModularValue {
    var value = 0L
    var shift = 0
    var pos = offset
    while (pos + 1 < data.size) {
        val word = (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        value = value or ((word and 0x7FFF).toLong() shl shift)
        shift += 15
        if ((word and 0x8000) == 0) break
    }
    return ModularValue(value, pos - offset)
}

private class DwgMergedReader(
    private val main: DwgBitReader,
    private val handle: DwgBitReader,
    private val version: DwgLiteVersion
) {
    private var referenceHandle = 0L

    fun readObjectType(): Int = main.readObjectType()
    fun readBit(): Boolean = main.readBit()
    fun readByte(): Int = main.readByte()
    fun readRawLong(): Long = main.readRawLong()
    fun readRawDouble(): Double = main.readRawDouble()
    fun read2RawDouble(z: Double): DwgLitePoint = main.read2RawDouble(z)
    fun read3BitDouble(): DwgLitePoint = main.read3BitDouble()
    fun readBitShort(): Int = main.readBitShort()
    fun readBitLong(): Int = main.readBitLong()
    fun readBitDouble(): Double = main.readBitDouble()
    fun readBitDoubleWithDefault(default: Double): Double = main.readBitDoubleWithDefault(default)
    fun readBitThickness(): Double = main.readBitThickness()
    fun readBitExtrusion(): DwgLitePoint = main.readBitExtrusion()
    fun readVariableText(): String = main.readVariableText()

    fun readHandle(): Long = handle.readHandle(referenceHandle)

    fun readCommonEntityData() {
        if (version == DwgLiteVersion.AC1015) {
            main.readRawLong()
        }

        val objectHandle = main.readHandle(0)
        referenceHandle = objectHandle

        readExtendedDataRaw()

        val hasGraphic = readBit()
        if (hasGraphic) {
            val graphicSize = readRawLong().coerceAtLeast(0L).coerceAtMost(MaxSkippedByteCount.toLong())
            repeat(graphicSize.toInt()) { readByte() }
        }

        val entityMode = main.read2Bits()
        if (entityMode == 0) readHandle()

        val reactorCount = readBitLong().coerceIn(0, MaxArrayCount)
        repeat(reactorCount) { readHandle() }

        readHandle()

        val noLinks = readBit()
        if (!noLinks) {
            readHandle()
            readHandle()
        }

        readBitShort()
        readBitDouble()
        readHandle()

        val linetypeFlags = main.read2Bits()
        if (linetypeFlags == 0b11) readHandle()

        val plotstyleFlags = main.read2Bits()
        if (plotstyleFlags == 0b11) readHandle()

        readBitShort()
        readByte()
    }

    private fun readExtendedDataRaw() {
        while (true) {
            val size = readBitShort()
            if (size <= 0) break
            val safeSize = size.coerceAtMost(MaxSkippedByteCount)
            main.readHandle(0)
            repeat(safeSize) { readByte() }
        }
    }

    private companion object {
        private const val MaxArrayCount = 100_000
        private const val MaxSkippedByteCount = 1_000_000
    }
}

private class DwgBitReader(
    private val data: ByteArray,
    private val version: DwgLiteVersion
) {
    private var position = 0
    private var bitShift = 0
    private var lastByte = 0

    fun setPositionInBits(positionBits: Long) {
        position = (positionBits shr 3).toInt().coerceIn(0, data.size)
        bitShift = (positionBits and 7L).toInt()
        if (bitShift > 0) advanceByte()
    }

    fun readBit(): Boolean {
        return if (bitShift == 0) {
            advanceByte()
            bitShift = 1
            (lastByte and 0x80) == 0x80
        } else {
            val value = ((lastByte shl bitShift) and 0x80) == 0x80
            bitShift = (bitShift + 1) and 7
            value
        }
    }

    fun read2Bits(): Int {
        return if (bitShift == 0) {
            advanceByte()
            bitShift = 2
            lastByte ushr 6
        } else if (bitShift == 7) {
            val lastValue = (lastByte shl 1) and 2
            advanceByte()
            bitShift = 1
            lastValue or (lastByte ushr 7)
        } else {
            val value = (lastByte ushr (6 - bitShift)) and 3
            bitShift = (bitShift + 2) and 7
            value
        }
    }

    fun readByte(): Int {
        return if (bitShift == 0) {
            advanceByte()
            lastByte
        } else {
            val previous = lastByte
            advanceByte()
            ((previous shl bitShift) or (lastByte ushr (8 - bitShift))) and 0xFF
        }
    }

    fun readBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        for (index in bytes.indices) {
            bytes[index] = readByte().toByte()
        }
        return bytes
    }

    fun readBitShort(): Int {
        return when (read2Bits()) {
            0 -> readRawShort()
            1 -> readByte()
            2 -> 0
            3 -> 256
            else -> 0
        }
    }

    fun readBitLong(): Int {
        return when (read2Bits()) {
            0 -> readRawLong().toInt()
            1 -> readByte()
            2 -> 0
            else -> 0
        }
    }

    fun readBitDouble(): Double {
        return when (read2Bits()) {
            0 -> readRawDouble()
            1 -> 1.0
            2 -> 0.0
            else -> 0.0
        }
    }

    fun readBitDoubleWithDefault(default: Double): Double {
        val bytes = default.toLittleEndianBytes()
        when (read2Bits()) {
            0 -> return default
            1 -> {
                bytes[0] = readByte().toByte()
                bytes[1] = readByte().toByte()
                bytes[2] = readByte().toByte()
                bytes[3] = readByte().toByte()
            }
            2 -> {
                bytes[4] = readByte().toByte()
                bytes[5] = readByte().toByte()
                bytes[0] = readByte().toByte()
                bytes[1] = readByte().toByte()
                bytes[2] = readByte().toByte()
                bytes[3] = readByte().toByte()
            }
            3 -> return readRawDouble()
        }
        return bytes.toLittleEndianDouble()
    }

    fun readRawShort(): Int {
        val b0 = readByte()
        val b1 = readByte()
        return (b0 or (b1 shl 8)).toShort().toInt()
    }

    fun readRawLong(): Long {
        val b0 = readByte()
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        return (b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)).toLong()
    }

    fun readRawDouble(): Double {
        return readBytes(8).toLittleEndianDouble()
    }

    fun read2RawDouble(z: Double): DwgLitePoint {
        return DwgLitePoint(
            x = readRawDouble(),
            y = readRawDouble(),
            z = z
        )
    }

    fun readVariableText(): String {
        val length = readBitShort()
        if (length <= 0) return ""
        val safeLength = length.coerceAtMost(MaxVariableTextBytes)
        val bytes = readBytes(safeLength)
        repeat(length - safeLength) { readByte() }
        return String(bytes, StandardCharsets.ISO_8859_1).replace("\u0000", "")
    }

    fun readBitThickness(): Double {
        return if (version >= DwgLiteVersion.AC1015) {
            if (readBit()) 0.0 else readBitDouble()
        } else {
            readBitDouble()
        }
    }

    fun readBitExtrusion(): DwgLitePoint {
        return if (version >= DwgLiteVersion.AC1015) {
            if (readBit()) DwgLitePoint(0.0, 0.0, 1.0) else read3BitDouble()
        } else {
            read3BitDouble()
        }
    }

    fun readObjectType(): Int = readBitShort()

    fun readHandle(referenceHandle: Long): Long {
        val form = readByte()
        val code = form ushr 4
        val counter = form and 0x0F
        return when {
            code <= 0x5 -> readHandleBytes(counter)
            code == 0x6 -> referenceHandle + 1
            code == 0x8 -> referenceHandle - 1
            code == 0xA -> referenceHandle + readHandleBytes(counter)
            code == 0xC -> referenceHandle - readHandleBytes(counter)
            else -> 0L
        }
    }

    fun read3BitDouble(): DwgLitePoint {
        return DwgLitePoint(
            x = readBitDouble(),
            y = readBitDouble(),
            z = readBitDouble()
        )
    }

    private fun readHandleBytes(length: Int): Long {
        var value = 0L
        repeat(length.coerceAtMost(8)) {
            value = (value shl 8) or readByte().toLong()
        }
        return value
    }

    private fun advanceByte() {
        lastByte = if (position < data.size) {
            data[position].toInt() and 0xFF
        } else {
            0
        }
        position += 1
    }

    private companion object {
        private const val MaxVariableTextBytes = 1_000_000
    }
}

private fun ByteArray.readIntLe(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)
}

private fun ByteArray.toLittleEndianDouble(): Double {
    var bits = 0L
    for (index in 7 downTo 0) {
        bits = (bits shl 8) or (this[index].toLong() and 0xFF)
    }
    return Double.fromBits(bits)
}

private fun Double.toLittleEndianBytes(): ByteArray {
    var bits = toRawBits()
    val bytes = ByteArray(8)
    for (index in bytes.indices) {
        bytes[index] = (bits and 0xFF).toByte()
        bits = bits ushr 8
    }
    return bytes
}

private fun InputStream.readCapped(maxBytes: Int): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun List<DwgLitePoint>.withoutClosingDuplicate(): List<DwgLitePoint> {
    return if (size > 1 && first() == last()) dropLast(1) else this
}
