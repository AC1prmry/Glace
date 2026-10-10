package com.snac.graphics.impl;

import com.snac.graphics.ImageLoader;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;

/**
 * Image loader to load {@link BufferedImage Images} for {@link SwingRenderer} (or Swing in general).
 * Extends from {@link ImageLoader}
 */
@Slf4j
public class SwingImageLoader extends ImageLoader<BufferedImage> {
    protected static final BufferedImage FALLBACK_IMAGE;

    static {
        FALLBACK_IMAGE = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);

        FALLBACK_IMAGE.setRGB(0, 0, Color.MAGENTA.getRGB());
        FALLBACK_IMAGE.setRGB(0, 1, Color.MAGENTA.getRGB());
        FALLBACK_IMAGE.setRGB(1, 0, Color.BLACK.getRGB());
        FALLBACK_IMAGE.setRGB(1, 1, Color.BLACK.getRGB());
    }

    /**
     * Loads an image from the given path.
     *
     * @param path The path to the image resource
     * @return The loaded image
     */
    @Override
    public BufferedImage load(String path) {
        BufferedImage image;
        try (var stream = ImageLoader.class.getResourceAsStream(path)) {
            if (stream == null) throw new FileNotFoundException("Ressource not found:" + path);

            image = ImageIO.read(stream);
            log.info("Loaded image from: {}", path);
            return image;
        } catch (Exception e) {
            log.warn("Failed to load image from: '{}' | '{}'. Returned fallback image", path, e.toString());
        }

        return null;
    }

    /**
     * Returns a fallback image to use when loading fails or no cached version is available.
     *
     * @return The fallback image
     */
    @Override
    public BufferedImage getFallback() {
        return FALLBACK_IMAGE;
    }
}