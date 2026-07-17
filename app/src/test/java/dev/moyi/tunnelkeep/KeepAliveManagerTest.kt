package dev.moyi.tunnelkeep

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class KeepAliveManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var manager: KeepAliveManager

    @Before
    fun setup() {
        context = mock()
        prefs = mock()
        editor = mock()

        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putBoolean(any(), any())).thenReturn(editor)

        manager = KeepAliveManager(context)
    }

    @Test
    fun `isEnabled returns false by default`() {
        whenever(prefs.getBoolean("keep_alive_enabled", false)).thenReturn(false)
        assertFalse(manager.isEnabled())
    }

    @Test
    fun `isEnabled returns true when enabled`() {
        whenever(prefs.getBoolean("keep_alive_enabled", false)).thenReturn(true)
        assertTrue(manager.isEnabled())
    }

    @Test
    fun `setEnabled true persists true`() {
        manager.setEnabled(true)
        verify(editor).putBoolean("keep_alive_enabled", true)
    }

    @Test
    fun `setEnabled false persists false`() {
        manager.setEnabled(false)
        verify(editor).putBoolean("keep_alive_enabled", false)
    }

    @Test
    fun `toggle from false to true`() {
        whenever(prefs.getBoolean("keep_alive_enabled", false)).thenReturn(false)
        assertFalse(manager.isEnabled())

        manager.setEnabled(true)
        verify(editor).putBoolean("keep_alive_enabled", true)
    }

    @Test
    fun `toggle from true to false`() {
        whenever(prefs.getBoolean("keep_alive_enabled", false)).thenReturn(true)
        assertTrue(manager.isEnabled())

        manager.setEnabled(false)
        verify(editor).putBoolean("keep_alive_enabled", false)
    }
}
