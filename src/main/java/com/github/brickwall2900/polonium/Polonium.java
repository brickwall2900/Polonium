package com.github.brickwall2900.polonium;

import com.formdev.flatlaf.FlatLightLaf;
import com.github.brickwall2900.app.ApplicationUtil;
import com.github.brickwall2900.app.ConfigManager;
import com.github.brickwall2900.app.ProgressIndicator;
import com.github.brickwall2900.polonium.trash.PoloniumTrash;
import com.github.brickwall2900.polonium.windows.PoloniumConfigEditor;
import org.httprpc.sierra.UILoader;
import org.tinylog.Logger;
import org.tinylog.provider.ProviderRegistry;
import picocli.CommandLine;

import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static com.github.brickwall2900.app.TranslatableText.text;

// this code is extremely messy i shouldn't publish this on GitHub
public class Polonium implements Runnable {
    public static final String CONFIG_FILE_NAME = ".polonium-210.json";

    public static final String PROP_ALWAYS_MAP_FILES_TO_MEMORY = "polonium.always_map_files_to_memory";
    public static final String PROP_INCLUDE_HIDDEN_FILES = "polonium.include_hidden_files";
    public static final String PROP_ROOT_DIRECTORY = "polonium.root_directory";

    public static Path rootDirectory =  Path.of(System.getProperty("user.home"), ".polonium");

    public static String version;

    private static void preInit() {
        Runtime.getRuntime().addShutdownHook(new Thread(Polonium::onShutdown));
        String rootDirectory = System.getProperty(PROP_ROOT_DIRECTORY);
        if (rootDirectory != null) {
            Polonium.rootDirectory = Path.of(rootDirectory);
        } else {
            System.setProperty(PROP_ROOT_DIRECTORY, String.valueOf(Polonium.rootDirectory));
        }
        ApplicationUtil.createDirectoriesSafe(Polonium.rootDirectory);
        ConfigManager.init(CONFIG_FILE_NAME, Polonium.rootDirectory);
    }

    public static void main(String[] args) {
        preInit();

        try {
            Logger.info("Polonium initializing!");
            Logger.info("Root Directory = {}", Polonium.rootDirectory);
            try (InputStream stream = Polonium.class.getResourceAsStream("/version.properties")) {
                Properties versionProperties = new Properties();
                versionProperties.load(stream);
                version = versionProperties.getProperty("app.version");
            } catch (IOException | NullPointerException e) {
                System.err.println("Version cannot be retrieved!");
                e.printStackTrace();
                version = "Unknown";
            }

            Logger.info("Polonium version {}", version);
            CommandLine cli = new CommandLine(new Polonium());
            cli.setExecutionExceptionHandler((ex, commandLine, fullParseResult) -> {
                showException(ex);
                System.exit(-1);
                throw ex;
            });
            cli.execute(args);
        } catch (Exception e) {
            Logger.error(e);
            showException(e);
        }
    }

    private static final List<Runnable> shutdownHooks = new ArrayList<>();

    private static void onShutdown() {
        Logger.info("Shutting down!");
        try {
            shutdownHooks.forEach(Runnable::run);
        } catch (Exception e) {
            Logger.error(e);
        }

        try {
            ProviderRegistry.getLoggingProvider().shutdown();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void addShutdownHook(Runnable r) {
        shutdownHooks.add(r);
    }

    @CommandLine.Option(names = {"--config", "-c"}, description = "The configuration path", paramLabel = "config")
    public Path path;

    @CommandLine.Option(names = {"--edit", "-e"}, description = "Edits Polonium configuration.")
    public boolean editRequested;

    @CommandLine.Option(names = {"--run", "-r"}, description = "Runs Polonium and decays files.")
    public boolean runRequested;

    @CommandLine.Option(names = {"--notify", "-n"}, description = "Notify what Polonium did from its last run.")
    public boolean notifyLastRun;

    private Path getConfigPath() {
        Path configPath = path != null ? path : ConfigManager.globalConfig;

        if (!Files.exists(configPath)) {
            Logger.error("{} points to a non-existent file", configPath);
            throw new RuntimeException(configPath + " points to a non-existent file");
        }
        return configPath;
    }

    private void initSwing() {
        FlatLightLaf.setup();
        UILoader.bind("panel", JPanel.class);
        UILoader.bind("tabbed-pane", JTabbedPane.class);
    }

    @Override
    public void run() {
        initSwing();
        if (notifyLastRun) {
            editRequested |= PoloniumNotifier.notifyFiles("notifier.filesDeletedForever", PoloniumVolatile.get("Trash.DeletedForever", ArrayList::new).stream().map(Object::toString).toList());
            editRequested |= PoloniumNotifier.notifyFiles("notifier.filesDeleted", PoloniumVolatile.get("FilesDeleted", ArrayList::new));
            editRequested |= PoloniumNotifier.notifyFiles("notifier.filesWarn", PoloniumVolatile.get("FilesWarned", ArrayList::new));
        }

        if (runRequested) {
            Path configPath = getConfigPath();
            PoloniumConfig config = ConfigManager.load(configPath, PoloniumConfig.class);
            Logger.info("{} paths listed in config", config.paths.size());
            Logger.info("{} days of decay to warn for deletion", config.warnDays);
            Logger.info("{} days of decay for file deletion", config.deleteDays);
            Instant now = Instant.now();
            PoloniumVolatile.put("LastRun", now);
            if (config.paths.isEmpty()) {
                Logger.warn("Polonium could not find any paths to manage files. Please set the config first.");
                ConfigManager.save(configPath, config);
            } else {
                PoloniumTrash.setConfig(config);
                ArrayList<String> filesAddedVolatile = PoloniumVolatile.get("FilesAdded", ArrayList::new);
                ArrayList<String> filesDeletedVolatile = PoloniumVolatile.get("FilesDeleted", ArrayList::new);
                ArrayList<String> filesWarnedVolatile = PoloniumVolatile.get("FilesWarned", ArrayList::new);
                filesAddedVolatile.clear();
                filesDeletedVolatile.clear();
                filesWarnedVolatile.clear();
                try {
                    poloniumRunOnList(config.paths, config.warnDays, config.deleteDays);
                } finally {
                    PoloniumTrash.decay();
                }
            }
        }

        editRequested |= PoloniumNotifier.notifyFiles("notifier.filesDeletedForever", PoloniumVolatile.get("Trash.DeletedForever", ArrayList::new).stream().map(Object::toString).toList());
        editRequested |= PoloniumNotifier.notifyFiles("notifier.filesDeleted", PoloniumVolatile.get("FilesDeleted", ArrayList::new));
        editRequested |= PoloniumNotifier.notifyFiles("notifier.filesWarn", PoloniumVolatile.get("FilesWarned", ArrayList::new));

        if (editRequested) {
            SwingUtilities.invokeLater(this::runConfigEditor);
        }
    }

    private void runConfigEditor() {
        Path configPath = getConfigPath();
        PoloniumConfig config = ConfigManager.load(configPath, PoloniumConfig.class);

        PoloniumConfigEditor configEditor = new PoloniumConfigEditor(config, configPath);
        configEditor.setVisible(true);
    }

    private void poloniumRunOnList(List<String> pathnameList, long warnDays, long deleteDays) {
        PoloniumVolatile.put("PathsDecaying", pathnameList);

        for (String pathName : pathnameList) {
            if (pathName != null) {
                Logger.info("Now traversing {}", pathName);
                Path path = Path.of(pathName);
                if (Files.exists(path)) {
                    poloniumRun(path, warnDays, deleteDays);
                } else {
                    Logger.error("Path points to non-existent file!");
                }
            }
        }
    }

    public void poloniumRun(Path dir, long warnDays, long deleteDays) {
        Logger.info("Running Polonium on {}...", dir);
        Logger.info("Loading mole");
        PoloniumMole mole = PoloniumFileTracker.loadMole(dir.toString(), ProgressIndicator.VOID);

        // get the file structure
        try {
            long startMs = System.currentTimeMillis();
            PoloniumFileVisitor fileVisitor = new PoloniumFileVisitor(dir, mole, deleteDays, warnDays);
            Files.walkFileTree(dir, fileVisitor);

            checkUnmarkedFiles(fileVisitor, mole);
            logStats(fileVisitor, mole, dir, startMs);

            PoloniumFileTracker.saveMole(dir.toString(), mole, ProgressIndicator.VOID);
        } catch (IOException e) {
            Logger.error(e, "An exception occurred while checking the mole of {}", dir);
            // throw new UncheckedIOException(e);
            // Polonium still needs to do it's job however...
        }
    }

    private static void checkUnmarkedFiles(PoloniumFileVisitor visitor, PoloniumMole mole) {
        Logger.info("Checking for unvisited files. There are {} of them.", visitor.unvisitedFiles.size());
        for (Path file : visitor.unvisitedFiles) {
            Logger.warn("File {} was not visited, removing from mole.", file);
            PoloniumMole.Entry entry = mole.entryList.get(file);
            if (entry != null) {
                mole.entryList.remove(file);
            }
        }
    }

    private static void logStats(PoloniumFileVisitor visitor, PoloniumMole mole, Path path, long startMs) {
        Logger.trace("== Files added to mole ==");
        TreeSet<Path> addedFilesSorted = new TreeSet<>(visitor.addedFiles);
        for (Path file : addedFilesSorted) {
            Logger.trace("[+] {}", file);
        }

        Logger.trace("== Files deleted due to decay ==");
        TreeSet<Path> deletedFilesSorted = new TreeSet<>(visitor.deletedFiles);
        for (Path file : deletedFilesSorted) {
            Logger.trace("[-] {}", file);
        }

        Logger.trace("== Files not visited ==");
        TreeSet<Path> unvisitedFilesSorted = new TreeSet<>(visitor.unvisitedFiles);
        for (Path file : unvisitedFilesSorted) {
            Logger.trace("[?] {}", file);
        }

        Logger.trace("== Files warned ==");
        TreeSet<Path> warnedFilesSorted = new TreeSet<>(visitor.warnedFiles);
        for (Path file : warnedFilesSorted) {
            Logger.trace("[!] {}", file);
        }

        Logger.trace("== Files in the mole ==");
        TreeSet<Path> filesSorted = new TreeSet<>(mole.entryList.keySet());
        for (Path file : filesSorted) {
            PoloniumMole.Entry entry = mole.entryList.get(file);
            Logger.trace("[*] {}; {} days of decay", file, entry.getDecay());
        }

        // put that into the volatile
        PoloniumVolatile.put("Mole.FilesAdded___" + path, addedFilesSorted.stream().map(Path::toString).toList());
        PoloniumVolatile.put("Mole.FilesRemoved___" + path, deletedFilesSorted.stream().map(Path::toString).toList());
        PoloniumVolatile.put("Mole.FilesNotVisited___" + path, unvisitedFilesSorted.stream().map(Path::toString).toList());
        PoloniumVolatile.put("Mole.FilesWarned___" + path, warnedFilesSorted.stream().map(Path::toString).toList());
        PoloniumVolatile.put("Mole.Files___" + path, filesSorted.stream().map(Path::toString).toList());

        ArrayList<String> filesAddedVolatile = PoloniumVolatile.get("FilesAdded", ArrayList::new);
        ArrayList<String> filesDeletedVolatile = PoloniumVolatile.get("FilesDeleted", ArrayList::new);
        ArrayList<String> filesWarnedVolatile = PoloniumVolatile.get("FilesWarned", ArrayList::new);
        filesAddedVolatile.addAll(addedFilesSorted.stream().map(Path::toString).toList());
        filesDeletedVolatile.addAll(deletedFilesSorted.stream().map(Path::toString).toList());
        filesWarnedVolatile.addAll(warnedFilesSorted.stream().map(Path::toString).toList());

        Logger.info("=== FILE STATS ===");
        Logger.info("{} files were added to the mole.", visitor.addedFiles.size());
        Logger.info("{} files were deleted due to decay.", visitor.deletedFiles.size());
        Logger.info("{} files were gone but were in the mole.", visitor.unvisitedFiles.size());
        Logger.info("{} files are in the mole.", visitor.files.size());
        Logger.info("{} were warned!", visitor.warnedFiles.size());
        Logger.info("{} seconds to process file tree.", (System.currentTimeMillis() - startMs) / 1e3);
    }

    public static void showException(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(text("errors.generic")).append("\n");

        String dialogMessage = "";

        try (StringWriter stringWriter = new StringWriter();
             PrintWriter printWriter = new PrintWriter(stringWriter)) {
            throwable.printStackTrace(printWriter);
            sb.append(stringWriter);

            dialogMessage = sb.toString();

            Throwable cause = throwable;
            Set<StackTraceElement> commonStack = new HashSet<>(Arrays.asList(cause.getStackTrace()));
            cause = cause.getCause();
            while (cause != null) {
                Set<StackTraceElement> stack = new HashSet<>(Arrays.asList(cause.getStackTrace()));
                for (StackTraceElement element : stack) {
                    if (!commonStack.contains(element)) {
                        commonStack.remove(element);
                    }
                }
                cause = cause.getCause();
            }

            sb.append('\n').append("-- Exception classes and their causes --").append('\n');
            int tab = 0;
            cause = throwable;
            do {
                String indent = "  ".repeat(Math.max(0, tab));
                sb.append(indent);
                sb.append("-> ").append(cause.getClass().getName());
                String message = cause.getMessage();
                if (message != null) {
                    message = message.replace('\n', ' ');
                    if (message.length() > 100) {
                        message = message.substring(0, 100).concat("...");
                    }
                }
                sb.append(" ").append(message).append('\n');
                // stack
                StackTraceElement[] stack = cause.getStackTrace();
                for (StackTraceElement element : stack) {
                    if (!commonStack.contains(element) || cause.getCause() == null) {
                        sb.append(indent).append(" at ").append(element).append('\n');
                    }
                }
                if (cause.getCause() != null) {
                    sb.append(indent).append(" <common stack>\n");
                }
                sb.append(indent).append("--- End of stack --- ");
                sb.append('\n');
                cause = cause.getCause();
                tab++;
            } while (cause != null);
        } catch (IOException ignored) {
        }

        String text = sb.toString();
        Logger.error(text);

        FlatLightLaf.setup();

        JOptionPane optionPane = new JOptionPane();
        optionPane.setMessageType(JOptionPane.ERROR_MESSAGE);
        optionPane.setWantsInput(false);
        JTextArea area = new JTextArea();
        area.setText(dialogMessage);
        area.setTabSize(2);
        area.setEditable(false);
        area.setBackground(optionPane.getBackground());
        optionPane.setMessage(area);
        JDialog dialog = optionPane.createDialog(text("errors.title"));
        dialog.setAlwaysOnTop(true);
        dialog.pack();
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
    }

}
