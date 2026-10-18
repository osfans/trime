// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import com.osfans.trime.core.CandidateProto;
import com.osfans.trime.core.Rime;
import com.osfans.trime.core.RimeLifecycle;
import com.osfans.trime.core.SchemaItem;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual old/new APK native learning, independent of the Daemon lifecycle wrapper.
 *
 * Java avoids depending on Kotlin/coroutine helpers removed by old Release R8.
 * All native work runs on one owned HandlerThread after verifying no Daemon
 * session exists and the production lifecycle is STOPPED. Native finalize is
 * completed before freezing or deleting any owned LevelDB bytes.
 */
public final class UserDictionaryNativeUpgradeProbe {
    private static final String[] TEXTS = {"茶雲翎", "墨羽燈"};
    private static final String[] SPELLINGS = {"cha'yun'ling", "mo'yu'deng"};
    private final Instrumentation instrumentation;
    private final Context context;
    private final String phase;
    private final File external;
    private final File stateFile;
    private final File receiptFile;
    private final JSONObject receipt = new JSONObject();
    private JSONObject state;
    private File user;
    private String prefix;
    private boolean nativeStarted;
    private boolean nativeStopped;
    private boolean stateValidated;
    private boolean fixtureRetained;
    private int checks;
    private Throwable failure;

    private UserDictionaryNativeUpgradeProbe(Instrumentation instrumentation, String phase) {
        this.instrumentation = instrumentation;
        this.context = instrumentation.getTargetContext();
        this.phase = phase;
        this.external = context.getExternalFilesDir(null);
        this.stateFile = new File(external, "t10-upgrade-state.json");
        this.receiptFile = new File(external, "t10-upgrade-" + phase + "-receipt.json");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new IllegalStateException(description);
    }

    private static Object production(String owner, String name, Class<?>[] parameters, Object... arguments)
            throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.osfans.trime." + owner);
        } catch (ClassNotFoundException missing) {
            // The same instrumentation verifies the published APK before the package move.
            if (!owner.equals("core.data.DataManager")) throw missing;
            type = Class.forName("com.osfans.trime.data.base.DataManager");
        }
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        Object receiver = Modifier.isStatic(method.getModifiers()) ? null : type.getDeclaredField("INSTANCE").get(null);
        return method.invoke(receiver, arguments);
    }

    private static Object production(String owner, String name) throws Exception {
        return production(owner, name, new Class<?>[0]);
    }

    private static void idleDaemon() throws Exception {
        check(production("daemon.RimeDaemon", "getFirstSessionOrNull") == null, "Upgrade native probe requires an idle Daemon");
        Rime real = (Rime) production("daemon.RimeDaemon", "getRealRime");
        check(real.getLifecycle().getCurrentState() == RimeLifecycle.State.STOPPED, "Daemon engine must be fully STOPPED");
    }

    private static byte[] bytes(File file) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (FileInputStream source = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String text(File file) throws Exception {
        return new String(bytes(file), StandardCharsets.UTF_8);
    }

    private static void write(File file, byte[] data) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
            output.getFD().sync();
        }
    }

    private static void write(File file, String contents) throws Exception {
        write(file, contents.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream source = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = source.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        char[] alphabet = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(alphabet[(value & 255) >>> 4]);
            result.append(alphabet[value & 15]);
        }
        return result.toString();
    }

    private void mark(String description) {
        checks++;
        Bundle status = new Bundle();
        status.putString("stream", "PASS: T10 native APK upgrade " + description + "\n");
        instrumentation.sendStatus(0, status);
    }

    private void walk(File file, String relative, boolean owned, Map<String, String> output) throws Exception {
        check(file.getCanonicalFile().equals(file.getAbsoluteFile()), "Owned tree contains a symbolic link");
        boolean inside = owned || file.getName().startsWith(prefix);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            check(children != null, "Cannot enumerate owned fixture tree");
            for (File child : children) walk(child, relative.isEmpty() ? child.getName() : relative + "/" + child.getName(), inside, output);
        } else if (inside && file.isFile()) {
            output.put(relative, hash(file));
        }
    }

    private Map<String, String> ownedTree() throws Exception {
        Map<String, String> output = new TreeMap<>();
        walk(user, "", false, output);
        return output;
    }

    private static Map<String, String> stringMap(JSONObject value) throws Exception {
        Map<String, String> output = new TreeMap<>();
        Iterator<String> keys = value.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            output.put(key, value.getString(key));
        }
        return output;
    }

    private static Map<String, Integer> integerMap(JSONObject value) throws Exception {
        Map<String, Integer> output = new TreeMap<>();
        Iterator<String> keys = value.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            output.put(key, value.getInt(key));
        }
        return output;
    }

    private void writeFixture() throws Exception {
        File dictionary = new File(user, prefix + ".dict.yaml");
        check(dictionary.createNewFile(), "Owned dictionary already exists");
        write(dictionary, "---\nname: " + prefix + "\nversion: \"1\"\nsort: by_weight\nuse_preset_vocabulary: false\n...\n" +
                "茶\tcha\t100\n雲\tyun\t100\n翎\tling\t100\n墨\tmo\t100\n羽\tyu\t100\n燈\tdeng\t100\n");
        for (String suffix : new String[]{"full", "t9"}) {
            String schema = prefix + "_" + suffix;
            String algebra = suffix.equals("t9") ? "  algebra:\n    - derive/^(.*)$/\\U$1/\n    - xlit/ABCDEFGHIJKLMNOPQRSTUVWXYZ/22233344455566677778889999/\n" : "";
            File file = new File(user, schema + ".schema.yaml");
            check(file.createNewFile(), "Owned schema already exists");
            write(file, "schema:\n  schema_id: " + schema + "\n  name: T10 upgrade " + suffix + "\n  version: \"1\"\n" +
                    "switches:\n  - name: ascii_mode\n    reset: 0\n    states: [native, ascii]\n" +
                    "ascii_composer:\n  switch_key:\n    Shift_L: inline_ascii\n    Shift_R: commit_text\n" +
                    "engine:\n  processors: [ascii_composer, speller, selector, navigator, express_editor]\n" +
                    "  segmentors: [ascii_segmentor, abc_segmentor, fallback_segmentor]\n  translators: [script_translator]\n" +
                    "speller:\n  alphabet: abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ987654321\n  delimiter: \" '\"\n" + algebra +
                    "translator:\n  dictionary: " + prefix + "\n  prism: " + schema + "\n" +
                    "  enable_user_dict: true\n  enable_sentence: true\n  enable_completion: false\nmenu:\n  page_size: 5\n");
            check(Rime.deployRimeSchemaFile(file.getAbsolutePath()), "Actual APK engine could not deploy owned schema");
        }
    }

    private static String[] enabledSchemas() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (SchemaItem item : Rime.getRimeSchemaList()) {
            check(item.getId() != null && !item.getId().isEmpty() && ids.add(item.getId()),
                    "Native enabled schema list is empty or duplicated");
        }
        check(!ids.isEmpty(), "Native enabled schema list is absent");
        return ids.toArray(new String[0]);
    }

    private static String[] strings(JSONArray values) throws Exception {
        String[] result = new String[values.length()];
        for (int index = 0; index < result.length; index++) result[index] = values.getString(index);
        return result;
    }

    private File defaultCustom() throws Exception {
        File file = new File(user, "default.custom.yaml");
        check(file.getCanonicalFile().equals(file.getAbsoluteFile()) && (!file.exists() || file.isFile()),
                "Default customization is not a regular owned-tree file");
        check(!file.exists() || file.length() <= 1024 * 1024, "Default customization exceeds fixture bound");
        return file;
    }

    private void startNative(File shared, String version) {
        nativeStopped = false;
        nativeStarted = true;
        Rime.startupRime(shared.getAbsolutePath(), user.getAbsolutePath(), version, false);
        Rime.setRimePrivacyMode(false);
        check(!Rime.isRimePrivacyGuardEnabledForTesting(), "Native normal-learning privacy guard unexpectedly active");
    }

    private static void awaitNewConfigTimestamp() throws Exception {
        // Rime's modification/build timestamps have one-second resolution. The
        // fixture must actually change in a later second, as an ordinary saved
        // configuration would; never force deployment in the upgrade verifier.
        long previousSecond = System.currentTimeMillis() / 1000L;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.currentTimeMillis() / 1000L <= previousSecond) {
            check(System.nanoTime() < deadline, "Clock did not advance for normal configuration maintenance");
            Thread.sleep(20L);
        }
    }

    private void registerFixture(File shared, String version) throws Exception {
        String[] original = strings(state.getJSONArray("original_enabled_schema_ids"));
        String[] expected = Arrays.copyOf(original, original.length + 2);
        expected[original.length] = prefix + "_full";
        expected[original.length + 1] = prefix + "_t9";
        awaitNewConfigTimestamp();
        check(Rime.selectRimeSchemas(expected), "Production schema selection could not register owned full/T9");
        File custom = defaultCustom();
        check(custom.isFile(), "Production schema selection did not persist default customization");
        String registered = Base64.encodeToString(bytes(custom), Base64.NO_WRAP);
        finalizeNative();
        startNative(shared, version);
        String[] actual = enabledSchemas();
        check(Arrays.equals(actual, expected), "Normal old-APK maintenance did not enable both owned schemas");
        check(registered.equals(Base64.encodeToString(bytes(custom), Base64.NO_WRAP)),
                "Normal registration maintenance changed default customization bytes");
        state.put("registered_enabled_schema_ids", new JSONArray(Arrays.asList(actual)))
                .put("registered_default_custom", registered)
                .put("enabled_schema_registration", new JSONObject()
                        .put("expected_ids", new JSONArray(Arrays.asList(expected)))
                        .put("actual_ids", new JSONArray(Arrays.asList(actual)))
                        .put("default_custom_sha256", hash(custom))
                        .put("normal_startup_full_check", false)
                        .put("ordinary_maintenance_readback", true));
    }

    private void restoreFixtureSelection(File shared, String version) throws Exception {
        File custom = defaultCustom();
        check(custom.isFile() && state.getString("registered_default_custom").equals(
                        Base64.encodeToString(bytes(custom), Base64.NO_WRAP)),
                "Only the registered fixture customization may be restored");
        check(Arrays.equals(enabledSchemas(), strings(state.getJSONArray("registered_enabled_schema_ids"))),
                "Upgraded enabled schema registration changed");
        String[] original = strings(state.getJSONArray("original_enabled_schema_ids"));
        awaitNewConfigTimestamp();
        check(Rime.selectRimeSchemas(original), "Production schema selection could not restore the original list");
        if (state.getBoolean("original_default_custom_existed")) {
            write(custom, Base64.decode(state.getString("original_default_custom"), Base64.NO_WRAP));
        } else {
            check(!custom.exists() || custom.delete(), "Original absent default customization could not be restored");
        }
        finalizeNative();
        startNative(shared, version);
        check(Arrays.equals(enabledSchemas(), original), "Normal cleanup did not restore original enabled schemas");
        check(state.getBoolean("original_default_custom_existed") == custom.exists() &&
                        (!custom.exists() || state.getString("original_default_custom").equals(
                                Base64.encodeToString(bytes(custom), Base64.NO_WRAP))),
                "Original default customization bytes/existence were not restored");
        receipt.put("schema_registration_restored", true)
                .put("restored_enabled_schema_ids", new JSONArray(Arrays.asList(original)));
    }

    private void input(String schema, String spelling) {
        check(Rime.selectRimeSchema(schema), "Cannot select owned schema");
        Rime.setRimeOption("ascii_mode", false);
        Rime.clearRimeComposition();
        check(Rime.getRimeCommit().getText() == null, "Unexpected pending commit");
        for (int index = 0; index < spelling.length(); index++) check(Rime.processRimeKey(spelling.charAt(index), 0), "Native engine rejected fixture input");
    }

    private void learn() {
        for (int phrase = 0; phrase < TEXTS.length; phrase++) {
            for (int repetition = 0; repetition < 3; repetition++) {
                input(prefix + "_full", SPELLINGS[phrase]);
                CandidateProto[] candidates = Rime.getRimeCandidates(0, 80);
                int found = -1;
                for (int index = 0; index < candidates.length; index++) if (candidates[index].getText().equals(TEXTS[phrase])) found = index;
                check(found >= 0 && Rime.selectRimeCandidate(found, true), "Fictional phrase candidate missing or selection failed");
                check(TEXTS[phrase].equals(Rime.getRimeCommit().getText()), "Native committed text differs from selected fictional phrase");
                check(Rime.getRimeCommit().getText() == null && Rime.getRimeRawInput().isEmpty(), "Commit duplicated or composition remained");
            }
        }
    }

    private Map<String, Integer> export() throws Exception {
        // A different schema releases this owned user dictionary. The original
        // daemon stays STOPPED; this is still the one owned JNI worker context.
        Rime.clearRimeComposition();
        check(Rime.selectRimeSchema(state.getString("original_schema")), "Could not release owned DB before native export");
        File file = new File(context.getCacheDir(), prefix + "-" + phase + "-export.txt");
        check(!file.exists(), "Owned export output already exists");
        try {
            int count = (Integer) production("data.userdict.UserDictManager", "exportUserDict",
                    new Class<?>[]{String.class, String.class}, prefix, file.getAbsolutePath());
            check(count >= 0, "Actual APK native user dictionary export failed");
            Map<String, Integer> output = new TreeMap<>();
            try (BufferedReader input = new BufferedReader(new StringReader(text(file)))) {
                String line;
                while ((line = input.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] fields = line.split("\t", -1);
                    check(fields.length == 3, "Malformed native logical export");
                    String key = fields[0] + "\t" + fields[1].trim();
                    check(output.put(key, Integer.parseInt(fields[2])) == null, "Duplicate logical learning entry");
                }
            }
            return output;
        } finally {
            check(!file.exists() || file.delete(), "Owned temporary export cleanup failed");
        }
    }

    private void verifyLearned() {
        for (boolean nineKey : new boolean[]{false, true}) {
            for (int phrase = 0; phrase < TEXTS.length; phrase++) {
                String spelling = SPELLINGS[phrase];
                if (nineKey) {
                    StringBuilder digits = new StringBuilder();
                    for (int index = 0; index < spelling.length(); index++) {
                        char value = spelling.charAt(index);
                        if (value != '\'') digits.append("22233344455566677778889999".charAt(value - 'a'));
                    }
                    spelling = digits.toString();
                }
                input(prefix + (nineKey ? "_t9" : "_full"), spelling);
                CandidateProto[] candidates = Rime.getRimeCandidates(0, 80);
                check(candidates.length > 0 && candidates[0].getText().equals(TEXTS[phrase]), "Upgraded full/T9 first candidate lost learned phrase");
                Rime.clearRimeComposition();
                check(Rime.getRimeCommit().getText() == null, "Read-only upgraded lookup unexpectedly committed");
            }
        }
    }

    private File portableBackupFile() throws Exception {
        // Only the new Release verify branch calls these kept JNI methods.
        // Avoid Kotlin configuration wrappers that an ordinary Release can inline.
        long peer = (Long) production("core.RimeConfig", "openRimeUserConfig",
                new Class<?>[]{String.class}, "installation");
        check(peer != 0, "Cannot open actual native installation configuration");
        String identity;
        String configured;
        try {
            identity = (String) production("core.RimeConfig", "getRimeConfigString",
                    new Class<?>[]{long.class, String.class}, peer, "installation_id");
            configured = (String) production("core.RimeConfig", "getRimeConfigString",
                    new Class<?>[]{long.class, String.class}, peer, "sync_dir");
        } finally {
            production("core.RimeConfig", "closeRimeConfig", new Class<?>[]{long.class}, peer);
        }
        check(identity != null && identity.matches("[A-Za-z0-9_-]+") && !identity.equals("unknown"),
                "Actual native installation identity is unsafe or missing");
        File canonicalUser = user.getCanonicalFile();
        File requested = configured == null || configured.isEmpty() ? new File(canonicalUser, "sync") : new File(configured);
        check(requested.isAbsolute(), "Actual native sync directory is not absolute");
        // Android shared-storage ancestors can be aliases. Resolve that trusted
        // parent, while rejecting a link in the sync leaf or owned snapshot path.
        File parent = requested.getAbsoluteFile().getParentFile();
        check(parent != null, "Actual native sync directory has no parent");
        File root = new File(parent.getCanonicalFile(), requested.getName());
        check(root.getCanonicalFile().equals(root.getAbsoluteFile()) &&
                        root.getCanonicalPath().startsWith(canonicalUser.getPath() + File.separator),
                "Portable backup must remain in the dedicated user tree");
        File file = new File(new File(root, identity), prefix + ".userdb.txt");
        check(file.getCanonicalFile().equals(file.getAbsoluteFile()), "Owned portable backup path contains a symbolic link");
        return file;
    }

    private Map<String, Integer> snapshotEntries(File file) throws Exception {
        check(file.isFile() && file.length() > 0 && file.length() <= 1024 * 1024,
                "Owned native portable backup is absent, empty or too large");
        Map<String, String> metadata = new TreeMap<>();
        Map<String, Integer> entries = new TreeMap<>();
        try (BufferedReader input = new BufferedReader(new StringReader(text(file)))) {
            check("# Rime user dictionary".equals(input.readLine()), "Wrong native portable backup header");
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("#@")) {
                    String[] fields = line.substring(2).split("\t", -1);
                    check(fields.length == 2 && metadata.put(fields[0], fields[1]) == null,
                            "Malformed or duplicate portable backup metadata");
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    String[] fields = line.split("\t", -1);
                    check(fields.length == 3 && !fields[0].isEmpty() && !fields[1].isEmpty(),
                            "Malformed native portable backup entry");
                    Integer commits = null;
                    for (String value : fields[2].split(" ", -1)) {
                        if (value.startsWith("c=")) {
                            check(commits == null, "Duplicate portable backup commit counter");
                            commits = Integer.parseInt(value.substring(2));
                        }
                    }
                    check(commits != null && commits > 0 &&
                                    entries.put(fields[1] + "\t" + fields[0].trim(), commits) == null,
                            "Missing, non-positive or duplicate portable learning entry");
                }
            }
        }
        check(prefix.equals(metadata.get("/db_name")) && "userdb".equals(metadata.get("/db_type")) &&
                        file.getParentFile().getName().equals(metadata.get("/user_id")),
                "Portable backup metadata does not identify the owned dictionary and real installation");
        return entries;
    }

    private void verifyPortableBackups(Map<String, Integer> expected) throws Exception {
        // export() selected the original schema, releasing the owned engine DB.
        // The Daemon remains STOPPED and every backup uses the same serial worker.
        File file = portableBackupFile();
        check(!file.exists(), "Owned native portable backup already exists");
        byte[] original = null;
        JSONArray iterations = new JSONArray();
        for (int index = 0; index < 3; index++) {
            boolean backedUp = (Boolean) production("data.userdict.UserDictManager", "backupUserDict",
                    new Class<?>[]{String.class}, prefix);
            check(backedUp,
                    "New normal Release native portable backup failed");
            Map<String, Integer> entries = snapshotEntries(file);
            check(entries.equals(expected), "Native portable backup lost or changed learned records");
            byte[] current = bytes(file);
            if (original == null) original = current;
            else check(Arrays.equals(original, current), "Repeated native portable backup changed snapshot bytes");
            File[] siblings = file.getParentFile().listFiles();
            check(siblings != null, "Cannot inspect owned portable backup directory");
            for (File sibling : siblings) check(!sibling.getName().startsWith(prefix + ".userdb.txt.tmp."),
                    "Successful native portable backup left an owned temporary file");
            Map<String, Integer> exported = export();
            check(exported.equals(expected), "Repeated native portable backup changed exact database learning counters");
            iterations.put(new JSONObject().put("call", index + 1).put("sha256", hash(file))
                    .put("bytes", current.length).put("logical_entries", new JSONObject(entries))
                    .put("exported_entries", new JSONObject(exported)).put("backup_returned", backedUp)
                    .put("owned_temporary_files", 0));
        }
        verifyLearned();
        check(export().equals(expected), "Post-backup full/T9 lookups changed old learning counters");
        receipt.put("release_backup", new JSONObject().put("calls", 3).put("path", file.getAbsolutePath())
                .put("sha256", hash(file)).put("bytes", file.length())
                .put("snapshot_utf8_base64", Base64.encodeToString(original, Base64.NO_WRAP))
                .put("logical_entries", new JSONObject(expected)).put("iterations", iterations)
                .put("user_data_dir", user.getAbsolutePath()).put("canonical_user_data_dir", user.getCanonicalPath())
                .put("sync_directory", file.getParentFile().getParentFile().getCanonicalPath())
                .put("installation_id", file.getParentFile().getName())
                .put("full_t9_lookup_preserved", true).put("native_context", "independent_serial_JNI_worker_not_Daemon_lifecycle"));
        mark("new normal Release repeats actual native portable backup three times with identical bytes, records and full/T9 lookups");
    }

    private void removeOwned(File file, boolean inside) throws Exception {
        check(file.getCanonicalFile().equals(file.getAbsoluteFile()), "Owned cleanup encountered a symbolic link");
        boolean owned = inside || file.getName().startsWith(prefix);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            check(children != null, "Owned cleanup enumeration failed");
            for (File child : children) removeOwned(child, owned);
        }
        if (owned) check(file.delete(), "Owned upgrade fixture cleanup failed");
    }

    private void finalizeNative() throws Exception {
        if (nativeStarted) {
            Rime.exitRime();
            nativeStarted = false;
        }
        idleDaemon();
        nativeStopped = true;
    }

    @SuppressWarnings("deprecation")
    private void perform() {
        try {
            check("prepare".equals(phase) || "verify".equals(phase), "Unknown upgrade phase");
            check("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE), "Dedicated emulator required");
            check(external != null && new File(external, "runtime-audit-dedicated").isFile(), "Dedicated marker missing");
            check(!receiptFile.exists(), "Never overwrite an earlier upgrade receipt");
            idleDaemon();
            user = ((File) production("core.data.DataManager", "getUserDataDir")).getCanonicalFile();
            File config = new File(user, "user.yaml");
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if ("prepare".equals(phase)) {
                check(!stateFile.exists(), "Old owned upgrade fixture already exists");
                prefix = "__t10_upgrade_" + UUID.randomUUID().toString().replace("-", "");
                state = new JSONObject();
                state.put("format_version", 3).put("native_context", "independent_serial_JNI_worker_not_Daemon_lifecycle")
                        .put("prefix", prefix).put("package", context.getPackageName()).put("uid", context.getApplicationInfo().uid)
                        .put("application_data_dir", context.getApplicationInfo().dataDir).put("user_data_dir", user.getAbsolutePath())
                        .put("old_version_code", info.versionCode).put("old_version_name", info.versionName)
                        .put("original_user_yaml", config.exists() ? Base64.encodeToString(bytes(config), Base64.NO_WRAP) : JSONObject.NULL);
            } else {
                check(stateFile.isFile(), "Published old APK did not leave its owned learning state");
                state = new JSONObject(text(stateFile));
                check(state.getInt("format_version") == 3, "Wrong actual native upgrade state format");
                prefix = state.getString("prefix");
                check(prefix.matches("__t10_upgrade_[a-f0-9]{32}"), "Untrusted owned fixture prefix");
                check(context.getPackageName().equals(state.getString("package")) && context.getApplicationInfo().uid == state.getInt("uid"), "Normal APK upgrade changed package/UID");
                check(context.getApplicationInfo().dataDir.equals(state.getString("application_data_dir")) && user.getAbsolutePath().equals(state.getString("user_data_dir")), "Normal APK upgrade changed retained data paths");
                check(info.versionCode >= state.getInt("old_version_code") && (info.versionCode != state.getInt("old_version_code") || !info.versionName.equals(state.getString("old_version_name"))), "Not an actual new APK upgrade");
                check(ownedTree().equals(stringMap(state.getJSONObject("owned_tree"))), "APK install changed stopped owned dictionary bytes before native startup");
                mark("normal install preserves real UID, paths, schema and learned LevelDB bytes before native startup");
            }
            stateValidated = true;
            // Invoke the real resource deployment used by production, not a
            // copied/stub dictionary. Its exact old Release method is audited.
            production("core.data.DataManager", "sync");
            File shared = (File) production("core.data.DataManager", "getSharedDataDir");
            check(shared.isDirectory() && user.isDirectory(), "Production resource deployment failed");
            System.loadLibrary("rime_jni");
            idleDaemon();
            startNative(shared, info.versionName);
            if ("prepare".equals(phase)) {
                state.put("original_schema", Rime.getCurrentRimeSchema());
                state.put("original_ascii", Rime.getRimeOption("ascii_mode"));
                File custom = defaultCustom();
                String[] original = enabledSchemas();
                check(Arrays.asList(original).contains(state.getString("original_schema")),
                        "Original current schema is not actually enabled");
                state.put("original_default_custom_existed", custom.exists())
                        .put("original_default_custom", custom.exists()
                                ? Base64.encodeToString(bytes(custom), Base64.NO_WRAP) : JSONObject.NULL)
                        .put("original_enabled_schema_ids", new JSONArray(Arrays.asList(original)));
                writeFixture();
                registerFixture(shared, info.versionName);
                learn();
                Map<String, Integer> entries = export();
                for (int phrase = 0; phrase < TEXTS.length; phrase++) {
                    Integer count = entries.get(TEXTS[phrase] + "\t" + SPELLINGS[phrase].replace('\'', ' '));
                    check(count != null && count >= 3, "Old native engine did not persist three positive learning commits");
                }
                state.put("logical_entries", new JSONObject(entries));
                mark("published old APK learns two fictional phrases through six JNI commits and exports positive learning counters");
            } else {
                verifyLearned();
                Map<String, Integer> entries = integerMap(state.getJSONObject("logical_entries"));
                check(export().equals(entries), "New APK read-only queries changed or lost old learning counters");
                mark("new normal Release reads old learned phrases first in both full/T9 and exports unchanged positive counters");
                verifyPortableBackups(entries);
                restoreFixtureSelection(shared, info.versionName);
            }
            Rime.clearRimeComposition();
            check(Rime.selectRimeSchema(state.getString("original_schema")), "Could not restore native original schema");
            Rime.setRimeOption("ascii_mode", state.getBoolean("original_ascii"));
            finalizeNative();
            if ("prepare".equals(phase)) {
                Map<String, String> tree = ownedTree();
                check(!tree.isEmpty(), "Stopped native fixture tree is empty");
                state.put("owned_tree", new JSONObject(tree));
                check(stateFile.createNewFile(), "Owned state must not replace previous evidence");
                write(stateFile, state.toString(2));
                fixtureRetained = true;
                mark("actual native finalize completed; owned fixture frozen and retained only for normal APK upgrade");
            }
            receipt.put("old_version_code", state.getInt("old_version_code")).put("old_version_name", state.getString("old_version_name"))
                    .put("tested_version_code", info.versionCode).put("tested_version_name", info.versionName)
                    .put("package", context.getPackageName()).put("uid", context.getApplicationInfo().uid)
                    .put("prefix", prefix).put("state_sha256", hash(stateFile))
                    .put("enabled_schema_registration", state.getJSONObject("enabled_schema_registration"));
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                finalizeNative();
            } catch (Throwable error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
            if (nativeStopped && stateValidated && failure == null && !fixtureRetained) {
                try {
                    removeOwned(user, false);
                    File config = new File(user, "user.yaml");
                    if (state.isNull("original_user_yaml")) check(!config.exists() || config.delete(), "Original absent user.yaml could not be restored");
                    else write(config, Base64.decode(state.getString("original_user_yaml"), Base64.NO_WRAP));
                    if ("verify".equals(phase) && failure == null) check(stateFile.delete(), "Verified owned upgrade state cleanup failed");
                } catch (Throwable error) {
                    if (failure == null) failure = error; else failure.addSuppressed(error);
                }
            }
            if (failure != null && stateValidated) {
                // Preserve failed fixtures for inspection after native finalize.
                // In particular, never destroy the evidence of a failed lookup.
                fixtureRetained = true;
                try {
                    if (!stateFile.exists()) {
                        state.put("diagnostic_only", true);
                        if (nativeStopped) state.put("owned_tree", new JSONObject(ownedTree()));
                        check(stateFile.createNewFile(), "Failed state must not replace earlier evidence");
                        write(stateFile, state.toString(2));
                    }
                } catch (Throwable error) {
                    failure.addSuppressed(error);
                }
            }
            try {
                receipt.put("passed", failure == null).put("phase", phase).put("checks", checks).put("native_stopped", nativeStopped)
                        .put("fixture_retained_for_upgrade", fixtureRetained).put("native_context", "independent_serial_JNI_worker_not_Daemon_lifecycle")
                        .put("failure", failure == null ? JSONObject.NULL : android.util.Log.getStackTraceString(failure));
                check(receiptFile.createNewFile(), "Never overwrite an earlier native receipt");
                write(receiptFile, receipt.toString(2));
            } catch (Throwable error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
    }

    public static void run(Instrumentation instrumentation, String phase) {
        UserDictionaryNativeUpgradeProbe probe = new UserDictionaryNativeUpgradeProbe(instrumentation, phase);
        HandlerThread worker = new HandlerThread("t10-upgrade-native-serial");
        CountDownLatch complete = new CountDownLatch(1);
        worker.start();
        boolean posted = new Handler(worker.getLooper()).post(() -> {
            try { probe.perform(); } finally { complete.countDown(); }
        });
        Bundle result = new Bundle();
        try {
            check(posted && complete.await(840, TimeUnit.SECONDS), "Owned native worker failed to finish before timeout");
            worker.quitSafely();
            worker.join(30000);
            check(!worker.isAlive(), "Owned serial native HandlerThread did not exit");
            result.putBoolean("passed", probe.failure == null && probe.nativeStopped);
            result.putInt("checks", probe.checks);
            result.putString("stream", probe.failure == null ? "PASS: T10 actual native APK upgrade " + phase + "; " + probe.checks + " checks; native finalized; independent serial JNI context, no Daemon lifecycle claim\n" : "FAIL: " + android.util.Log.getStackTraceString(probe.failure) + "\n");
        } catch (Throwable error) {
            result.putBoolean("passed", false);
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error) + "\n");
        } finally {
            worker.quitSafely();
        }
        instrumentation.finish(result.getBoolean("passed") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
