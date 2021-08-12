package com.openwebstart.app;

import com.openwebstart.os.linux.FavIcon;
import net.adoptopenjdk.icedteaweb.Assert;
import net.adoptopenjdk.icedteaweb.client.controlpanel.CacheFileInfo;
import net.adoptopenjdk.icedteaweb.client.controlpanel.CacheIdInfo;
import net.adoptopenjdk.icedteaweb.jnlp.element.information.IconKind;
import net.adoptopenjdk.icedteaweb.xmlparser.ParseException;
import net.sourceforge.jnlp.JNLPFile;
import net.sourceforge.jnlp.JNLPFileFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.openwebstart.concurrent.ThreadPoolHolder.getDaemonExecutorService;

/**
 * Defines a JNLP based application that is manged by OpenWebStart
 */
public class Application {

    private final CacheIdInfo cacheId;

    private final long size;

    private final JNLPFile jnlpFile;

    /**
     * Constructor
     *
     * @param cacheId the cache object from IcedTeaWeb
     */
    public Application(final CacheIdInfo cacheId) throws IOException, ParseException {
        this.cacheId = Assert.requireNonNull(cacheId, "cacheId");
        this.size = cacheId.getFileInfos().stream().mapToLong(CacheFileInfo::getSize).sum();
        jnlpFile = new JNLPFileFactory().create(Paths.get(cacheId.getId()).toUri().toURL());
    }

    /**
     * Returns the application name
     *
     * @return the name
     */
    public String getName() {
        try {
            return jnlpFile.getTitle(true);
        } catch (final Exception e) {
            return "UNKNOWN";
        }
    }

    /**
     * returns the current size of the application on disc in bytes
     *
     * @return the size
     */
    public long getSize() {
        return size;
    }

    public CompletableFuture<BufferedImage> loadIcon(final int dimension) {
        final CompletableFuture<BufferedImage> result = new CompletableFuture<>();
        final URL iconURL = Optional.ofNullable(jnlpFile.getInformation().getIconLocation(IconKind.SHORTCUT, dimension, dimension))
                .orElseGet(() -> jnlpFile.getInformation().getIconLocation(IconKind.DEFAULT, dimension, dimension));
        if (iconURL == null) {
            getDaemonExecutorService().execute(() -> {
                try {
                    final FavIcon favIcon = new FavIcon(jnlpFile);
                    final File favIconFile = favIcon.download();
                    result.complete(ImageIO.read(favIconFile));
                } catch (final IOException e) {
                    result.completeExceptionally(e);
                }
            });

        } else {
            getDaemonExecutorService().execute(() -> {
                try (final InputStream inputStream = iconURL.openStream()) {
                    result.complete(ImageIO.read(inputStream));
                } catch (final IOException e) {
                    result.completeExceptionally(e);
                }
            });
        }
        return result;
    }

    public JNLPFile getJnlpFile() {
        return jnlpFile;
    }

    public String getId() {
        return cacheId.getId();
    }
}
