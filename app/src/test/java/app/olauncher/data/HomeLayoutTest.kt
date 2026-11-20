package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    private val cbc = HomeApp(label = "CBC", packageName = "com.kbc.cbc", activityClassName = "Main", user = "0")
    private val maps = HomeApp(label = "Maps", packageName = "com.google.maps", activityClassName = null, user = "0")
    private val chat = HomeApp(label = "Signal", packageName = "org.signal", activityClassName = null, user = "0")

    @Test
    fun `an added app appears once at the end of the home screen`() {
        val layout = HomeLayout().addApp(cbc).addApp(maps).addApp(cbc)

        assertEquals(listOf(HomeEntry.App(cbc), HomeEntry.App(maps)), layout.entries)
        assertTrue(layout.isOnHome(cbc))
    }

    @Test
    fun `removing an app takes it off the home screen only`() {
        val layout = HomeLayout().addApp(cbc).addApp(maps).removeApp(cbc)

        assertEquals(listOf(HomeEntry.App(maps)), layout.entries)
        assertFalse(layout.isOnHome(cbc))
    }

    @Test
    fun `the same package for another user is a different app`() {
        val work = cbc.copy(user = "10")
        val layout = HomeLayout().addApp(cbc).addApp(work)

        assertEquals(2, layout.entries.size)
    }

    @Test
    fun `adding to a new group creates it on the home screen`() {
        val layout = HomeLayout().addToGroup("Travel", maps)

        assertEquals(listOf(HomeEntry.Group("Travel", listOf(maps))), layout.entries)
    }

    @Test
    fun `an empty group can be created from the home screen, once per name`() {
        val layout = HomeLayout().createGroup(" Travel ").createGroup("Travel").createGroup("  ")

        assertEquals(listOf(HomeEntry.Group("Travel", emptyList())), layout.entries)
    }

    @Test
    fun `old fixed home slots become home apps in the same order`() {
        val layout = HomeLayout.fromSlots(listOf(cbc, maps, cbc))

        assertEquals(listOf(HomeEntry.App(cbc), HomeEntry.App(maps)), layout.entries)
    }

    @Test
    fun `adding to an existing group appends without duplicates`() {
        val layout = HomeLayout().addToGroup("Travel", maps).addToGroup("Travel", chat).addToGroup("Travel", maps)

        assertEquals(listOf(HomeEntry.Group("Travel", listOf(maps, chat))), layout.entries)
    }

    @Test
    fun `group names are trimmed and blank names are refused`() {
        val layout = HomeLayout().addToGroup("  Travel ", maps).addToGroup("   ", chat)

        assertEquals(listOf(HomeEntry.Group("Travel", listOf(maps))), layout.entries)
    }

    @Test
    fun `removing the last app of a group keeps the empty group`() {
        val layout = HomeLayout().addToGroup("Travel", maps).removeFromGroup("Travel", maps)

        assertEquals(listOf(HomeEntry.Group("Travel", emptyList())), layout.entries)
    }

    @Test
    fun `renaming a group keeps its apps and position`() {
        val layout = HomeLayout().addApp(cbc).addToGroup("Travel", maps).renameGroup("Travel", "Trips")

        assertEquals(listOf(HomeEntry.App(cbc), HomeEntry.Group("Trips", listOf(maps))), layout.entries)
    }

    @Test
    fun `renaming onto an existing name is refused`() {
        val start = HomeLayout().addToGroup("Travel", maps).addToGroup("Chat", chat)

        assertEquals(start, start.renameGroup("Travel", "Chat"))
    }

    @Test
    fun `deleting a group removes it and nothing else`() {
        val layout = HomeLayout().addApp(cbc).addToGroup("Travel", maps).deleteGroup("Travel")

        assertEquals(listOf(HomeEntry.App(cbc)), layout.entries)
    }

    @Test
    fun `moving an entry up and down stays within bounds`() {
        val layout = HomeLayout().addApp(cbc).addApp(maps).addApp(chat)

        assertEquals(listOf("Maps", "CBC", "Signal"), layout.move(1, -1).labels())
        assertEquals(listOf("CBC", "Maps", "Signal"), layout.move(0, -1).labels())
        assertEquals(listOf("CBC", "Maps", "Signal"), layout.move(2, 1).labels())
    }

    @Test
    fun `uninstalled apps are dropped from the home screen and from groups`() {
        val layout = HomeLayout().addApp(cbc).addToGroup("Travel", maps).addToGroup("Travel", chat)

        val kept = layout.retainInstalled { it.packageName != "com.google.maps" && it.packageName != "com.kbc.cbc" }

        assertEquals(listOf(HomeEntry.Group("Travel", listOf(chat))), kept.entries)
    }

    @Test
    fun `encoding then decoding gives back the same layout`() {
        val layout = HomeLayout().addApp(cbc).addToGroup("Travel: \"far\"", maps).addToGroup("Travel: \"far\"", chat)

        assertEquals(layout, HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout)))
    }

    @Test
    fun `decoding nothing or garbage gives an empty layout`() {
        assertEquals(HomeLayout(), HomeLayoutCodec.decode(null))
        assertEquals(HomeLayout(), HomeLayoutCodec.decode("not json"))
    }

    private fun HomeLayout.labels() = entries.map {
        when (it) {
            is HomeEntry.App -> it.app.label
            is HomeEntry.Group -> it.name
        }
    }
}
