package dev.jaeyoung.dwg

import java.io.ByteArrayInputStream
import java.util.Base64
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DwgLiteParserTest {
    @Test
    fun parsesAcadrustR2000LineFixture() {
        val bytes = Base64.getMimeDecoder().decode(Ac1015LineFixture)

        val result = DwgLiteParser().parse(ByteArrayInputStream(bytes))

        assertTrue("Expected Success but was $result", result is DwgLiteParseResult.Success)
        val document = (result as DwgLiteParseResult.Success).document
        assertEquals(DwgLiteVersion.AC1015, document.version)
        assertEquals(1, document.entities.size)

        val line = document.entities.single()
        assertTrue(line is DwgLiteEntity.Line)
        line as DwgLiteEntity.Line
        assertClose(0.0, line.start.x)
        assertClose(0.0, line.start.y)
        assertClose(0.0, line.start.z)
        assertClose(100.0, line.end.x)
        assertClose(100.0, line.end.y)
        assertClose(0.0, line.end.z)
    }

    @Test
    fun parsesAcadrustR2000PointFixture() {
        val result = DwgLiteParser().parse(readFixture("ac1015-point.dwg"))

        assertTrue("Expected Success but was $result", result is DwgLiteParseResult.Success)
        val document = (result as DwgLiteParseResult.Success).document
        assertEquals(DwgLiteVersion.AC1015, document.version)
        assertEquals(1, document.entities.size)

        val point = document.entities.single()
        assertTrue(point is DwgLiteEntity.Point)
        point as DwgLiteEntity.Point
        assertClose(50.0, point.position.x)
        assertClose(50.0, point.position.y)
        assertClose(0.0, point.position.z)
    }

    @Test
    fun parsesAcadrustR2000CircleFixture() {
        val result = DwgLiteParser().parse(readFixture("ac1015-circle.dwg"))

        assertTrue("Expected Success but was $result", result is DwgLiteParseResult.Success)
        val document = (result as DwgLiteParseResult.Success).document
        assertEquals(DwgLiteVersion.AC1015, document.version)
        assertEquals(1, document.entities.size)

        val circle = document.entities.single()
        assertTrue(circle is DwgLiteEntity.Circle)
        circle as DwgLiteEntity.Circle
        assertClose(50.0, circle.center.x)
        assertClose(50.0, circle.center.y)
        assertClose(0.0, circle.center.z)
        assertClose(25.0, circle.radius)
    }

    @Test
    fun parsesAcadrustR2000ArcFixture() {
        val result = DwgLiteParser().parse(readFixture("ac1015-arc.dwg"))

        assertTrue("Expected Success but was $result", result is DwgLiteParseResult.Success)
        val document = (result as DwgLiteParseResult.Success).document
        assertEquals(DwgLiteVersion.AC1015, document.version)
        assertEquals(1, document.entities.size)

        val arc = document.entities.single()
        assertTrue(arc is DwgLiteEntity.Arc)
        arc as DwgLiteEntity.Arc
        assertClose(50.0, arc.center.x)
        assertClose(50.0, arc.center.y)
        assertClose(0.0, arc.center.z)
        assertClose(25.0, arc.radius)
        assertClose(0.0, arc.startAngleRadians)
        assertClose(PI, arc.endAngleRadians)
    }

    @Test
    fun parsesAcadrustR2000EllipseFixtureAsClosedPolyline() {
        val result = DwgLiteParser().parse(readFixture("ac1015-ellipse.dwg"))

        assertTrue("Expected Success but was $result", result is DwgLiteParseResult.Success)
        val document = (result as DwgLiteParseResult.Success).document
        assertEquals(DwgLiteVersion.AC1015, document.version)
        assertEquals(1, document.entities.size)

        val polyline = document.entities.single()
        assertTrue(polyline is DwgLiteEntity.Polyline)
        polyline as DwgLiteEntity.Polyline
        assertEquals(true, polyline.closed)
        assertEquals(64, polyline.points.size)
        assertClose(90.0, polyline.points[0].x)
        assertClose(50.0, polyline.points[0].y)
        assertClose(50.0, polyline.points[16].x)
        assertClose(70.0, polyline.points[16].y)
        assertClose(10.0, polyline.points[32].x)
        assertClose(50.0, polyline.points[32].y)
        assertClose(50.0, polyline.points[48].x)
        assertClose(30.0, polyline.points[48].y)
    }

    @Test
    fun parsesAcadrustR2000LwPolylineFixture() {
        val result = DwgLiteParser().parse(readFixture("ac1015-lwpolyline.dwg"))

        assertTrue("Expected Success but was $result", result is DwgLiteParseResult.Success)
        val document = (result as DwgLiteParseResult.Success).document
        assertEquals(DwgLiteVersion.AC1015, document.version)
        assertEquals(1, document.entities.size)

        val polyline = document.entities.single()
        assertTrue(polyline is DwgLiteEntity.Polyline)
        polyline as DwgLiteEntity.Polyline
        assertEquals(false, polyline.closed)
        assertEquals(4, polyline.points.size)
        assertClose(0.0, polyline.points[0].x)
        assertClose(0.0, polyline.points[0].y)
        assertClose(10.0, polyline.points[1].x)
        assertClose(0.0, polyline.points[1].y)
        assertClose(10.0, polyline.points[2].x)
        assertClose(10.0, polyline.points[2].y)
        assertClose(0.0, polyline.points[3].x)
        assertClose(10.0, polyline.points[3].y)
    }

    @Test
    fun parsesAcadrustR2000LeaderFixtureAsOpenPolyline() {
        val result = DwgLiteParser().parse(readFixture("ac1015-leader.dwg"))

        assertTrue("Expected Success but was $result", result is DwgLiteParseResult.Success)
        val document = (result as DwgLiteParseResult.Success).document
        assertEquals(DwgLiteVersion.AC1015, document.version)
        assertEquals(1, document.entities.size)

        val polyline = document.entities.single()
        assertTrue(polyline is DwgLiteEntity.Polyline)
        polyline as DwgLiteEntity.Polyline
        assertEquals(false, polyline.closed)
        assertEquals(2, polyline.points.size)
        assertClose(0.0, polyline.points[0].x)
        assertClose(0.0, polyline.points[0].y)
        assertClose(0.0, polyline.points[0].z)
        assertClose(10.0, polyline.points[1].x)
        assertClose(10.0, polyline.points[1].y)
        assertClose(0.0, polyline.points[1].z)
    }

    @Test
    fun parsesAcadrustR2000SolidFixtureAsClosedPolyline() {
        val result = DwgLiteParser().parse(readFixture("ac1015-solid.dwg"))

        assertTrue("Expected Success but was $result", result is DwgLiteParseResult.Success)
        val document = (result as DwgLiteParseResult.Success).document
        assertEquals(DwgLiteVersion.AC1015, document.version)
        assertEquals(1, document.entities.size)

        val polyline = document.entities.single()
        assertTrue(polyline is DwgLiteEntity.Polyline)
        polyline as DwgLiteEntity.Polyline
        assertEquals(true, polyline.closed)
        assertEquals(4, polyline.points.size)
        assertClose(0.0, polyline.points[0].x)
        assertClose(0.0, polyline.points[0].y)
        assertClose(0.0, polyline.points[0].z)
        assertClose(10.0, polyline.points[1].x)
        assertClose(0.0, polyline.points[1].y)
        assertClose(10.0, polyline.points[2].x)
        assertClose(10.0, polyline.points[2].y)
        assertClose(0.0, polyline.points[3].x)
        assertClose(10.0, polyline.points[3].y)
    }

    @Test
    fun parsesAcadrustR2000Face3dFixtureAsClosedPolyline() {
        val result = DwgLiteParser().parse(readFixture("ac1015-face3d.dwg"))

        assertTrue("Expected Success but was $result", result is DwgLiteParseResult.Success)
        val document = (result as DwgLiteParseResult.Success).document
        assertEquals(DwgLiteVersion.AC1015, document.version)
        assertEquals(1, document.entities.size)

        val polyline = document.entities.single()
        assertTrue(polyline is DwgLiteEntity.Polyline)
        polyline as DwgLiteEntity.Polyline
        assertEquals(true, polyline.closed)
        assertEquals(4, polyline.points.size)
        assertClose(0.0, polyline.points[0].x)
        assertClose(0.0, polyline.points[0].y)
        assertClose(0.0, polyline.points[0].z)
        assertClose(10.0, polyline.points[1].x)
        assertClose(0.0, polyline.points[1].y)
        assertClose(0.0, polyline.points[1].z)
        assertClose(10.0, polyline.points[2].x)
        assertClose(10.0, polyline.points[2].y)
        assertClose(5.0, polyline.points[2].z)
        assertClose(0.0, polyline.points[3].x)
        assertClose(10.0, polyline.points[3].y)
        assertClose(5.0, polyline.points[3].z)
    }

    @Test
    fun parsesAcadrustR2000TextFixture() {
        val result = DwgLiteParser().parse(readFixture("ac1015-text.dwg"))

        assertTrue("Expected Success but was $result", result is DwgLiteParseResult.Success)
        val document = (result as DwgLiteParseResult.Success).document
        assertEquals(DwgLiteVersion.AC1015, document.version)
        assertEquals(1, document.entities.size)

        val text = document.entities.single()
        assertTrue(text is DwgLiteEntity.Text)
        text as DwgLiteEntity.Text
        assertEquals("Hello World", text.value)
        assertClose(0.0, text.position.x)
        assertClose(0.0, text.position.y)
        assertClose(0.0, text.position.z)
        assertClose(1.0, text.height)
        assertClose(0.0, text.rotationRadians)
    }

    @Test
    fun parsesAcadrustR2000MTextFixture() {
        val result = DwgLiteParser().parse(readFixture("ac1015-mtext.dwg"))

        assertTrue("Expected Success but was $result", result is DwgLiteParseResult.Success)
        val document = (result as DwgLiteParseResult.Success).document
        assertEquals(DwgLiteVersion.AC1015, document.version)
        assertEquals(1, document.entities.size)

        val text = document.entities.single()
        assertTrue(text is DwgLiteEntity.Text)
        text as DwgLiteEntity.Text
        assertEquals("Multi\\Pline\\PText", text.value)
        assertClose(0.0, text.position.x)
        assertClose(0.0, text.position.y)
        assertClose(0.0, text.position.z)
        assertClose(1.0, text.height)
        assertClose(0.0, text.rotationRadians)
    }

    private fun assertClose(expected: Double, actual: Double) {
        assertEquals(expected, actual, 0.000001)
    }

    private fun readFixture(name: String) =
        checkNotNull(javaClass.getResourceAsStream("/dwg/$name")) { "Missing DWG fixture $name" }

    private companion object {
        private val Ac1015LineFixture = """
            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
            RBArAQAAAADg2pL4K8nX12KoNcBiu+/U
        """.trimIndent()
    }
}
