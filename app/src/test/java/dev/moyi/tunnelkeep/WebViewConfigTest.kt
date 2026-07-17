package dev.moyi.tunnelkeep

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class WebViewConfigTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var config: WebViewConfig

    @Before
    fun setup() {
        context = mock()
        prefs = mock()
        editor = mock()

        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.putBoolean(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)

        config = WebViewConfig(context)
    }

    @Test
    fun `getUrl returns default when no URL is stored`() {
        whenever(prefs.getString("workspace_url", null)).thenReturn(null)
        assertEquals(WebViewConfig.DEFAULT_URL, config.getUrl())
    }

    @Test
    fun `getUrl returns stored URL when valid`() {
        val stored = "https://vscode.dev/tunnel/my-instance"
        whenever(prefs.getString("workspace_url", null)).thenReturn(stored)
        assertEquals(stored, config.getUrl())
    }

    @Test
    fun `getUrl falls back to default when stored URL is not HTTPS`() {
        val stored = "http://evil.com"
        whenever(prefs.getString("workspace_url", null)).thenReturn(stored)
        assertEquals(WebViewConfig.DEFAULT_URL, config.getUrl())
    }

    @Test
    fun `getUrl falls back to default when stored URL is empty`() {
        whenever(prefs.getString("workspace_url", null)).thenReturn("")
        assertEquals(WebViewConfig.DEFAULT_URL, config.getUrl())
    }

    @Test
    fun `setUrl accepts valid HTTPS URL`() {
        val url = "https://vscode.dev/tunnel/workspace-42"
        assertTrue(config.setUrl(url))
        verify(editor).putString("workspace_url", url)
        verify(editor).putBoolean("url_set_manually", true)
    }

    @Test
    fun `setUrl rejects HTTP URL`() {
        assertFalse(config.setUrl("http://vscode.dev/tunnel/foo"))
    }

    @Test
    fun `setUrl rejects URL with spaces`() {
        assertFalse(config.setUrl("https://vscode .dev"))
    }

    @Test
    fun `setUrl rejects URL with newlines`() {
        assertFalse(config.setUrl("https://vscode.dev\n/tunnel"))
    }

    @Test
    fun `setUrl rejects empty string`() {
        assertFalse(config.setUrl(""))
        assertFalse(config.setUrl("   "))
    }

    @Test
    fun `setUrl trims whitespace from URL`() {
        val url = "  https://vscode.dev/tunnel/test  "
        assertTrue(config.setUrl(url))
        verify(editor).putString("workspace_url", "https://vscode.dev/tunnel/test")
    }

    @Test
    fun `resetToDefault clears stored URL`() {
        config.resetToDefault()
        verify(editor).remove("workspace_url")
        verify(editor).remove("url_set_manually")
    }

    @Test
    fun `isCustomUrl returns false when URL never manually set`() {
        whenever(prefs.getBoolean("url_set_manually", false)).thenReturn(false)
        assertFalse(config.isCustomUrl())
    }

    @Test
    fun `isCustomUrl returns true when URL was manually set`() {
        whenever(prefs.getBoolean("url_set_manually", false)).thenReturn(true)
        whenever(prefs.getString("workspace_url", null)).thenReturn("https://example.com")
        assertTrue(config.isCustomUrl())
    }

    @Test
    fun `isCustomUrl returns false when manually set but no URL stored`() {
        whenever(prefs.getBoolean("url_set_manually", false)).thenReturn(true)
        whenever(prefs.getString("workspace_url", null)).thenReturn(null)
        assertFalse(config.isCustomUrl())
    }

    // --- isValidUrl tests (pure logic, no Android dependency) ---

    @Test
    fun `isValidUrl accepts standard HTTPS URL`() {
        assertTrue(config.isValidUrl("https://vscode.dev"))
        assertTrue(config.isValidUrl("https://vscode.dev/tunnel/instance-20260610-18"))
        assertTrue(config.isValidUrl("https://example.com/path?query=value"))
    }

    @Test
    fun `isValidUrl rejects HTTP URL`() {
        assertFalse(config.isValidUrl("http://vscode.dev"))
    }

    @Test
    fun `isValidUrl rejects FTP URL`() {
        assertFalse(config.isValidUrl("ftp://vscode.dev"))
    }

    @Test
    fun `isValidUrl rejects plain text`() {
        assertFalse(config.isValidUrl("just-a-string"))
    }

    @Test
    fun `isValidUrl rejects URL with spaces`() {
        assertFalse(config.isValidUrl("https://vscode .dev"))
    }

    @Test
    fun `isValidUrl rejects URL with newlines`() {
        assertFalse(config.isValidUrl("https://vscode.dev\nhidden"))
    }

    @Test
    fun `isValidUrl rejects too-short HTTPS string`() {
        // "https://" is 8 chars, we require > 8
        assertFalse(config.isValidUrl("https://"))
    }

    @Test
    fun `isValidUrl rejects URL with carriage return`() {
        assertFalse(config.isValidUrl("https://vscode.dev\r"))
    }
}
