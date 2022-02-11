package com.openwebstart.os.mac;

import com.openwebstart.os.mac.icns.IcnsFactory;
import net.adoptopenjdk.icedteaweb.Assert;
import net.adoptopenjdk.icedteaweb.JavaSystemProperties;
import net.adoptopenjdk.icedteaweb.config.FilesystemConfiguration;
import net.adoptopenjdk.icedteaweb.io.FileUtils;
import net.adoptopenjdk.icedteaweb.io.IOUtils;
import net.adoptopenjdk.icedteaweb.logging.Logger;
import net.adoptopenjdk.icedteaweb.logging.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AppFactory {

    private static final Logger LOG = LoggerFactory.getLogger(AppFactory.class);

    public static final String CONTENTS_FOLDER_NAME = "Contents";

    private static final String MAC_OS_FOLDER_NAME = "MacOS";

    private static final String RESOURCES_FOLDER_NAME = "Resources";

    private static final String INFO_PLIST_NAME = "Info.plist";

    private static final String INFO_PLIST_TEMPLATE_NAME = "Info.plist.template";

    public static final String APP_EXTENSION = ".app";

    private static final String SCRIPT_NAME = "start.sh";

    private static final String SCRIPT_NAME_PROPERTY = "${scriptName}";

    private static final String ICON_FILE_PROPERTY = "${iconFile}";

    private static final String ICON_FILE_NAME = "icons";

    private static final String ICON_FILE_EXTENSION = ".icns";

    private static final String USER_APPLICATIONS_FOLDER = "Applications";

    private static final String USER_APPLICATIONS_CACHE_FOLDER = "applications";

    private static final String USER_DESKTOP = "Desktop";

    public static boolean exists(final String name) {
        Assert.requireNonBlank(name, "name");
        final Path applicationsFolder = ensureUserApplicationFolder();
        final File appPackage = new File(applicationsFolder.toFile(), name + APP_EXTENSION);
        return appPackage.exists();
    }

    public static void createApp(final String name, final String script, final String... iconPaths) throws Exception {

        final Path userApplicationFolder = ensureUserApplicationFolder();
        createNativeApp(userApplicationFolder, name, script, iconPaths);
    }

    static File createAppWithoutMenuEntry(final String name, final String script, final String... iconPaths)
            throws Exception {

        final Path applicationsFolder = ensureUserApplicationCacheFolder();
        return createNativeApp(applicationsFolder, name, script, iconPaths);
    }

    static File createNativeApp(final Path applicationsFolder, final String name, final String script, final String... iconPaths)
            throws Exception {
        Assert.requireNonNull(applicationsFolder, "applicationsFolder");
        Assert.requireNonBlank(name, "name");
        Assert.requireNonBlank(script, "script");

        final File appPackage = new File(applicationsFolder.toFile(), name + APP_EXTENSION);

        LOG.info("Creating app '{}' at '{}'", name, appPackage);


        if (!appPackage.exists()) {
            if (!appPackage.mkdirs()) {
                throw new IOException("Cannot create app directory");
            }
        }
        LOG.debug("App '{}' will be placed at '{}'", name, appPackage);

        final File contentsFolder = new File(appPackage, CONTENTS_FOLDER_NAME);
        FileUtils.recursiveDelete(contentsFolder, appPackage);
        if (!contentsFolder.mkdirs()) {
            throw new IOException("Cannot create contents directory");
        }
        LOG.debug("Folder '{}' for app '{}' created", CONTENTS_FOLDER_NAME, name);

        final File resourcesFolder = new File(contentsFolder, RESOURCES_FOLDER_NAME);
        if (!resourcesFolder.mkdirs()) {
            throw new IOException("Cannot create resources directory");
        }
        LOG.debug("Folder '{}' for app '{}' created", RESOURCES_FOLDER_NAME, name);

        final File macFolder = new File(contentsFolder, MAC_OS_FOLDER_NAME);
        if (!macFolder.mkdirs()) {
            throw new IOException("Cannot create macOs directory");
        }
        LOG.debug("Folder '{}' for app '{}' created", MAC_OS_FOLDER_NAME, name);

        final File iconsFile = new File(resourcesFolder, ICON_FILE_NAME + ICON_FILE_EXTENSION);
        try (final InputStream inputStream = getIcnsInputStream(iconPaths);
             final FileOutputStream outputStream = new FileOutputStream(iconsFile)) {
            IOUtils.copy(inputStream, outputStream);
        }
        LOG.debug("Iconfile for app '{}' created", name);

        final File infoFile = new File(contentsFolder, INFO_PLIST_NAME);
        try (final InputStream inputStream = AppFactory.class.getResourceAsStream(INFO_PLIST_TEMPLATE_NAME)) {
            final String infoContent = IOUtils.readContentAsUtf8String(inputStream)
                    .replaceAll(Pattern.quote(SCRIPT_NAME_PROPERTY), SCRIPT_NAME)
                    .replaceAll(Pattern.quote(ICON_FILE_PROPERTY), ICON_FILE_NAME);
            try (final FileOutputStream outputStream = new FileOutputStream(infoFile)) {
                IOUtils.writeUtf8Content(outputStream, infoContent);
            }
        }
        LOG.debug("{} for app '{}' created", INFO_PLIST_NAME, name);

        // TODO: Here we need to change the calculator sample to a concrete OpenWebStart
        // call
        final File scriptFile = new File(macFolder, SCRIPT_NAME);
        try (final FileOutputStream outputStream = new FileOutputStream(scriptFile)) {
            IOUtils.writeUtf8Content(outputStream, script);
        }
        if (!scriptFile.setExecutable(true)) {
            throw new IOException("Cannot create script file");
        }
        LOG.debug("Script for app '{}' created", name);
        return appPackage;
    }

    private static InputStream getIcnsInputStream(final String... iconPaths) throws Exception {
        final IcnsFactory factory = new IcnsFactory();
        final List<File> iconFiles = Arrays.stream(iconPaths).map(File::new).collect(Collectors.toList());
        return new FileInputStream(factory.createIconSet(iconFiles));
    }

    private static Path ensureUserApplicationFolder() {
        final String userHome = JavaSystemProperties.getUserHome();
        final File appFolder = new File(new File(userHome), USER_APPLICATIONS_FOLDER);
        if (!appFolder.exists()) {
            appFolder.mkdir();
        }
        return appFolder.toPath();
    }

    private static Path ensureUserApplicationCacheFolder() {
        final Path appcache = Paths.get(FilesystemConfiguration.getCacheHome(), USER_APPLICATIONS_CACHE_FOLDER);
        if (!Files.isDirectory(appcache)) {
            try {
                Files.createDirectories(appcache);
            } catch (final IOException ioExc) {
                throw new RuntimeException("Could not create application cache directory [" + appcache + "]", ioExc);
            }
        }
        return appcache;
    }

    static Path getApplicationRootInCache(final String name) {
        return Paths.get(FilesystemConfiguration.getCacheHome(), USER_APPLICATIONS_CACHE_FOLDER, name + APP_EXTENSION);
    }

    public static boolean desktopLinkExists(final String appname) {
        Assert.requireNonBlank(appname, "appname");
        final Path cache = getApplicationRootInCache(appname);
        if (Files.isDirectory(cache)) {
            final Path link = getDesktopLink(appname);
            if (Files.isSymbolicLink(link)) {
                try {
                    final Path linkRealPath = link.toRealPath();
                    return cache.toRealPath().equals(linkRealPath);
                } catch (final Exception e) {
                    LOG.debug("Coluld not verify desktop link [" + link + "]", e);
                }
            }
        }
        return false;
    }

    public static void createDesktopLink(final String appname, final String script, final String... iconPaths)
            throws Exception {
        Assert.requireNonBlank(appname, "appname");
        if (!desktopLinkExists(appname)) {
            final Path approot = getApplicationRootInCache(appname);
            if (!Files.isDirectory(approot)) {
                createAppWithoutMenuEntry(appname, script, iconPaths);
            }
            final Path link = getDesktopLink(appname);
            Files.deleteIfExists(link);
            Files.createSymbolicLink(link, approot);
        }
    }

    private static Path getDesktopLink(final String appname) {
        return Paths.get(JavaSystemProperties.getUserHome(), USER_DESKTOP, appname + APP_EXTENSION);
    }
}
