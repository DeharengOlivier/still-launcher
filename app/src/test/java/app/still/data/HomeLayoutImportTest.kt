package app.still.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutImportTest {

    private val installed = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.google.android.apps.maps" to "Maps",
        "com.waze" to "Waze",
    )

    private fun resolve(packageName: String): HomeApp? =
        installed[packageName]?.let { HomeApp(it, packageName, null, "0") }

    @Test
    fun `apps and groups are imported in order with their real labels`() {
        val spec = """{"entries":[{"app":"com.whatsapp"},{"group":"Travel","apps":["com.google.android.apps.maps","com.waze"]}]}"""

        val result = HomeLayoutImport.parse(spec, ::resolve)

        assertEquals(
            listOf(
                HomeEntry.App(resolve("com.whatsapp")!!),
                HomeEntry.Group("Travel", listOf(resolve("com.google.android.apps.maps")!!, resolve("com.waze")!!)),
            ),
            result.layout.entries,
        )
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun `apps that are not installed are skipped and reported`() {
        val spec = """{"entries":[{"app":"com.gone"},{"group":"Travel","apps":["com.waze","com.gone.too"]}]}"""

        val result = HomeLayoutImport.parse(spec, ::resolve)

        assertEquals(listOf(HomeEntry.Group("Travel", listOf(resolve("com.waze")!!))), result.layout.entries)
        assertEquals(listOf("com.gone", "com.gone.too"), result.missing)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a malformed spec is rejected instead of wiping the home screen`() {
        HomeLayoutImport.parse("""{"entries":[{"nothing":1}]}""", ::resolve)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `text that is not json is rejected`() {
        HomeLayoutImport.parse("hello", ::resolve)
    }
}
