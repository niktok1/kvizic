package io.ntole.kvizic

import io.ntole.kvizic.core.network.AndroidTokenStorage
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The session stays on its phone: a copy restored from a backup, or moved to a new phone, would share one
 * refresh-token family with the phone it came from, and whichever refreshed less would become a fresh guest.
 * So every backup rule keeps out the file the token storage writes, by its name, which this holds to it.
 */
class BackupRulesTest {
    private val sessionFile = "${AndroidTokenStorage.FILE_NAME}.xml"

    @Test
    fun theManifestNamesBothRuleFiles() {
        val manifest = parse(File(MAIN, "AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element
        assertEquals("@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"))
        assertEquals("@xml/backup_rules", application.getAttribute("android:fullBackupContent"))
    }

    /** Android 12 and later: the cloud backup and a device-to-device transfer alike. */
    @Test
    fun theSessionIsKeptOutOfTheCloudAndOfATransfer() {
        val rules = parse(File(RES, "xml/data_extraction_rules.xml"))
        listOf("cloud-backup", "device-transfer").forEach { section ->
            val excluded =
                (rules.getElementsByTagName(section).item(0) as Element).let { exclusionsIn(it) }
            assertTrue(sessionFile in excluded, "$section keeps out $excluded, not $sessionFile")
        }
    }

    /** Android 11 and earlier, one set of rules for both. */
    @Test
    fun theSessionIsKeptOutOfAnOlderPhonesBackup() {
        val excluded = exclusionsIn(parse(File(RES, "xml/backup_rules.xml")))
        assertTrue(sessionFile in excluded, "the backup keeps out $excluded, not $sessionFile")
    }

    /** The shared preferences files [element] excludes. */
    private fun exclusionsIn(element: Element): List<String> {
        val nodes = element.getElementsByTagName("exclude")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .filter { it.getAttribute("domain") == "sharedpref" }
            .map { it.getAttribute("path") }
    }

    private fun parse(file: File) =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement

    private companion object {
        // A unit test runs in its module's directory.
        val MAIN = File("src/main")
        val RES = File(MAIN, "res")
    }
}
