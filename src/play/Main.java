package play;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.BufferedInputStream;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@SuppressWarnings({
    "LocalVariableHidesMemberVariable",
    "BroadCatchBlock",
    "TooBroadCatch",
    "UseSpecificCatch",
    "SleepWhileInLoop",
    "override",
    "CallToThreadDumpStack",
    "OverridableMethodCallInConstructor"})
public class Main implements Runnable {

    String file;
    String aapt;
    String adb;
    String androidJar;

    Main(String file) {
        this.file = file;
        try {
            File tmpdir = new File(System.getProperty("java.io.tmpdir"), "playbin");
            aapt = tmpdir + "\\aapt.exe";
            adb = tmpdir + "\\adb.exe";
            androidJar = tmpdir + "\\android.jar";
            if (!tmpdir.exists()) {
                begin("Initializing");
                tmpdir.mkdir();
                String[] names = new String[]{
                    "aapt.exe",
                    "adb.exe",
                    "AdbWinApi.dll",
                    "AdbWinUsbApi.dll",
                    "android.jar"};
                for (String name : names) {
                    copyFile(getClass().getResourceAsStream(name), tmpdir + "\\" + name);
                }
                end();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    String aapt() {
        return aapt;
    }

    String adb() {
        return adb;
    }

    String androidJar() {
        return androidJar;
    }

    String file() {
        return file;
    }

    String code;
    String pkg;
    String name;

    void clearCache() {
        code = null;
        pkg = null;
        name = null;
    }

    String code() {
        if (code == null) {
            code = readFile(file());
        }
        return code;
    }

    Pattern pkgPattern = Pattern.compile("package\\s+([^;\\s]+)");

    String pkg() {
        if (this.pkg == null) {
            Matcher matcher = pkgPattern.matcher(code());
            if (!matcher.find()) {
                throw new RuntimeException("Missing package declaration");
            }
            String pkg = matcher.group(1);
            if (!pkg.contains(".")) {
                throw new RuntimeException("Invalid package declaration");
            }
            this.pkg = pkg;
        }
        return this.pkg;
    }

    Pattern namePattern = Pattern.compile("class\\s+([^{\\s]+)");

    String name() {
        if (this.name == null) {
            Matcher matcher = namePattern.matcher(code());
            if (!matcher.find()) {
                throw new RuntimeException("Missing public class declaration");
            }
            this.name = matcher.group(1);
        }
        return this.name;
    }

    String activityName() {
        return pkg() + "/." + name();
    }

    String packageDir() {
        return pkg().replace('.', '\\');
    }

    String buildDir() {
        return name() + "-build";
    }

    String javaDir() {
        return buildDir() + "\\java\\" + packageDir();
    }

    String javaFile() {
        return javaDir() + "\\" + name() + ".java";
    }

    String classesDir() {
        return buildDir() + "\\classes";
    }

    String androidManifestXml() {
        return buildDir() + "\\AndroidManifest.xml";
    }

    String classesDex() {
        return buildDir() + "\\classes.dex";
    }

    String manifestApk() {
        return buildDir() + "\\" + name() + "-manifest.apk";
    }

    String unsignedApk() {
        return buildDir() + "\\" + name() + "-unsigned.apk";
    }

    String finalApk() {
        return buildDir() + "\\" + name() + ".apk";
    }

    String androidManifest() {
        return "<?xml version=\"1.0\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"" + pkg() + "\">\n"
                + "  <support-screens android:anyDensity=\"true\"/>\n"
                + "  <application android:label=\"" + name() + "\" android:theme=\"@android:style/Theme.Light.NoTitleBar.Fullscreen\">\n"
                + "    <activity android:name=\"" + name() + "\" android:label=\"" + name() + "\">\n"
                + "      <intent-filter>\n"
                + "        <action android:name=\"android.intent.action.MAIN\"/>\n"
                + "        <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
                + "      </intent-filter>\n"
                + "    </activity>\n"
                + "  </application>\n"
                + "</manifest>";
    }

    void initJavaDir() {
        mkdirs(javaDir());
    }

    void initClassesDir() {
        mkdirs(classesDir());
    }

    void copyFileToJavaFile() {
        copyFile(file(), javaFile());
    }

    void buildApp() {
        clearCache();
        compileClasses();
        compileDex();
        compileApk();
        signApk();
    }

    void compileClasses() {
        begin("Compiling classes");
        initJavaDir();
        copyFileToJavaFile();
        initClassesDir();
        ecj(
                "-bootclasspath", androidJar(),
                "-d", classesDir(),
                "-source", "1.7",
                "-target", "1.7",
                "-proc:none",
                "-nowarnd",
                javaFile());
        end();
    }

    void compileDex() {
        begin("Compiling DEX");
        dexer("--output", classesDex(), classesDir());
        end();
    }

    void compileApk() {
        begin("Compiling APK");
        compileManifest();
        addClassesDex();
        end();
    }

    void signApk() {
        begin("Signing APK");
        try {
            kellinwood.security.zipsigner.ZipSigner signer = new kellinwood.security.zipsigner.ZipSigner();
            signer.setKeymode(kellinwood.security.zipsigner.ZipSigner.KEY_TESTKEY);
            signer.signZip(unsignedApk(), finalApk());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        end();
    }

    String prevPkg;
    String prevName;

    void compileManifest() {
        if (!Objects.equals(prevPkg, pkg()) || Objects.equals(prevName, name())) {
            prevPkg = pkg();
            prevName = name();
            writeFile(androidManifestXml(), androidManifest());
            exec(aapt(), "p",
                    "-I", androidJar(),
                    "-M", androidManifestXml(),
                    "-F", manifestApk(),
                    "-f");
        }
    }

    void addClassesDex() {
        copyFile(manifestApk(), unsignedApk());
        exec(aapt(), "a", "-k", unsignedApk(), classesDex());
    }

    void runApp() {
        installApk();
        clearLogcat();
        startActivity();
        startLogcat();
    }

    void installApk() {
        begin("Installing APK");
        boolean nextTry = false;
        while (true) {
            try {
                execNone(adb(), "install", "-r", finalApk());
                break;
            } catch (Exception e) {
                execNone(adb(), "kill-server");
                execNone(adb(), "start-server");
                if (nextTry) {
                    try {
                        Thread.sleep(5000);
                    } catch (Exception e2) {
                        break;
                    }
                }
                nextTry = true;
            }
        }
        end();
    }

    void startActivity() {
        begin("Starting Activity");
        exec(adb(), "shell", "am", "start", "-a", "android.intent.action.MAIN", "-n", activityName());
        end();
    }

    Thread thread;

    void startLogcat() {
        if (thread == null) {
            thread = new Thread(this);
            thread.start();
        }
    }

    boolean logcatCleared;

    void clearLogcat() {
        if (!logcatCleared) {
            begin("Clearing LogCat");
            logcatCleared = true;
            exec(adb(), "logcat", "-c");
            end();
        }
    }

    public void run() {
        try {
            Process process = new ProcessBuilder(adb(), "logcat", name() + ":V", "AndroidRuntime:E", "*:S").start();
            Scanner scanner = new Scanner(new BufferedInputStream(process.getInputStream()));
            while (true) {
                System.out.println("[LOGCAT] " + scanner.nextLine());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void ecj(String... args) {
        org.eclipse.jdt.internal.compiler.batch.Main main = new org.eclipse.jdt.internal.compiler.batch.Main(
                new PrintWriter(System.out),
                new PrintWriter(System.err),
                false,
                null,
                null);
        if (!main.compile(args)) {
            throw new RuntimeException("Ecj failed");
        }
    }

    void dexer(String... args) {
        try {
            com.android.dx.command.dexer.Main.Arguments arguments = new com.android.dx.command.dexer.Main.Arguments();
            for (Method method : arguments.getClass().getDeclaredMethods()) {
                if (method.getName().equals("parse")) {
                    method.setAccessible(true);
                    method.invoke(arguments, new Object[]{args});
                }
            }
            int result = com.android.dx.command.dexer.Main.run(arguments);
            if (result != 0) {
                throw new Exception("Dx failed");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void exec(String... commands) {
        try {
            int result = new ProcessBuilder(commands)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                    .waitFor();
            if (result != 0) {
                throw new Exception("Exec failed");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void execNone(String... commands) {
        try {
            int result = new ProcessBuilder(commands)
                    .start()
                    .waitFor();
            if (result != 0) {
                throw new Exception("Exec failed");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void mkdirs(String path) {
        new File(path).mkdirs();
    }

    void copy(InputStream in, OutputStream out) {
        try {
            byte[] buffer = new byte[4096];
            while (true) {
                int read = in.read(buffer);
                if (read == -1) {
                    break;
                }
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void copyFile(String source, String dest) {
        try {
            try (FileInputStream in = new FileInputStream(new File(source))) {
                copyFile(in, dest);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void copyFile(InputStream source, String dest) {
        try {
            try (FileOutputStream out = new FileOutputStream(new File(dest))) {
                copy(source, out);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void writeFile(String file, String contents) {
        try {
            try (FileOutputStream out = new FileOutputStream(new File(file))) {
                out.write(contents.getBytes());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    String readFile(String file) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            FileInputStream in = new FileInputStream(new File(file));
            copy(in, out);
            return new String(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    long start;
    long total;

    void begin(String name) {
        System.out.print("[PLAY] " + name + "... ");
        start = System.currentTimeMillis();
    }

    void end() {
        long duration = System.currentTimeMillis() - start;
        System.out.println(duration + "ms");
        total += duration;
    }

    void done() {
        System.out.println("[PLAY] Done in " + total + "ms");
        total = 0;
    }

    void start() {
        File file = new File(file());
        long lastModified = 0;
        while (true) {
            try {
                long newLastModified = file.lastModified();
                if (lastModified != newLastModified) {
                    lastModified = newLastModified;
                    buildApp();
                    runApp();
                    done();
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new Main(args[0]).start();
    }
}
