package app.restvolt.camperlog.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Das Logo im Repository ist von Hand aus den Launcher-Vektoren übertragen und darf nicht davon abweichen. */
class LogoSvgTest {

    private fun androidPaths(name: String) =
        Regex("""android:pathData="([^"]*)"""").findAll(File("src/main/res/drawable/$name.xml").readText()).map { it.groupValues[1] }.sorted().toList()

    private fun svgPaths(name: String) =
        Regex("""\sd="([^"]*)"""").findAll(File("../logo/$name.svg").readText()).map { it.groupValues[1] }.sorted().toList()

    @Test
    fun iconSvgMatchesLauncherForeground() = assertEquals(androidPaths("ic_launcher_foreground"), svgPaths("icon"))

    @Test
    fun monochromeSvgMatchesLauncherMonochrome() = assertEquals(androidPaths("ic_launcher_monochrome"), svgPaths("icon-monochrome"))
}
