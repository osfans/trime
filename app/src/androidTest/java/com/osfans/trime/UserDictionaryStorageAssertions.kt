// SPDX-FileCopyrightText: 2015 - 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime

import android.os.Build
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeConfig
import com.osfans.trime.core.RimeDispatcher
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.userdict.CustomPhraseStore
import com.osfans.trime.data.userdict.DictionaryFileStore
import com.osfans.trime.data.userdict.DictionaryTextFormats
import com.osfans.trime.data.userdict.UserDictManager
import com.osfans.trime.util.appContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/** Production file and JNI paths on a dedicated emulator, never on an existing user's device. */
internal object UserDictionaryStorageAssertions {
    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    private fun inventory(root: File): Map<String, String> {
        if (!root.exists()) return emptyMap()
        val result = linkedMapOf<String, String>()
        fun visit(file: File, relative: String) {
            check(file.canonicalFile == file.absoluteFile) { "Storage fixture will not follow symbolic links" }
            if (file.isDirectory) {
                result["$relative/"] = "directory"
                checkNotNull(file.listFiles()).sortedBy { it.name }.forEach { child ->
                    visit(child, if (relative.isEmpty()) child.name else "$relative/${child.name}")
                }
            } else {
                check(file.isFile)
                result[relative] = digest(file)
            }
        }
        visit(root, "")
        return result
    }

    private fun exportedRecords(bytes: ByteArray): Map<Pair<String, String>, Int> = bytes.toString(Charsets.UTF_8)
        .lineSequence().filter { it.isNotEmpty() && !it.startsWith('#') }
        .associate { line ->
            val fields = line.split('\t')
            check(fields.size == 3)
            (fields[0] to fields[1].trim()) to fields[2].toInt()
        }

    /** Caller keeps its ordinary editor active and removes parentPrefix fixtures after native stop. */
    suspend fun verify(session: RimeSession, dispatcher: RimeDispatcher, parentPrefix: String): Int {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(File(checkNotNull(appContext.getExternalFilesDir(null)), "runtime-audit-dedicated").isFile)
        val token = UUID.randomUUID().toString()
        val owned = "${parentPrefix}_storage_${token.replace("-", "")}"
        val root = DataManager.userDataDir.canonicalFile
        val privateRoot = appContext.filesDir.canonicalFile
        val phraseFile = File(root, CustomPhraseStore.FILE_NAME)
        val backups = File(privateRoot, "userdict-backups")
        // Holds deliberately do not start with parentPrefix: outer fixture cleanup
        // must never delete an original file if restoring that file fails.
        val heldPhrase = File(root, ".t10-held-phrase-$token")
        val heldBackups = File(privateRoot, ".t10-held-backups-$token")
        val journalData = File(root, "${owned}_journal")
        val journalBackups = File(privateRoot, "${owned}_journal_backups")
        val dictionaryName = "${owned}_db"
        val dictionary = File(root, "$dictionaryName.userdb")
        val schemaId = "${owned}_phrases"
        val schemaFile = File(root, "$schemaId.schema.yaml")
        var snapshotFile: File? = null
        var originalPhrase = emptyMap<String, String>()
        var originalBackups = emptyMap<String, String>()
        var originalSchema = ""
        var originalAscii = false
        var phraseMoved = false
        var backupsMoved = false
        var replacementOwned = false
        var checks = 0
        var failure: Throwable? = null

        suspend fun <T> maintained(block: () -> T): T = session.runOnReady { maintainUserDictionaries(block) }

        suspend fun phraseCandidate(code: String, expected: String, excluded: String? = null) {
            session.runOnReady {
                check(selectSchema(schemaId))
                setRuntimeOption("ascii_mode", false)
                clearComposition()
                code.forEach { check(processKey(it.code)) }
                val snapshot = candidateSnapshotCached
                check(snapshot.isValid)
                val texts = getCandidates(0, 16, snapshot).map { it.text }
                check(expected in texts) { "Updated stabledb phrase is absent from bounded candidates" }
                check(excluded == null || excluded !in texts) { "Old stabledb phrase cache survived maintenance" }
                clearComposition()
            }
            checks++
        }

        suspend fun records(): Map<Pair<String, String>, Int> = maintained {
            val output = ByteArrayOutputStream()
            UserDictManager.exportUserDict(output, dictionaryName, "$owned-export.txt").getOrThrow()
            exportedRecords(output.toByteArray())
        }

        suspend fun restoreOwnedState() {
            val cleanup = runCatching {
                withContext(NonCancellable) {
                    withTimeout(120_000) {
                        if (originalSchema.isNotEmpty()) {
                            session.runOnReady {
                                check(selectSchema(originalSchema))
                                setRuntimeOption("ascii_mode", originalAscii)
                                clearComposition()
                            }
                        }
                        suspend fun restoreOwnedStorageFiles() {
                            maintained {
                                if (replacementOwned) {
                                    check(!phraseFile.exists() || (phraseFile.isFile && phraseFile.canonicalFile == phraseFile.absoluteFile && phraseFile.delete()))
                                    if (phraseMoved) {
                                        check(heldPhrase.renameTo(phraseFile))
                                        phraseMoved = false
                                    }
                                    if (backups.exists()) {
                                        check(backups.canonicalFile == backups.absoluteFile)
                                        checkNotNull(backups.listFiles()).filter(File::isDirectory).forEach { directory ->
                                            val metadata = File(directory, "metadata")
                                            check(metadata.isFile)
                                            val value = Properties().apply { metadata.inputStream().use(::load) }
                                            check(value.getProperty("target") in setOf(CustomPhraseStore.FILE_NAME, "$dictionaryName.userdb"))
                                        }
                                        check(backups.deleteRecursively())
                                    }
                                    if (backupsMoved) {
                                        check(heldBackups.renameTo(backups))
                                        backupsMoved = false
                                    }
                                } else {
                                    if (phraseMoved) {
                                        check(heldPhrase.renameTo(phraseFile))
                                        phraseMoved = false
                                    }
                                    if (backupsMoved) {
                                        check(heldBackups.renameTo(backups))
                                        backupsMoved = false
                                    }
                                }
                                if (snapshotFile?.exists() == true) check(checkNotNull(snapshotFile).delete())
                                checkNotNull(root.listFiles()).filter { it.name.startsWith(owned) }.forEach { file ->
                                    inventory(file)
                                    check(file.deleteRecursively())
                                }
                                if (journalBackups.exists()) {
                                    inventory(journalBackups)
                                    check(journalBackups.deleteRecursively())
                                }
                                check(inventory(phraseFile) == originalPhrase)
                                check(inventory(backups) == originalBackups)
                            }
                        }
                        restoreOwnedStorageFiles()
                    }
                }
            }.exceptionOrNull()
            if (cleanup != null) {
                val primaryFailure = failure
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanup) else throw cleanup
            }
        }

        try {
            session.runOnReady {
                originalSchema = selectedSchemaId()
                originalAscii = getRuntimeOption("ascii_mode")
            }
            maintained {
                check(!heldPhrase.exists() && !heldBackups.exists() && !dictionary.exists() && !schemaFile.exists())
                check(!File(backups, "pending").exists()) { "Storage assertions require a recovered dictionary store" }
                originalPhrase = inventory(phraseFile)
                originalBackups = inventory(backups)
                if (phraseFile.exists()) {
                    check(phraseFile.isFile && phraseFile.renameTo(heldPhrase))
                    phraseMoved = true
                }
                if (backups.exists()) {
                    check(backups.isDirectory && backups.renameTo(heldBackups))
                    backupsMoved = true
                }
                check(backups.mkdir())
                replacementOwned = true
            }
            checks++
            suspend fun verifyInitialStablePhrases() {
                val initial = "示例萤火句\tabc\t4\n示例青舟句\t222\t3\n"
                maintained { check(CustomPhraseStore.save(initial) == 2) }
                check(phraseFile.readBytes().contentEquals(initial.toByteArray()))
                dispatcher.runConfined {
                    check(schemaFile.createNewFile())
                    schemaFile.writeText(
                        "schema:\n  schema_id: $schemaId\n  name: T10 storage stabledb fixture\n  version: \"1\"\n" +
                            "switches:\n  - name: ascii_mode\n    reset: 0\n    states: [native, ascii]\n" +
                            "engine:\n  processors: [ascii_composer, speller, selector, navigator, express_editor]\n" +
                            "  segmentors: [ascii_segmentor, abc_segmentor, fallback_segmentor]\n" +
                            "  translators: [table_translator@custom_phrase]\n" +
                            "speller:\n  alphabet: abcdefghijklmnopqrstuvwxyz987654321\n" +
                            "custom_phrase:\n  dictionary: \"\"\n  user_dict: custom_phrase\n  db_class: stabledb\n" +
                            "  enable_completion: false\n  enable_sentence: false\n  initial_quality: 1\nmenu:\n  page_size: 5\n",
                    )
                    check(Rime.deployRimeSchemaFile(schemaFile.absolutePath))
                }
                checks++
                phraseCandidate("abc", "示例萤火句")
                phraseCandidate("222", "示例青舟句")
            }
            verifyInitialStablePhrases()

            suspend fun verifyPhraseEditingAndRecovery() {
                val imported = "# T10 imported phrases\r\n示例晨星句\tabc\t8\r\n示例青舟句\t222\t3\r\n"
                maintained {
                    check(CustomPhraseStore.importPhrases(ByteArrayInputStream(imported.toByteArray())) == 2)
                    check(CustomPhraseStore.read() == imported)
                }
                checks++
                phraseCandidate("abc", "示例晨星句", "示例萤火句")
                val exported = maintained { ByteArrayOutputStream().also(CustomPhraseStore::export).toByteArray() }
                check(exported.contentEquals(imported.toByteArray()))
                checks++

                val edited = "示例繁星句\tabc\t9\n示例青舟句\t222\t3\n示例微光句\tdef\t2\n"
                maintained { check(CustomPhraseStore.save(edited) == 3) }
                phraseCandidate("abc", "示例繁星句", "示例晨星句")
                val beforeInvalid = inventory(backups)
                maintained {
                    val error = runCatching { CustomPhraseStore.importPhrases(ByteArrayInputStream("invalid row\n".toByteArray())) }.exceptionOrNull()
                    check(error is IOException)
                }
                check(phraseFile.readBytes().contentEquals(edited.toByteArray()))
                check(inventory(backups) == beforeInvalid)
                checks++
                maintained {
                    check(CustomPhraseStore.hasRecoveryBackup())
                    CustomPhraseStore.recover()
                }
                check(phraseFile.readBytes().contentEquals(imported.toByteArray()))
                phraseCandidate("abc", "示例晨星句", "示例繁星句")
                checks++
            }
            verifyPhraseEditingAndRecovery()

            suspend fun verifyInterruptedJournalRecovery() {
                maintained {
                    check(journalData.mkdir())
                    val target = File(journalData, "owned.userdb").apply { check(mkdir()) }
                    val original = byteArrayOf(0, 1, 2, 127, -128, -1)
                    File(target, "CURRENT").writeBytes(original)
                    check(File(target, "empty").mkdir())
                    val files = DictionaryFileStore(journalData, journalBackups)
                    val backup = files.capture(target.name)
                    files.beginMutation(backup)
                    File(target, "CURRENT").writeBytes(byteArrayOf(7, 8))
                    File(target, "new.ldb").writeText("fictional interrupted write")
                    check(File(journalBackups, "pending").isFile)
                    // A new instance must discover all recovery state from real files.
                    check(DictionaryFileStore(journalData, journalBackups).recoverPendingMutation())
                    check(File(target, "CURRENT").readBytes().contentEquals(original))
                    check(!File(target, "new.ldb").exists() && File(target, "empty").isDirectory)
                    check(!File(journalBackups, "pending").exists())
                    check(!DictionaryFileStore(journalData, journalBackups).recoverPendingMutation())
                }
                checks++
            }
            verifyInterruptedJournalRecovery()

            val first = "示例词库萤火\tabc\t4\n"
            val second = "示例词库青舟\tdef\t6\n"
            suspend fun captureAndVerifyPortableSnapshot(): ByteArray {
                maintained {
                    check(UserDictManager.importUserDict(ByteArrayInputStream(first.toByteArray()), dictionaryName, "$owned-first.txt").getOrThrow() == 1)
                    val location = RimeConfig.openUserConfig("installation").use { installation ->
                        val identity = checkNotNull(installation.getString("installation_id"))
                        check(identity.isNotEmpty() && identity != "unknown" && '/' !in identity && '\\' !in identity)
                        val configured = installation.getString("sync_dir")
                        val syncRoot = if (configured.isNullOrEmpty()) File(root, "sync") else File(configured)
                        check(syncRoot.isAbsolute)
                        File(File(syncRoot.canonicalFile, identity), "$dictionaryName.userdb.txt")
                    }
                    check(!location.exists())
                    snapshotFile = location
                    UserDictManager.backupForRecovery(dictionaryName).getOrThrow()
                    check(location.isFile)
                    check(DictionaryTextFormats.snapshotDictionaryName(location) == dictionaryName)
                }
                checks++
                val snapshot = checkNotNull(snapshotFile).readBytes()
                check(!snapshot.toString(Charsets.UTF_8).contains("#@/tick\t")) { "Fresh imported fixture unexpectedly has tick metadata" }
                repeat(3) {
                    maintained {
                        // Each successful JNI call must exhaust and release both
                        // LevelDB iterators before closing and reopening the DB.
                        UserDictManager.backupForRecovery(dictionaryName).getOrThrow()
                        val location = checkNotNull(snapshotFile)
                        check(location.readBytes().contentEquals(snapshot))
                        check(
                            checkNotNull(checkNotNull(location.parentFile).listFiles()).none { file ->
                                file.name.startsWith("$dictionaryName.userdb.txt.tmp.")
                            },
                        ) { "Successful portable backup left an owned temporary file" }
                    }
                    check(records() == mapOf(("示例词库萤火" to "abc") to 4)) { "Repeated portable backup changed dictionary records" }
                    checks++
                }
                return snapshot
            }
            val snapshot = captureAndVerifyPortableSnapshot()
            suspend fun verifyPortableRestore() {
                maintained {
                    // Only the fresh UUID-owned database is replaced, while all handles
                    // are closed. The portable snapshot must genuinely add its lost row.
                    check(dictionary.isDirectory && dictionary.name == "$dictionaryName.userdb")
                    check(dictionary.deleteRecursively())
                    check(UserDictManager.importUserDict(ByteArrayInputStream(second.toByteArray()), dictionaryName, "$owned-second.txt").getOrThrow() == 1)
                }
                check(records() == mapOf(("示例词库青舟" to "def") to 6))
                checks++
                maintained {
                    UserDictManager.restoreUserDict(ByteArrayInputStream(snapshot), "$owned-portable.userdb.txt").getOrThrow()
                }
                check(records() == mapOf(("示例词库萤火" to "abc") to 4, ("示例词库青舟" to "def") to 6))
                checks++
                // Exercise checked restore a second time with destination tick metadata
                // present, and require idempotent record weights rather than duplicates.
                maintained { UserDictManager.restoreUserDict(ByteArrayInputStream(snapshot), "$owned-portable-again.userdb.txt").getOrThrow() }
                check(records() == mapOf(("示例词库萤火" to "abc") to 4, ("示例词库青舟" to "def") to 6))
                checks++
            }
            verifyPortableRestore()
            suspend fun verifyNativeMutationRollback() {
                maintained {
                    val beforeFailure = inventory(dictionary)
                    val rollbackInput = File(root, "$owned-rollback.txt")
                    check(rollbackInput.createNewFile())
                    try {
                        rollbackInput.writeText("示例回滚流萤\tghi\t3\n")
                        val error = runCatching {
                            DictionaryFileStore(root, backups).mutate(dictionary.name) {
                                check(UserDictManager.importUserDict(dictionaryName, rollbackInput.absolutePath) == 1)
                                throw IOException("Owned failure after a successful native dictionary write")
                            }
                        }.exceptionOrNull()
                        check(error is IOException)
                        check(inventory(dictionary) == beforeFailure) { "Failed native mutation did not restore exact dictionary files" }
                        check(!File(backups, "pending").exists())
                    } finally {
                        check(rollbackInput.delete())
                    }
                }
                check(records() == mapOf(("示例词库萤火" to "abc") to 4, ("示例词库青舟" to "def") to 6)) {
                    "Failed native mutation retained the added rollback record"
                }
                checks++
            }
            verifyNativeMutationRollback()
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            restoreOwnedState()
        }
        check(!phraseMoved && !backupsMoved && !heldPhrase.exists() && !heldBackups.exists())
        checks++
        return checks
    }
}
