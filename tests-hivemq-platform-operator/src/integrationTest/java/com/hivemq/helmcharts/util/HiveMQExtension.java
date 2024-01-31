package com.hivemq.helmcharts.util;

import com.hivemq.extension.sdk.api.ExtensionMain;
import javassist.ClassPool;
import javassist.NotFoundException;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

/**
 * Creates a HiveMQ extension ZIP from a given extension main class.
 *
 * @see <a
 *         href="https://github.com/testcontainers/testcontainers-java/blob/main/modules/hivemq/src/main/java/org/testcontainers/hivemq/HiveMQExtension.java">Testcontainers</a>
 */
public class HiveMQExtension {

    private static final @NotNull String VALID_EXTENSION_XML = "<hivemq-extension>\n" + //
            "   <id>%s</id>\n" + //
            "   <name>%s</name>\n" + //
            "   <version>%s</version>\n" + //
            "   <priority>%s</priority>\n" + //
            "   <start-priority>%s</start-priority>\n" + //
            "</hivemq-extension>\n";

    private static final @NotNull String EXTENSION_MAIN_CLASS_NAME = "com.hivemq.extension.sdk.api.ExtensionMain";

    private static final @NotNull Logger LOGGER = LoggerFactory.getLogger(HiveMQExtension.class);

    private final @NotNull String id;
    private final @NotNull String name;
    private final @NotNull String version;
    private final int priority;
    private final int startPriority;
    private final boolean disabledOnStartup;
    private final @NotNull Class<?> mainClass;
    private final @NotNull List<Class<?>> additionalClasses;

    private HiveMQExtension(
            final @NotNull String id,
            final @NotNull String name,
            final @NotNull String version,
            final int priority,
            final int startPriority,
            final boolean disabledOnStartup,
            final @NotNull Class<? extends ExtensionMain> mainClass,
            final @NotNull List<Class<?>> additionalClasses) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.priority = priority;
        this.startPriority = startPriority;
        this.disabledOnStartup = disabledOnStartup;
        this.mainClass = mainClass;
        this.additionalClasses = additionalClasses;
    }

    public static @NotNull Path createHiveMQExtensionZip(
            final @NotNull Path tempDir,
            final @NotNull String extensionId,
            final @NotNull String extensionName,
            final @NotNull String version,
            final @NotNull Class<? extends ExtensionMain> extensionMainClass) throws Exception {
        final var hiveMQExtension = new Builder() //
                .id(extensionId) //
                .name(extensionName) //
                .version(version) //
                .mainClass(extensionMainClass) //
                .build();
        return hiveMQExtension.createExtensionZip(tempDir);
    }

    private @NotNull Path createExtensionZip(final @NotNull Path tempDir) throws Exception {
        final var extensionDir = tempDir.resolve(id + "-" + version);
        Files.createDirectory(extensionDir);

        final var extensionZip = tempDir.resolve(id + "-" + version + ".zip");
        final var extensionZipArchive = ShrinkWrap.create(JavaArchive.class);

        // hivemq-extension.xml
        final var extensionXml = extensionDir.resolve("hivemq-extension.xml");
        Files.createFile(extensionXml);
        Files.writeString(extensionXml, String.format(VALID_EXTENSION_XML, id, name, version, priority, startPriority));
        extensionZipArchive.add(new FileAsset(extensionXml.toFile()), id + "/" + extensionXml.getFileName().toString());

        // DISABLED
        if (disabledOnStartup) {
            final var disabled = extensionDir.resolve("DISABLED");
            Files.createFile(disabled);
            extensionZipArchive.add(new FileAsset(disabled.toFile()), id + "/" + disabled.getFileName().toString());
        }

        // extension.jar
        final var extensionJarArchive = ShrinkWrap.create(JavaArchive.class)
                .addAsServiceProvider(EXTENSION_MAIN_CLASS_NAME, mainClass.getName());

        putSubclassesIntoJar(id, mainClass, extensionJarArchive);
        for (final var additionalClass : additionalClasses) {
            extensionJarArchive.addClass(additionalClass);
            putSubclassesIntoJar(id, additionalClass, extensionJarArchive);
        }

        final var extensionJar = extensionDir.resolve(id + "-" + version + ".jar");
        extensionJarArchive.as(ZipExporter.class).exportTo(extensionJar.toFile());
        extensionZipArchive.add(new FileAsset(extensionJar.toFile()), id + "/" + extensionJar.getFileName().toString());

        extensionZipArchive.as(ZipExporter.class).exportTo(extensionZip.toFile());

        return extensionZip;
    }

    private void putSubclassesIntoJar(
            final @NotNull String extensionId,
            final @Nullable Class<?> clazz,
            final @NotNull JavaArchive javaArchive) throws NotFoundException {
        if (clazz != null) {
            final var subClassNames =
                    ClassPool.getDefault().get(clazz.getName()).getClassFile().getConstPool().getClassNames();
            for (final var subClassName : subClassNames) {
                final var className = subClassName.replaceAll("/", ".");
                if (!className.startsWith("[L")) {
                    LOGGER.debug("Trying to package subclass '{}' into extension '{}'", className, extensionId);
                    javaArchive.addClass(className);
                } else {
                    LOGGER.debug("Class '{}' will be ignored", className);
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private static final class Builder {

        private final @NotNull LinkedList<Class<?>> additionalClasses = new LinkedList<>();

        private @Nullable String id;
        private @Nullable String name;
        private @Nullable String version;
        private int priority = 0;
        private int startPriority = 1000;
        private boolean disabledOnStartup = false;
        private @Nullable Class<? extends ExtensionMain> mainClass;

        /**
         * Builds the {@link HiveMQExtension} with the provided values or default values.
         *
         * @return the HiveMQ Extension
         */
        private @NotNull HiveMQExtension build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("extension id must not be null or empty");
            }
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("extension name must not be null or empty");
            }
            if (version == null || version.isEmpty()) {
                throw new IllegalArgumentException("extension version must not be null or empty");
            }
            if (mainClass == null) {
                throw new IllegalArgumentException("extension main class must not be null");
            }
            return new HiveMQExtension(id,
                    name,
                    version,
                    priority,
                    startPriority,
                    disabledOnStartup,
                    mainClass,
                    additionalClasses);
        }

        /**
         * Sets the identifier of the {@link HiveMQExtension}.
         *
         * @param id the identifier, must not be empty
         * @return the {@link Builder}
         */
        private @NotNull Builder id(final @NotNull String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the name of the {@link HiveMQExtension}.
         *
         * @param name the identifier, must not be empty
         * @return the {@link Builder}
         */
        private @NotNull Builder name(final @NotNull String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the version of the {@link HiveMQExtension}.
         *
         * @param version the version, must not be empty
         * @return the {@link Builder}
         */
        private @NotNull Builder version(final @NotNull String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the priority of the {@link HiveMQExtension}.
         *
         * @param priority the priority
         * @return the {@link Builder}
         */
        private @NotNull Builder priority(final int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Sets the start-priority of the {@link HiveMQExtension}.
         *
         * @param startPriority the start-priority
         * @return the {@link Builder}
         */
        private @NotNull Builder startPriority(final int startPriority) {
            this.startPriority = startPriority;
            return this;
        }

        /**
         * Flag, that indicates whether the {@link HiveMQExtension} should be disabled when HiveMQ starts.
         * Disabling on startup is achieved by placing a DISABLED file in the {@link HiveMQExtension}'s directory before
         * coping it to the container.
         *
         * @param disabledOnStartup if the {@link HiveMQExtension} should be disabled when HiveMQ starts
         * @return the {@link Builder}
         */
        private @NotNull Builder disabledOnStartup(final boolean disabledOnStartup) {
            this.disabledOnStartup = disabledOnStartup;
            return this;
        }

        /**
         * The main class of the {@link HiveMQExtension}.
         * This class MUST implement com.hivemq.extension.sdk.api.ExtensionMain.
         *
         * @param mainClass the main class
         * @return the {@link Builder}
         * @throws IllegalArgumentException if the provides class does not implement
         *                                  com.hivemq.extension.sdk.api.ExtensionMain}
         * @throws IllegalStateException    if com.hivemq.extension.sdk.api.ExtensionMain is not found in the classpath
         */
        private @NotNull Builder mainClass(final @NotNull Class<? extends ExtensionMain> mainClass) {
            try {
                final var extensionMain = Class.forName(EXTENSION_MAIN_CLASS_NAME);
                this.mainClass = mainClass;
                return this;
            } catch (final ClassNotFoundException e) {
                throw new IllegalStateException("The class '" +
                        EXTENSION_MAIN_CLASS_NAME +
                        "' was not found in the classpath.");
            }
        }

        /**
         * Adds additional classes to the JAR file of the {@link HiveMQExtension}.
         *
         * @param clazz the additional class
         * @return the {@link Builder}
         */
        private @NotNull Builder addAdditionalClass(final @NotNull Class<?> clazz) {
            this.additionalClasses.add(clazz);
            return this;
        }
    }
}
